# ml-api/app.py
from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
import torch
import torch.nn as nn
import torch.nn.functional as F
from torchvision import models, transforms
from PIL import Image
import numpy as np
import base64
import io
import os
import sys
import time
import logging

def _setup_logging() -> None:
    """Логирование в файл, когда приложение собрано PyInstaller'ом (--windowed).

    В windowed-сборке sys.stdout/stderr могут быть None, поэтому потоковые
    обработчики уронили бы uvicorn. Пишем в ~/.malaria-detection/ml-api.log.
    """
    if getattr(sys, "frozen", False):
        log_dir = os.path.join(os.path.expanduser("~"), ".malaria-detection")
        os.makedirs(log_dir, exist_ok=True)
        logging.basicConfig(
            level=logging.INFO,
            filename=os.path.join(log_dir, "ml-api.log"),
            filemode="a",
            format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        )
    else:
        logging.basicConfig(level=logging.INFO)


_setup_logging()
logger = logging.getLogger(__name__)

app = FastAPI(title="Malaria Detection API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Конфигурация из ии
IMG_SIZE = 224
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
logger.info(f"Using device: {device}")

val_tfms = transforms.Compose([
    transforms.Resize((IMG_SIZE, IMG_SIZE)),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])


def resource_path(name: str) -> str:
    """Путь к файлу рядом с приложением.

    Работает и при обычном запуске, и внутри собранного PyInstaller .exe,
    где данные распаковываются в каталог sys._MEIPASS.
    """
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, name)


def build_model() -> nn.Module:
    """Функция создания модели.

    weights=None — предобученные веса ImageNet не скачиваются (важно для
    оффлайн-работы в .exe): все веса всё равно перезаписываются из best.pt.
    """
    model = models.efficientnet_b0(weights=None)
    for p in model.features.parameters():
        p.requires_grad = False
    model.classifier = nn.Sequential(
        nn.Dropout(0.3),
        nn.Linear(model.classifier[1].in_features, 2)
    )
    return model.to(device)


# загрузка модели
try:
    model = build_model()
    model.load_state_dict(torch.load(resource_path('best.pt'), map_location=device))
    model.eval()
    logger.info("Model loaded successfully")
except Exception as e:
    logger.error(f"Error loading model: {e}")
    model = None


def _jet_colormap(gray: np.ndarray) -> np.ndarray:
    """Переводит карту значений HxW в [0,1] в RGB HxWx3 (палитра JET)."""
    r = np.clip(1.5 - np.abs(4.0 * gray - 3.0), 0.0, 1.0)
    g = np.clip(1.5 - np.abs(4.0 * gray - 2.0), 0.0, 1.0)
    b = np.clip(1.5 - np.abs(4.0 * gray - 1.0), 0.0, 1.0)
    return (np.stack([r, g, b], axis=-1) * 255.0).astype(np.uint8)


def generate_gradcam_overlay(image_tensor: torch.Tensor,
                             original_image: Image.Image,
                             predicted_class: int) -> str | None:
    """Grad-CAM по последнему сверточному слою EfficientNet-B0.

    Строит тепловую карту зон, на которые «смотрела» модель при постановке
    диагноза, и накладывает её на исходное изображение. Возвращает PNG
    в base64 либо None, если что-то пошло не так (диагноз при этом не страдает).
    """
    if model is None:
        return None
    try:
        target_layer = model.features[-1]
        activations: dict[str, torch.Tensor] = {}
        gradients: dict[str, torch.Tensor] = {}

        def forward_hook(_module, _inp, out):
            activations["v"] = out
            out.register_hook(lambda grad: gradients.__setitem__("v", grad))

        handle = target_layer.register_forward_hook(forward_hook)
        try:
            inp = image_tensor.clone().requires_grad_(True)
            model.zero_grad(set_to_none=True)
            output = model(inp)
            score = output[0, predicted_class]
            score.backward()
        finally:
            handle.remove()

        acts = activations["v"].detach()[0]           # [C, h, w]
        grads = gradients["v"].detach()[0]            # [C, h, w]
        weights = grads.mean(dim=(1, 2))              # [C]
        cam = torch.relu((weights[:, None, None] * acts).sum(dim=0))  # [h, w]
        cam = cam - cam.min()
        cam = cam / (cam.max() + 1e-8)
        cam_np = cam.cpu().numpy()

        # Увеличиваем карту до размера исходного изображения
        w, h = original_image.size
        cam_img = Image.fromarray((cam_np * 255).astype(np.uint8)).resize(
            (w, h), Image.BILINEAR
        )
        cam_resized = np.asarray(cam_img).astype(np.float32) / 255.0

        heat = _jet_colormap(cam_resized).astype(np.float32)
        base = np.asarray(original_image.convert("RGB")).astype(np.float32)
        alpha = 0.45
        overlay = (alpha * heat + (1.0 - alpha) * base).clip(0, 255).astype(np.uint8)

        buf = io.BytesIO()
        Image.fromarray(overlay).save(buf, format="PNG")
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception as e:
        logger.error(f"Grad-CAM error: {e}")
        return None


def predict(image_data: bytes) -> dict:
    """Основная функция"""
    if model is None:
        return {"diagnosis": "error", "confidence": 0.0, "error": "Model not loaded"}

    try:
        start_time = time.time()

        # обработка изображения
        image = Image.open(io.BytesIO(image_data)).convert('RGB')
        image_tensor = val_tfms(image).unsqueeze(0).to(device)

        with torch.no_grad():
            output = model(image_tensor)
            probs = torch.softmax(output, dim=1)
            predicted_class = int(output.argmax(1)[0].item())
            confidence = probs[0][predicted_class].item()

        # Тепловая карта зоны заражения (Grad-CAM)
        heatmap_b64 = generate_gradcam_overlay(image_tensor, image, predicted_class)

        processing_time = time.time() - start_time

        diagnosis = "parasitized" if predicted_class == 0 else "uninfected"

        result = {
            "diagnosis": diagnosis,
            "confidence": confidence,
            "processing_time": round(processing_time, 2),
            "model_used": "EfficientNet-B0"
        }
        if heatmap_b64:
            result["heatmap"] = heatmap_b64
        return result

    except Exception as e:
        logger.error(f"Prediction error: {e}")
        return {
            "diagnosis": "error",
            "confidence": 0.0,
            "processing_time": 0.0,
            "error": str(e)
        }


@app.post("/analyze")
async def analyze_image(image: UploadFile = File(...)):
    try:
        if not image.content_type.startswith('image/'):
            return {"error": "File is not an image"}

        image_data = await image.read()

        if len(image_data) == 0:
            return {"error": "Empty image file"}

        result = predict(image_data)

        return result

    except Exception as e:
        logger.error(f"API error: {e}")
        return {"error": f"Processing error: {str(e)}"}


@app.get("/health")
async def health_check():
    """Здоровье сервера"""
    status = "healthy" if model is not None else "model_not_loaded"
    return {
        "status": status,
        "service": "malaria-ml-api",
        "device": str(device),
        "model_loaded": model is not None
    }


@app.get("/model-info")
async def model_info():
    """Инфо о модели"""
    if model is None:
        return {"error": "Model not loaded"}

    total_params = sum(p.numel() for p in model.parameters())
    return {
        "model_name": "EfficientNet-B0",
        "total_parameters": total_params,
        "input_size": IMG_SIZE,
        "device": str(device)
    }


if __name__ == "__main__":
    logger.info("Starting ML API server on http://localhost:8000")
    # 127.0.0.1 — локальный сервер для desktop-приложения (без firewall-запроса
    # и без выхода в сеть). log_config=None — используем настроенный выше root-логгер.
    uvicorn.run(app, host="127.0.0.1", port=8000, log_level="info", log_config=None)

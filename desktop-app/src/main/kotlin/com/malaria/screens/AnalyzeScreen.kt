package com.malaria.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import com.malaria.components.AnalysisResult
import com.malaria.components.MalariaApiClient
import com.malaria.data.AnalysisRecord
import com.malaria.repository.AnalysisRepository
import com.malaria.utils.FileValidator

/**
 * Главный экран анализа изображений клеток крови на малярию.
 *
 * Workflow:
 * 1. Выбор файла через системный диалог
 * 2. Валидация: формат (PNG/JPEG) + размер (max 5 МБ) + разрешение (max 1280×720)
 * 3. Асинхронная отправка на ML сервер
 * 4. Отображение результатов с цветовой кодировкой
 * 5. Сохранение в БД (поля зашифрованы через CryptoManager)
 *
 * @param onBackClick callback для возврата на главный экран
 */
@Composable
fun AnalyzeScreen(onBackClick: () -> Unit) {
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val analysisRepository = remember { AnalysisRepository() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF929292))
            .padding(32.dp)
    ) {
        Text(
            text = "Анализ изображения",
            fontSize = 28.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "⚠️ Это приложение предназначено исключительно для вспомогательных целей " +
                    "и не заменяет профессиональную медицинскую консультацию.",
            fontSize = 12.sp,
            color = Color(0xFFFFCC02)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Поддерживаемые форматы: PNG, JPEG | Макс. размер: 5 МБ | Макс. разрешение: 1280×720",
            fontSize = 11.sp,
            color = Color(0xFFAAAAAA)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Выбранный файл или кнопка выбора
        if (selectedFile != null) {
            FileItem(
                filePath = selectedFile!!,
                onRemove = {
                    selectedFile = null
                    errorMessage = null
                    analysisResult = null
                }
            )
        } else {
            Button(onClick = {
                openFileDialog(
                    onFileSelected = { path ->
                        selectedFile = path
                        errorMessage = null
                        analysisResult = null
                    },
                    onError = { error ->
                        errorMessage = error
                    }
                )
            }) {
                Text("📁 Выбрать изображение")
            }
        }

        // Сообщение об ошибке валидации
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "❌ $error", fontSize = 13.sp, color = Color(0xFFFF5252))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка анализа
        if (selectedFile != null && !isLoading) {
            Button(onClick = {
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val result = MalariaApiClient.analyzeImage(selectedFile!!)
                        analysisResult = result

                        if (result.error == null) {
                            val record = AnalysisRecord(
                                imagePath = selectedFile!!,
                                fileName = getFileName(selectedFile!!),
                                diagnosis = result.diagnosis,
                                confidence = result.confidence,
                                processingTime = result.processingTime,
                                modelUsed = result.modelUsed
                            )
                            analysisRepository.saveAnalysis(record)
                        }
                    } catch (e: Exception) {
                        errorMessage = "Ошибка соединения с ML сервером: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }) {
                Text("🔬 Анализировать")
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Анализ...", color = Color.White, fontSize = 14.sp)
            }
        }

        // Результат анализа
        analysisResult?.let { result ->
            Spacer(modifier = Modifier.height(24.dp))
            if (result.error != null) {
                Text("❌ ${result.error}", fontSize = 14.sp, color = Color(0xFFFF5252))
            } else {
                val diagnosisRu = getRussianDiagnosis(result.diagnosis)
                val diagnosisColor = if (result.diagnosis == "parasitized")
                    Color(0xFFFF5252) else Color(0xFF4CAF50)

                Column {
                    Text("Диагноз:", fontSize = 16.sp, color = Color.White)
                    Text(
                        text = diagnosisRu,
                        fontSize = 24.sp,
                        color = diagnosisColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Вероятность: ${"%.1f".format(result.confidence * 100)}%",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        "Время обработки: ${"%.2f".format(result.processingTime)} сек",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Text(
                        "Модель: ${result.modelUsed}",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onBackClick) {
            Text("← Назад")
        }
    }
}

@Composable
fun FileItem(filePath: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF7A7A7A))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Выбранный файл:", fontSize = 14.sp, color = Color.White)
            Text(
                text = getFileName(filePath),
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Формат: ${filePath.substringAfterLast(".", "").uppercase()}",
                fontSize = 10.sp,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Button(onClick = onRemove, modifier = Modifier.height(36.dp)) {
            Text("✕", color = Color.Black, fontSize = 14.sp)
        }
    }
}

private fun getRussianDiagnosis(englishDiagnosis: String): String {
    return when (englishDiagnosis) {
        "parasitized" -> "Заражено"
        "uninfected" -> "Не заражено"
        "error" -> "Ошибка"
        else -> englishDiagnosis
    }
}

/**
 * Открывает диалог выбора файла.
 * Валидация через [FileValidator]: формат + размер (5 МБ) + разрешение (1280×720).
 */
private fun openFileDialog(
    onFileSelected: (String) -> Unit,
    onError: (String) -> Unit
) {
    val fileDialog = FileDialog(null as Frame?, "Выберите изображение (PNG, JPEG)")
    fileDialog.mode = FileDialog.LOAD
    fileDialog.isMultipleMode = false
    fileDialog.isVisible = true

    val file = fileDialog.file
    val directory = fileDialog.directory
    fileDialog.dispose()

    if (file != null && directory != null) {
        val filePath = "$directory$file"
        when (val result = FileValidator.validate(filePath)) {
            is FileValidator.Result.Success -> onFileSelected(filePath)
            is FileValidator.Result.Error -> onError(result.message)
        }
    }
}

private fun getFileName(filePath: String): String {
    return filePath.substringAfterLast("\\").substringAfterLast("/")
}

/** Оставлен для обратной совместимости с FileValidationTest */
fun isSupportedFormat(filePath: String): Boolean = FileValidator.isSupportedFormat(filePath)

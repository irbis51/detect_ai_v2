package com.malaria.utils

import java.io.File
import javax.imageio.ImageIO

/**
 * Валидация загружаемых изображений.
 *
 * Проверяет три условия по порядку:
 * 1. Формат файла (PNG / JPEG)
 * 2. Размер файла (max 5 МБ)
 * 3. Разрешение изображения (max 1280×720)
 *
 * Разрешение читается через ImageReader из заголовка файла —
 * изображение целиком в память не загружается.
 */
object FileValidator {
    const val MAX_WIDTH = 1280
    const val MAX_HEIGHT = 720
    const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024  // 5 МБ

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    fun validate(filePath: String): Result {
        val file = File(filePath)

        if (!file.exists() || !file.isFile) {
            return Result.Error("Файл не найден")
        }

        if (!isSupportedFormat(filePath)) {
            return Result.Error("Неподдерживаемый формат. Используйте PNG или JPEG.")
        }

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            val sizeMb = file.length() / 1024.0 / 1024.0
            return Result.Error("Файл слишком большой: ${"%.2f".format(sizeMb)} МБ. Максимум — 5 МБ.")
        }

        val dimensions = readImageDimensions(file)
            ?: return Result.Error("Не удалось прочитать размеры изображения")

        val (width, height) = dimensions
        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            return Result.Error(
                "Слишком большое разрешение: ${width}×${height}. " +
                "Максимум — ${MAX_WIDTH}×${MAX_HEIGHT} (720p)."
            )
        }

        return Result.Success
    }

    fun isSupportedFormat(filePath: String): Boolean {
        val ext = filePath.substringAfterLast(".", "").lowercase()
        return ext == "png" || ext == "jpg" || ext == "jpeg"
    }

    private fun readImageDimensions(file: File): Pair<Int, Int>? {
        return try {
            ImageIO.createImageInputStream(file).use { iis ->
                val readers = ImageIO.getImageReaders(iis)
                if (!readers.hasNext()) return null
                val reader = readers.next()
                try {
                    reader.input = iis
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: Exception) {
            println("⚠️ Ошибка чтения размеров изображения: ${e.message}")
            null
        }
    }
}

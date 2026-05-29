package com.malaria.utils

import com.malaria.data.AnalysisRecord
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Экспорт истории анализов в CSV (RFC 4180).
 *
 * Особенности:
 * - BOM (0xEF 0xBB 0xBF) в начале файла — корректное открытие в Excel без перекодировки
 * - Экранирование полей содержащих запятые, кавычки или переносы строк
 * - Возвращает kotlin.Result с количеством экспортированных записей или исключением
 */
object CsvExporter {
    private const val SEPARATOR = ","
    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    val HEADERS = listOf(
        "id", "file_name", "image_path", "diagnosis",
        "confidence_%", "processing_time_sec", "model_used", "analysis_date"
    )

    fun export(records: List<AnalysisRecord>, file: File): Result<Int> {
        return try {
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF")  // BOM для корректного открытия в Excel
                writer.write(HEADERS.joinToString(SEPARATOR))
                writer.newLine()

                records.forEach { r ->
                    writer.write(
                        listOf(
                            r.id.toString(),
                            escape(r.fileName),
                            escape(r.imagePath),
                            escape(r.diagnosis),
                            "%.2f".format(r.confidence * 100),
                            "%.3f".format(r.processingTime),
                            escape(r.modelUsed),
                            r.analysisDate.format(DATE_FORMAT)
                        ).joinToString(SEPARATOR)
                    )
                    writer.newLine()
                }
            }
            println("✅ CSV экспортирован: ${file.absolutePath} (${records.size} записей)")
            Result.success(records.size)
        } catch (e: Exception) {
            println("❌ Ошибка экспорта CSV: ${e.message}")
            Result.failure(e)
        }
    }

    /** Экранирует значение по RFC 4180: оборачивает в кавычки если нужно. */
    private fun escape(value: String): String {
        val needsQuoting = value.contains(SEPARATOR) || value.contains('"') ||
                           value.contains('\n') || value.contains('\r')
        val safe = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$safe\"" else safe
    }
}

package com.malaria.repository

import com.malaria.data.AnalysisRecord
import com.malaria.database.DatabaseManager
import com.malaria.security.CryptoManager
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * CRUD-операции с локальной SQLite базой данных.
 *
 * Поля image_path, file_name, diagnosis шифруются через [CryptoManager] (AES-256/GCM).
 * Числовые поля и дата не шифруются — нужны для сортировки.
 *
 * Обратная совместимость: старые незашифрованные записи читаются корректно —
 * [CryptoManager.decrypt] возвращает значение как есть если префикс "enc:v1:" отсутствует.
 *
 * Фильтрация выполняется в памяти после расшифровки, так как AES/GCM с рандомным IV
 * делает WHERE по зашифрованному полю невозможным.
 */
class AnalysisRepository {

    fun saveAnalysis(record: AnalysisRecord): Long {
        return try {
            val connection = DatabaseManager.getConnection()
            val sql = """
                INSERT INTO analysis_records 
                (image_path, file_name, diagnosis, confidence, processing_time, model_used, analysis_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """

            val statement = connection.prepareStatement(sql, arrayOf("id"))
            statement.setString(1, CryptoManager.encrypt(record.imagePath))
            statement.setString(2, CryptoManager.encrypt(record.fileName))
            statement.setString(3, CryptoManager.encrypt(record.diagnosis))
            statement.setDouble(4, record.confidence)
            statement.setDouble(5, record.processingTime)
            statement.setString(6, record.modelUsed)
            statement.setString(7, record.analysisDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

            statement.executeUpdate()
            val generatedKeys = statement.generatedKeys
            generatedKeys.next()
            val id = generatedKeys.getLong(1)

            statement.close()
            connection.close()

            println("✅ Результат сохранен в БД с ID: $id")
            id
        } catch (e: Exception) {
            println("❌ Ошибка сохранения в БД: ${e.message}")
            -1L
        }
    }

    fun getAllAnalyses(): List<AnalysisRecord> {
        return try {
            val connection = DatabaseManager.getConnection()
            val statement = connection.createStatement()
            val resultSet: ResultSet = statement.executeQuery(
                "SELECT * FROM analysis_records ORDER BY id DESC"
            )

            val analyses = mutableListOf<AnalysisRecord>()
            while (resultSet.next()) {
                analyses.add(
                    AnalysisRecord(
                        id = resultSet.getLong("id"),
                        imagePath = CryptoManager.decrypt(resultSet.getString("image_path")),
                        fileName = CryptoManager.decrypt(resultSet.getString("file_name")),
                        diagnosis = CryptoManager.decrypt(resultSet.getString("diagnosis")),
                        confidence = resultSet.getDouble("confidence"),
                        processingTime = resultSet.getDouble("processing_time"),
                        modelUsed = resultSet.getString("model_used"),
                        analysisDate = LocalDateTime.parse(
                            resultSet.getString("analysis_date"),
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        )
                    )
                )
            }

            resultSet.close()
            statement.close()
            connection.close()

            analyses
        } catch (e: Exception) {
            println("❌ Ошибка чтения из БД: ${e.message}")
            emptyList()
        }
    }

    fun getAnalysesByDiagnosis(diagnosis: String): List<AnalysisRecord> {
        return getAllAnalyses().filter { it.diagnosis == diagnosis }
    }

    fun getFilteredAnalyses(
        diagnosis: String? = null,
        startDate: String? = null,
        endDate: String? = null
    ): List<AnalysisRecord> {
        val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
        return getAllAnalyses().filter { r ->
            val recordDate = r.analysisDate.format(dateFmt)
            (diagnosis == null || r.diagnosis == diagnosis) &&
            (startDate == null || recordDate >= startDate) &&
            (endDate == null || recordDate <= endDate)
        }
    }
}

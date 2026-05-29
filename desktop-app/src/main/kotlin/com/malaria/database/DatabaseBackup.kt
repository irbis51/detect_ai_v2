package com.malaria.database

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Резервное копирование базы данных SQLite.
 *
 * Вызывается из Main.kt в onCloseRequest перед exitApplication().
 * Хранит последние [MAX_BACKUPS] копий, старые удаляет автоматически.
 *
 * Путь к бэкапам: ~/.malaria-detection/backups/malaria_analyses_YYYYMMDD_HHmmss.db
 */
object DatabaseBackup {
    private const val MAX_BACKUPS = 5
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun createBackup(): File? {
        return try {
            val userHome = System.getProperty("user.home")
            val appDir = File(userHome, ".malaria-detection")
            val dbFile = File(appDir, "malaria_analyses.db")

            if (!dbFile.exists()) {
                println("ℹ️ Файл БД не найден, бэкап пропущен")
                return null
            }

            val backupDir = File(appDir, "backups").apply { mkdirs() }
            val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
            val backupFile = File(backupDir, "malaria_analyses_$timestamp.db")

            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("✅ Резервная копия создана: ${backupFile.absolutePath}")

            pruneOldBackups(backupDir)
            backupFile
        } catch (e: Exception) {
            println("❌ Ошибка создания резервной копии: ${e.message}")
            null
        }
    }

    private fun pruneOldBackups(backupDir: File) {
        val backups = backupDir.listFiles { f ->
            f.isFile && f.name.startsWith("malaria_analyses_") && f.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: return

        backups.drop(MAX_BACKUPS).forEach { old ->
            if (old.delete()) println("🗑️ Удалена старая копия: ${old.name}")
        }
    }
}

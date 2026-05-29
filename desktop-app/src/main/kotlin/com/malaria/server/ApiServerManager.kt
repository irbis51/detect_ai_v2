package com.malaria.server

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Управляет жизненным циклом встроенного ML API (FastAPI/uvicorn), упакованного
 * PyInstaller'ом в api-server.exe.
 *
 * В собранном приложении сервер лежит в каталоге ресурсов Compose
 * (system property "compose.application.resources.dir"). При запуске desktop‑приложения
 * сервер поднимается автоматически, при закрытии — останавливается.
 *
 * Если ML API уже отвечает на http://localhost:8000/health (например, поднят вручную
 * через run-api.bat в режиме разработки) — повторный запуск не выполняется.
 * Если api-server.exe не найден (запуск из исходников без сборки) — приложение
 * продолжает работу, рассчитывая на внешний сервер.
 */
object ApiServerManager {
    private const val HEALTH_URL = "http://localhost:8000/health"
    private const val STARTUP_TIMEOUT_MS = 90_000L

    @Volatile
    private var process: Process? = null

    /** Поднимает встроенный ML API, если он ещё не запущен. Не бросает исключений. */
    fun startIfNeeded() {
        if (isHealthy()) {
            println("ℹ️ ML API уже доступен на localhost:8000 — встроенный сервер не запускаем")
            return
        }

        val exe = locateApiExe()
        if (exe == null) {
            println("⚠️ api-server.exe не найден — desktop работает в расчёте на внешний ML API")
            return
        }

        try {
            println("🔄 Запуск встроенного ML API: ${exe.absolutePath}")
            process = ProcessBuilder(exe.absolutePath)
                .directory(exe.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            waitForHealthy()
        } catch (e: Exception) {
            println("❌ Не удалось запустить встроенный ML API: ${e.message}")
        }
    }

    /** Останавливает встроенный ML API (вместе с дочерними процессами). */
    fun stop() {
        val p = process ?: return
        if (p.isAlive) {
            println("🛑 Остановка встроенного ML API")
            p.descendants().forEach { it.destroy() }
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.descendants().forEach { it.destroyForcibly() }
                p.destroyForcibly()
            }
        }
        process = null
    }

    /**
     * Ищет api-server.exe в порядке приоритета:
     * 1) каталог ресурсов собранного приложения (onefile и onedir варианты PyInstaller);
     * 2) пути для запуска из исходников (gradle run).
     */
    private fun locateApiExe(): File? {
        val candidates = mutableListOf<File>()

        System.getProperty("compose.application.resources.dir")?.let { dir ->
            candidates += File(dir, "api-server.exe")
            candidates += File(dir, "api-server/api-server.exe")
        }

        val userDir = System.getProperty("user.dir")
        candidates += File(userDir, "api-server.exe")
        candidates += File(userDir, "resources/windows/api-server.exe")
        candidates += File(userDir, "resources/windows/api-server/api-server.exe")
        candidates += File(userDir, "../ml/api/dist/api-server.exe")
        candidates += File(userDir, "../ml/api/dist/api-server/api-server.exe")

        return candidates.firstOrNull { it.isFile }
    }

    private fun isHealthy(): Boolean = try {
        val connection = (URL(HEALTH_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 1500
            readTimeout = 1500
            requestMethod = "GET"
        }
        val ok = connection.responseCode == 200
        connection.disconnect()
        ok
    } catch (e: Exception) {
        false
    }

    private fun waitForHealthy() {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) {
                println("✅ Встроенный ML API готов")
                return
            }
            Thread.sleep(1000)
        }
        println("⚠️ ML API не ответил за ${STARTUP_TIMEOUT_MS / 1000} с — приложение продолжит работу")
    }
}

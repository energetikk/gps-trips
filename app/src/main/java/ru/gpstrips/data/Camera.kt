package ru.gpstrips.data

import android.util.Base64
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Снимок с четырёх камер кругового обзора по запросу с сайта.
 *
 * Камеры магнитолы отдаёт только вендорная библиотека `liblitecamdev.so`,
 * открывать их может лишь root-процесс. Поэтому кадры делает наш бинарник
 * `camd`: получает `--shot ПАПКА` и кладёт туда четыре JPEG
 * (`front.jpg`, `back.jpg`, `left.jpg`, `right.jpg`), при ошибке — `error.txt`.
 * Здесь — запуск, чтение файлов и отправка на сайт.
 *
 * Штатный обзор 360 при этом не блокируется: `camd` держит камеры только пока
 * снимает и отпускает их сразу после кадра.
 */
object Camera {

    /** Папка обмена с camd. */
    const val DIR = "/sdcard/GpsTrips/cam"

    val NAMES = listOf("front", "back", "left", "right")

    /** Где обычно лежит camd, в порядке поиска. */
    private val BINARIES = listOf(
        "/data/adb/gpstrips-cam/bin/camd",
        "/data/local/tmp/camd"
    )

    data class Shot(val frames: Map<String, ByteArray>, val error: String?)

    /**
     * Время запроса снимка из ответа на точку: `{"ok":true,"camera":1794…}`.
     * Ноль — снимок не просят.
     */
    fun requested(body: String): Long {
        val match = Regex("\"camera\"\\s*:\\s*(\\d+)").find(body) ?: return 0
        return match.groupValues[1].toLongOrNull() ?: 0
    }

    /** Делает снимок через camd под root. Долго: 1–5 с на открытие камер. */
    fun shoot(timeoutSec: Long = 25): Shot {
        val binary = BINARIES.firstOrNull { File(it).exists() }
            ?: return Shot(emptyMap(), "camd не установлен")
        val dir = File(DIR).apply { mkdirs() }

        // старые кадры не должны уехать вместо новых
        NAMES.forEach { File(dir, "$it.jpg").delete() }
        File(dir, "error.txt").delete()

        val runError = try {
            val process = ProcessBuilder("su", "-c", "$binary --shot $DIR")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroy()
                "camd не ответил за $timeoutSec с"
            } else if (process.exitValue() != 0) {
                output.trim().ifBlank { "camd вернул код ${process.exitValue()}" }
            } else {
                null
            }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }

        val frames = NAMES.mapNotNull { name ->
            val file = File(dir, "$name.jpg")
            if (file.isFile && file.length() > 0) name to file.readBytes() else null
        }.toMap()

        val fileError = File(dir, "error.txt").takeIf { it.isFile }?.readText()?.trim()
        return when {
            frames.size == NAMES.size -> Shot(frames, null)
            frames.isEmpty() -> Shot(emptyMap(), fileError ?: runError ?: "кадров нет")
            else -> Shot(frames, fileError ?: runError)
        }
    }

    /** Отправка кадров на сайт: `POST /api/camera`. */
    fun upload(base: String, token: String, device: String, ts: Long, shot: Shot): String? {
        if (!LiveSender.isValidEndpoint(base)) return "Адрес должен начинаться с http:// или https://"

        val body = buildString {
            append('{')
            append("\"ts\":").append(ts)
            append(",\"device\":").append(LiveSender.json(device))
            append(",\"error\":").append(LiveSender.json(shot.error.orEmpty()))
            append(",\"frames\":{")
            var first = true
            for ((name, bytes) in shot.frames) {
                if (!first) append(',')
                first = false
                append(LiveSender.json(name)).append(':')
                append(LiveSender.json(Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
            append("}}")
        }
        return LiveSender.postJson(LiveSender.baseUrl(base) + "/api/camera", token, body).error
    }
}

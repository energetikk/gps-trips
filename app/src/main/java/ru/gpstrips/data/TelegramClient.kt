package ru.gpstrips.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Отправка сообщения в Telegram Bot API напрямую с магнитолы.
 * Никакого сервера не нужно: приложению достаточно токена бота и chat_id.
 * Возвращает null при успехе или текст ошибки.
 */
object TelegramClient {

    fun sendMessage(token: String, chatId: String, html: String): String? {
        if (token.isBlank()) return "Не задан токен бота"
        if (chatId.isBlank()) return "Не задан chat_id"

        val body = buildString {
            append("chat_id=").append(URLEncoder.encode(chatId, "UTF-8"))
            append("&parse_mode=HTML")
            append("&disable_web_page_preview=true")
            append("&text=").append(URLEncoder.encode(html, "UTF-8"))
        }

        return try {
            val conn = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()

            if (code in 200..299) null else "HTTP $code: ${text.take(300)}"
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }
}

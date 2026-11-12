package ru.gpstrips.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.gpstrips.data.Fmt
import ru.gpstrips.data.LiveState

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onPickFolder: (String) -> Unit,
    onSaveTelegram: (String, String) -> Unit,
    onSendSummary: () -> Unit,
    onSaveLive: (String, String, String, Int) -> Unit,
    onToggleLive: (Boolean) -> Unit,
    onToggleHistory: (Boolean) -> Unit,
    onSyncTrips: () -> Unit
) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(state.tgToken) }
    var chat by remember { mutableStateOf(state.tgChat) }
    var manualPath by remember { mutableStateOf(state.target ?: "/storage/emulated/0/GPS") }

    var liveUrl by remember { mutableStateOf(state.liveUrl) }
    var liveToken by remember { mutableStateOf(state.liveToken) }
    var liveDevice by remember { mutableStateOf(state.liveDevice) }
    var liveInterval by remember { mutableStateOf(state.liveIntervalSec.toString()) }

    val live by LiveState.status.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onPickFolder(uri.toString())
        }
    }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onToggleLive(true) }

    fun enableLive() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onToggleLive(true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("‹ Назад", onBack)
            Spacer(Modifier.width(16.dp))
            Text("Настройки", color = P.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(18.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            SectionLabel("Папка с логами GPS")
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = state.target ?: "не выбрана",
                    color = if (state.target == null) P.warn else P.text,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    ActionButton("Выбрать папку", { picker.launch(null) }, primary = true)
                    Spacer(Modifier.width(10.dp))
                    ActionButton("Разрешить доступ к файлам", {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${ctx.packageName}")
                        runCatching { ctx.startActivity(intent) }
                    })
                }
                Spacer(Modifier.height(16.dp))
                Text("Путь вручную", color = P.dim, fontSize = 11.sp, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("", manualPath) { manualPath = it }
                    Spacer(Modifier.width(10.dp))
                    ActionButton("Применить", { onPickFolder(manualPath.trim()) }, primary = true)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "На магнитоле системный выбор папки часто отсутствует. Тогда нажмите " +
                        "«Разрешить доступ к файлам» и впишите путь, например /storage/emulated/0/GPS",
                    color = P.dim,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Telegram")
            Panel(Modifier.fillMaxWidth()) {
                Field("Токен бота", token) { token = it }
                Spacer(Modifier.height(12.dp))
                Field("chat_id (группа или личка)", chat) { chat = it }
                Spacer(Modifier.height(14.dp))
                Row {
                    ActionButton(
                        "Сохранить",
                        { onSaveTelegram(token, chat) },
                        primary = true
                    )
                    Spacer(Modifier.width(10.dp))
                    ActionButton(
                        if (state.sending) "Отправляю…" else "Отправить сводку",
                        onSendSummary,
                        enabled = !state.sending
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Создайте бота у @BotFather, добавьте его в приватную группу и узнайте " +
                        "chat_id командой /start или через @userinfobot. Для группы chat_id " +
                        "отрицательный, например -1001234567890.",
                    color = P.dim,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Передача координат на сайт")
            Panel(Modifier.fillMaxWidth()) {
                Field("Адрес сервера, например https://мой-сайт.ru", liveUrl) { liveUrl = it }
                Spacer(Modifier.height(12.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Field("Токен устройства", liveToken) { liveToken = it }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.width(96.dp)) {
                        Field("Интервал, с", liveInterval) { liveInterval = it }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Field("Имя устройства", liveDevice) { liveDevice = it }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        if (state.liveEnabled) "Выключить передачу" else "Включить передачу",
                        {
                            if (state.liveEnabled) {
                                onToggleLive(false)
                            } else {
                                saveLive(liveUrl, liveToken, liveDevice, liveInterval, onSaveLive)
                                enableLive()
                            }
                        },
                        primary = !state.liveEnabled
                    )
                    Spacer(Modifier.width(10.dp))
                    ActionButton(
                        "Сохранить",
                        { saveLive(liveUrl, liveToken, liveDevice, liveInterval, onSaveLive) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        if (state.historyEnabled) "История: вкл" else "История: выкл",
                        { onToggleHistory(!state.historyEnabled) }
                    )
                    Spacer(Modifier.width(10.dp))
                    ActionButton(
                        "Отправить поездки",
                        onSyncTrips,
                        enabled = state.trips.isNotEmpty() && !live.syncing
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = liveStatusText(state, live),
                    color = when {
                        live.lastError != null -> P.warn
                        state.liveEnabled -> P.accent
                        else -> P.dim
                    },
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = syncStatusText(state, live),
                    color = when {
                        live.syncError != null -> P.warn
                        live.syncing -> P.accent
                        else -> P.dim
                    },
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Приложение читает тот же лог GPS, что и список поездок, и отправляет новые " +
                        "точки на ваш сервер. История поездок загружается при обновлении списка. " +
                        "Токен должен совпадать с TRACK_TOKEN на сервере. Сайт — в папке server.",
                    color = P.dim,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun saveLive(
    url: String,
    token: String,
    device: String,
    interval: String,
    onSaveLive: (String, String, String, Int) -> Unit
) {
    onSaveLive(url.trim(), token.trim(), device.trim(), interval.trim().toIntOrNull() ?: 3)
}

private fun liveStatusText(state: UiState, live: LiveState.Status): String = when {
    !state.liveEnabled -> "Передача выключена"
    live.lastError != null -> "Ошибка: ${live.lastError}"
    live.lastFix == null -> "Включена, жду координаты из лога…"
    else -> buildString {
        append("Передаю · ").append(Fmt.hhmm(live.lastSentAt.takeIf { it > 0 } ?: live.lastFix.timeMs))
        append(" · ").append(String.format(java.util.Locale.US, "%.5f, %.5f", live.lastFix.lat, live.lastFix.lon))
        append(" · отправлено ").append(live.sentCount)
    }
}

private fun syncStatusText(state: UiState, live: LiveState.Status): String = when {
    !state.historyEnabled -> "История поездок не выгружается"
    state.trips.isEmpty() -> "Поездок для истории пока нет"
    live.syncing -> "Выгружаю поездки: ${live.syncDone} из ${live.syncTotal}…"
    live.syncError != null -> "Ошибка истории: ${live.syncError}"
    live.lastSyncAt > 0 && live.syncTotal == 0 -> "История актуальна"
    live.lastSyncAt > 0 ->
        "Отправлено ${live.syncDone} поездок · ${Fmt.hhmm(live.lastSyncAt)}"

    else -> "История отправится при обновлении списка"
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(label, color = P.dim, fontSize = 11.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = P.text, fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(P.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(P.surface2)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

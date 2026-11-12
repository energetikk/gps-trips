package ru.gpstrips.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onPickFolder: (String) -> Unit,
    onSaveTelegram: (String, String) -> Unit,
    onSendSummary: () -> Unit
) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(state.tgToken) }
    var chat by remember { mutableStateOf(state.tgChat) }
    var manualPath by remember { mutableStateOf(state.target ?: "/storage/emulated/0/GPS") }

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
        }
    }
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

package ru.gpstrips.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Простой встроенный обзор папок: на магнитолах часто нет системного
 * «Документов», из-за чего ACTION_OPEN_DOCUMENT_TREE падает или не открывается.
 */
@Composable
fun FolderPickerDialog(
    startPath: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf(resolveStart(startPath)) }
    var folders by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(current, reloadKey) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            try {
                val dir = File(current)
                if (!dir.isDirectory) {
                    null to "Папка недоступна"
                } else {
                    val list = dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }
                    if (list == null) null to "Нет доступа к папке. Разрешите чтение файлов."
                    else list to null
                }
            } catch (e: Exception) {
                null to (e.message ?: "Не удалось прочитать папку")
            }
        }
        folders = result.first ?: emptyList()
        error = result.second
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(P.surface)
                .padding(16.dp)
                .widthIn(min = 300.dp, max = 560.dp)
        ) {
            Text("Выбор папки", color = P.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(current, color = P.dim, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    "↑ Вверх",
                    { File(current).parent?.let { current = it } },
                    enabled = File(current).parent != null
                )
                Spacer(Modifier.width(8.dp))
                ActionButton("Обновить", { reloadKey++ })
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth()) {
                when {
                    loading -> Text("Читаю папки…", color = P.dim, fontSize = 13.sp)
                    error != null -> Text(error ?: "", color = P.warn, fontSize = 13.sp)
                    folders.isEmpty() -> Text("Вложенных папок нет", color = P.dim, fontSize = 13.sp)
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(folders, key = { it.absolutePath }) { dir ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { current = dir.absolutePath }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▸ ${dir.name}", color = P.text, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton("Выбрать эту папку", { onPick(current) }, primary = true)
                ActionButton("Отмена", onDismiss)
            }
        }
    }
}

private fun resolveStart(path: String): String {
    val candidates = listOf(
        path,
        "/storage/emulated/0",
        "/storage/emulated/0/GPS",
        "/storage",
        "/sdcard",
        "/mnt/sdcard",
        "/"
    )
    return candidates.firstOrNull {
        it.isNotBlank() && File(it).isDirectory && File(it).canRead()
    } ?: "/"
}

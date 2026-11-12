package ru.gpstrips.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.gpstrips.data.LogRepository
import ru.gpstrips.data.Prefs
import ru.gpstrips.data.Report
import ru.gpstrips.data.TelegramClient
import ru.gpstrips.data.Trip

data class UiState(
    val loading: Boolean = false,
    val trips: List<Trip> = emptyList(),
    val fileCount: Int = 0,
    val target: String? = null,
    val error: String? = null,
    val toast: String? = null,
    val selected: Trip? = null,
    val tgToken: String = "",
    val tgChat: String = "",
    val sending: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repo = LogRepository(app)

    var state by mutableStateOf(
        UiState(target = prefs.target, tgToken = prefs.tgToken, tgChat = prefs.tgChat)
    )
        private set

    init {
        if (state.target != null) refresh(false)
    }

    fun setTarget(value: String) {
        prefs.target = value
        state = state.copy(target = value, error = null)
        refresh(true)
    }

    fun refresh(force: Boolean) {
        val target = state.target
        if (target.isNullOrBlank()) {
            state = state.copy(loading = false, error = "Выберите папку с логами GPS")
            return
        }
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { repo.load(target, force) }.getOrElse {
                    LogRepository.Loaded(emptyList(), 0, false, it.message ?: "Ошибка чтения папки")
                }
            }
            state = state.copy(
                loading = false,
                trips = loaded.trips,
                fileCount = loaded.files,
                error = loaded.message
            )
        }
    }

    fun openTrip(trip: Trip) {
        state = state.copy(selected = trip)
    }

    fun closeTrip() {
        state = state.copy(selected = null)
    }

    fun saveTelegram(token: String, chat: String) {
        prefs.tgToken = token
        prefs.tgChat = chat
        state = state.copy(tgToken = prefs.tgToken, tgChat = prefs.tgChat, toast = "Настройки Telegram сохранены")
    }

    fun sendSummary() {
        send(Report.summary(state.trips))
    }

    fun sendTrip(trip: Trip) {
        send(Report.trip(trip))
    }

    private fun send(html: String) {
        if (state.sending) return
        state = state.copy(sending = true, toast = null)
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                TelegramClient.sendMessage(prefs.tgToken, prefs.tgChat, html)
            }
            state = state.copy(
                sending = false,
                toast = error ?: "Отправлено в Telegram"
            )
        }
    }

    fun clearToast() {
        if (state.toast != null) state = state.copy(toast = null)
    }
}

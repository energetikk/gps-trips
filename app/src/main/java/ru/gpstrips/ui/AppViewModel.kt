package ru.gpstrips.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.gpstrips.data.LiveSender
import ru.gpstrips.data.LiveState
import ru.gpstrips.data.LiveTrackingService
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
    val sending: Boolean = false,
    val liveEnabled: Boolean = false,
    val liveUrl: String = "",
    val liveToken: String = "",
    val liveDevice: String = "car",
    val liveIntervalSec: Int = 3,
    val historyEnabled: Boolean = true
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repo = LogRepository(app)
    private var syncJob: Job? = null
    private var refreshing = false

    var state by mutableStateOf(
        UiState(
            target = prefs.target,
            tgToken = prefs.tgToken,
            tgChat = prefs.tgChat,
            liveEnabled = prefs.liveEnabled,
            liveUrl = LiveSender.baseUrl(prefs.liveUrl),
            liveToken = prefs.liveToken,
            liveDevice = prefs.liveDevice,
            liveIntervalSec = prefs.liveIntervalSec,
            historyEnabled = prefs.historyEnabled
        )
    )
        private set

    init {
        if (state.liveUrl.isNotBlank() && state.liveUrl != prefs.liveUrl) prefs.liveUrl = state.liveUrl
        if (state.target != null) refresh(false)
        if (state.liveEnabled) LiveTrackingService.start(app)
        startAutoRefresh()
    }

    /**
     * Пока приложение открыто, раз в 10 минут перечитываем логи и досылаем
     * новые поездки: иначе история на сайте ждала бы ручного «Обновить».
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_MS)
                if (state.target != null && !refreshing) refresh(false, silent = true)
            }
        }
    }

    fun setTarget(value: String) {
        prefs.target = value
        state = state.copy(target = value, error = null)
        refresh(true)
    }

    fun refresh(force: Boolean, silent: Boolean = false) {
        val target = state.target
        if (target.isNullOrBlank()) {
            state = state.copy(loading = false, error = "Выберите папку с логами GPS")
            return
        }
        if (refreshing) return
        refreshing = true
        state = state.copy(loading = !silent, error = null)
        viewModelScope.launch {
            try {
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
                syncTrips()
            } finally {
                refreshing = false
            }
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

    fun saveLive(url: String, token: String, device: String, interval: Int) {
        val base = LiveSender.baseUrl(url)
        val changed = base != LiveSender.baseUrl(prefs.liveUrl) || token != prefs.liveToken
        prefs.liveUrl = base
        prefs.liveToken = token
        prefs.liveDevice = device
        prefs.liveIntervalSec = interval
        if (changed) prefs.clearSyncedTrips()
        state = state.copy(
            liveUrl = prefs.liveUrl,
            liveToken = prefs.liveToken,
            liveDevice = prefs.liveDevice,
            liveIntervalSec = prefs.liveIntervalSec,
            toast = "Настройки передачи сохранены"
        )
        if (changed) syncTrips()
    }

    fun setLiveEnabled(enabled: Boolean) {
        prefs.liveEnabled = enabled
        if (enabled) {
            LiveTrackingService.start(getApplication())
        } else {
            LiveTrackingService.stop(getApplication())
        }
        state = state.copy(
            liveEnabled = enabled,
            toast = if (enabled) "Передача координат включена" else "Передача координат выключена"
        )
        if (enabled) syncTrips()
    }

    fun setHistoryEnabled(enabled: Boolean) {
        prefs.historyEnabled = enabled
        state = state.copy(
            historyEnabled = enabled,
            toast = if (enabled) "История поездок включена" else "История поездок выключена"
        )
        if (enabled) syncTrips()
    }

    /** Догружает на сайт поездки, которых там ещё нет (или их трек изменился). */
    fun syncTrips() {
        if (!state.historyEnabled) return
        if (state.trips.isEmpty()) return
        if (syncJob?.isActive == true) return

        val base = LiveSender.baseUrl(state.liveUrl)
        if (!LiveSender.isValidEndpoint(base)) {
            LiveState.update { it.copy(syncError = "Не задан адрес сервера") }
            return
        }
        if (prefs.liveToken.isBlank()) {
            LiveState.update { it.copy(syncError = "Не задан токен устройства") }
            return
        }

        val trips = state.trips
        syncJob = viewModelScope.launch {
            val synced = syncedMap()
            val pending = withContext(Dispatchers.Default) {
                trips.filter { synced[it.id] != signature(it) }
            }
            if (pending.isEmpty()) {
                LiveState.update {
                    it.copy(
                        syncing = false,
                        syncDone = 0,
                        syncTotal = 0,
                        syncError = null,
                        lastSyncAt = System.currentTimeMillis()
                    )
                }
                return@launch
            }

            LiveState.update {
                it.copy(syncing = true, syncDone = 0, syncTotal = pending.size, syncError = null)
            }

            val uploaded = HashMap<String, String>()
            var done = 0
            var lastError: String? = null

            for (trip in pending) {
                val error = withContext(Dispatchers.IO) {
                    LiveSender.uploadTrip(base, prefs.liveToken, prefs.liveDevice, trip)
                }
                if (error == null) {
                    uploaded[trip.id] = signature(trip)
                } else if (lastError == null) {
                    lastError = error
                }
                done++
                LiveState.update { it.copy(syncDone = done) }
                if (error != null && error.contains("HTTP 401")) break
            }

            if (uploaded.isNotEmpty()) prefs.markTripsSynced(uploaded)
            LiveState.update {
                it.copy(
                    syncing = false,
                    lastSyncAt = System.currentTimeMillis(),
                    syncError = lastError
                )
            }
        }
    }

    private fun syncedMap(): Map<String, String> {
        val out = HashMap<String, String>()
        for (entry in prefs.syncedTrips()) {
            val sep = entry.indexOf('\u001F')
            if (sep > 0) out[entry.substring(0, sep)] = entry.substring(sep + 1)
        }
        return out
    }

    private fun signature(trip: Trip): String = "v2:${trip.endMs}:${trip.track.size}"

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

    fun showToast(text: String) {
        state = state.copy(toast = text)
    }

    companion object {
        private const val AUTO_REFRESH_MS = 10 * 60 * 1000L
    }
}

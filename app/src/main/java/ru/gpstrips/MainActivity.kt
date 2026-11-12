package ru.gpstrips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.gpstrips.ui.AppViewModel
import ru.gpstrips.ui.GpsTripsTheme
import ru.gpstrips.ui.P
import ru.gpstrips.ui.SettingsScreen
import ru.gpstrips.ui.TripDetailScreen
import ru.gpstrips.ui.TripListScreen
import ru.gpstrips.ui.TripMapScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GpsTripsTheme {
                Box(Modifier.fillMaxSize().background(P.bg)) {
                    App(vm)
                }
            }
        }
    }
}

private enum class Screen { List, Detail, Settings, Map }

@Composable
private fun App(vm: AppViewModel) {
    var screen by remember { mutableStateOf(Screen.List) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.state.toast) {
        val toast = vm.state.toast
        if (toast != null) {
            snackbar.showSnackbar(toast)
            vm.clearToast()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.List -> TripListScreen(
                state = vm.state,
                onOpen = {
                    vm.openTrip(it)
                    screen = Screen.Detail
                },
                onRefresh = { vm.refresh(true) },
                onSettings = { screen = Screen.Settings }
            )

            Screen.Detail -> {
                val trip = vm.state.selected
                LaunchedEffect(trip) { if (trip == null) screen = Screen.List }
                if (trip != null) {
                    TripDetailScreen(
                        trip = trip,
                        onBack = {
                            vm.closeTrip()
                            screen = Screen.List
                        },
                        onSend = { vm.sendTrip(trip) },
                        onOpenMap = { screen = Screen.Map },
                        sending = vm.state.sending
                    )
                }
            }

            Screen.Map -> {
                val trip = vm.state.selected
                LaunchedEffect(trip) { if (trip == null) screen = Screen.List }
                if (trip != null) {
                    TripMapScreen(trip = trip, onBack = { screen = Screen.Detail })
                }
            }

            Screen.Settings -> SettingsScreen(
                state = vm.state,
                onBack = {
                    screen = Screen.List
                    vm.refresh(false)
                },
                onPickFolder = { vm.setTarget(it) },
                onSaveTelegram = { token, chat -> vm.saveTelegram(token, chat) },
                onSendSummary = { vm.sendSummary() },
                onSaveLive = { url, token, device, interval ->
                    vm.saveLive(url, token, device, interval)
                },
                onToggleLive = { vm.setLiveEnabled(it) }
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = P.surface2,
                contentColor = P.text
            )
        }
    }
}

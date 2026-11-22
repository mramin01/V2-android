package com.amin.vbox.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amin.vbox.UiState
import com.amin.vbox.VpnViewModel
import com.amin.vbox.data.ConnectionState
import com.amin.vbox.i18n.AppStrings
import com.amin.vbox.util.Format
import kotlinx.coroutines.delay

/**
 * Home screen, mirroring screenshot 1: big "VBOX" title, one big card with
 * Memory / Upload / Download / Status rows, the two toggles, Routing,
 * DNS (DoU) and Telegram Community rows. Values are real: process memory,
 * live core traffic while connected, last-session totals while disconnected,
 * and the masked outbound IP (only while connected).
 */
@Composable
fun HomeScreen(
    state: UiState,
    vm: VpnViewModel,
    onOpenRouting: () -> Unit,
    onOpenDns: () -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    val s: AppStrings = state.strings
    val context = LocalContext.current
    var memoryText by remember { mutableStateOf("—") }

    // Live memory usage, refreshed every 2 s.
    LaunchedEffect(Unit) {
        while (true) {
            memoryText = processMemory(context)
            delay(2000)
        }
    }

    val connected = state.connection == ConnectionState.CONNECTED
    val up = if (connected) state.traffic.uploadBytes else state.settings.lastUploadBytes
    val down = if (connected) state.traffic.downloadBytes else state.settings.lastDownloadBytes
    // The row must show the mode that is actually set, not a fixed "(DoU)".
    val dnsLabel = when (state.settings.dnsMode) {
        "doh" -> "DNS (DoH)"
        "dou" -> "DNS (DoU)"
        else -> "DNS (UDP)"
    }
    val statusText = when (state.connection) {
        ConnectionState.CONNECTED -> {
            val ip = Format.maskIp(state.publicIp)
            val flag = state.selectedConfig?.let { Format.flagFor(it.name) }.orEmpty()
            if (state.publicIp == null) s.connected else "$ip $flag".trim()
        }
        ConnectionState.CONNECTING -> s.connecting
        ConnectionState.ERROR -> state.connectionError ?: s.disconnected
        ConnectionState.DISCONNECTED -> s.disconnected
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("VBOX")
        VboxCard(Modifier.fillMaxWidth()) {
            Column {
                CardRow(
                    icon = Icons.Filled.Memory,
                    label = "${s.memoryUsage}: $memoryText"
                )
                CardRow(
                    icon = Icons.Filled.CloudUpload,
                    label = "${s.upload}: ${Format.bytes(up)}"
                )
                CardRow(
                    icon = Icons.Filled.Download,
                    label = "${s.download}: ${Format.bytes(down)}"
                )
                CardRow(
                    icon = Icons.Filled.Language,
                    label = "${s.status}: $statusText"
                )
                ToggleRow(
                    icon = Icons.Filled.Nightlight,
                    iconTint = VboxColors.Accent,
                    label = s.stopOnSleep,
                    checked = state.settings.stopOnSleep,
                    onCheckedChange = { vm.updateSettings { it.copy(stopOnSleep = it.stopOnSleep.not()) } },
                    onInfo = {
                        onShowInfo(
                            s.stopOnSleep,
                            s.stopOnSleepInfo
                        )
                    }
                )
                ToggleRow(
                    icon = Icons.Filled.Bolt,
                    label = s.smartConnect,
                    checked = state.settings.smartConnect,
                    onCheckedChange = { vm.updateSettings { it.copy(smartConnect = it.smartConnect.not()) } },
                    onInfo = {
                        onShowInfo(
                            s.smartConnect,
                            s.smartConnectInfo
                        )
                    }
                )
                CardRow(
                    icon = Icons.Filled.CallSplit,
                    iconTint = Color(0xFF22C55E),
                    label = s.routing,
                    labelColor = VboxColors.Accent,
                    onClick = onOpenRouting,
                    trailing = {
                        androidx.compose.material3.Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = VboxColors.GreyText
                        )
                    }
                )
                CardRow(
                    icon = Icons.Filled.Language,
                    label = dnsLabel,
                    labelColor = VboxColors.Accent,
                    onClick = onOpenDns,
                    trailing = {
                        androidx.compose.material3.Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = VboxColors.GreyText
                        )
                    }
                )
                CardRow(
                    icon = Icons.Filled.Group,
                    label = s.telegramCommunity,
                    labelColor = VboxColors.Accent,
                    onClick = {
                        onShowInfo(
                            s.telegramCommunity,
                            s.telegramCommunityInfo
                        )
                    },
                    showDivider = false,
                    trailing = {
                        androidx.compose.material3.Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = VboxColors.GreyText
                        )
                    }
                )
            }
        }
        if (state.connection == ConnectionState.DISCONNECTED && state.configs.isEmpty()) {
            VerticalSpacer(16)
            androidx.compose.material3.Text(
                s.tapToConnect,
                color = VboxColors.GreyText,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        VerticalSpacer(BottomClearanceDp)
    }
}

private fun processMemory(context: Context): String {
    return try {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        Format.bytes(used)
    } catch (_: Exception) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        Format.bytes(info.availMem)
    }
}

package com.amin.vbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amin.vbox.UiState
import com.amin.vbox.VpnViewModel
import com.amin.vbox.core.CoreEngineFactory

/** Shared sub-screen scaffold: back arrow + title + card. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        VboxCard(Modifier.fillMaxWidth()) { Column { content() } }
        VerticalSpacer(BottomClearanceDp)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, showDivider: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                null, tint = if (selected) VboxColors.Accent else VboxColors.GreyText
            )
            Spacer(Modifier.padding(8.dp))
            Text(label, color = Color.White, fontSize = 18.sp)
        }
        if (showDivider) androidx.compose.material3.Divider(color = VboxColors.Divider)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, showDivider: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VboxColors.ThumbGrey,
                    checkedTrackColor = VboxColors.ToggleOn,
                    uncheckedThumbColor = VboxColors.ThumbGrey,
                    uncheckedTrackColor = VboxColors.ToggleTrackOff,
                    uncheckedBorderColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent
                )
            )
        }
        if (showDivider) androidx.compose.material3.Divider(color = VboxColors.Divider)
    }
}

@Composable
fun AppearanceScreen(state: UiState, vm: VpnViewModel, onBack: () -> Unit) {
    val s = state.strings
    SubScreen(s.appearance, onBack) {
        RadioRow(s.pureBlack, state.settings.pureBlack, { vm.updateSettings { it.copy(pureBlack = true) } })
        RadioRow(s.darkGrey, !state.settings.pureBlack, { vm.updateSettings { it.copy(pureBlack = false) } }, showDivider = false)
    }
}

@Composable
fun TunnelScreen(state: UiState, vm: VpnViewModel, onBack: () -> Unit) {
    val s = state.strings
    var mtuText by remember(state.settings.mtu) { mutableStateOf(state.settings.mtu.toString()) }
    SubScreen(s.tunnelSettings, onBack) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(s.mtu, color = Color.White, fontSize = 18.sp)
            OutlinedTextField(
                value = mtuText,
                onValueChange = { text ->
                    mtuText = text.filter { it.isDigit() }.take(5)
                    mtuText.toIntOrNull()?.let { mtu ->
                        if (mtu in 576..9000) vm.updateSettings { it.copy(mtu = mtu) }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = VboxColors.Accent, unfocusedBorderColor = VboxColors.Divider
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        androidx.compose.material3.Divider(color = VboxColors.Divider)
        SwitchRow(s.ipv6, state.settings.ipv6, { checked -> vm.updateSettings { it.copy(ipv6 = checked) } })
        SwitchRow(s.allowLanBypass, state.settings.allowLanBypass, { checked -> vm.updateSettings { it.copy(allowLanBypass = checked) } }, showDivider = false)
    }
}

@Composable
fun DnsScreen(state: UiState, vm: VpnViewModel, onBack: () -> Unit) {
    val s = state.strings
    var serverText by remember(state.settings.dnsServer) { mutableStateOf(state.settings.dnsServer) }
    SubScreen(s.dnsSettings, onBack) {
        Text(s.dnsMode, color = VboxColors.GreyText, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        RadioRow(s.dnsUdp, state.settings.dnsMode == "udp", { vm.updateSettings { it.copy(dnsMode = "udp") } })
        RadioRow(s.dnsDoh, state.settings.dnsMode == "doh", { vm.updateSettings { it.copy(dnsMode = "doh") } })
        RadioRow(s.dnsDou, state.settings.dnsMode == "dou", { vm.updateSettings { it.copy(dnsMode = "dou") } })
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(s.dnsServer, color = Color.White, fontSize = 18.sp)
            OutlinedTextField(
                value = serverText,
                onValueChange = {
                    serverText = it
                    if (it.isNotBlank()) vm.updateSettings { st -> st.copy(dnsServer = it.trim()) }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = VboxColors.Accent, unfocusedBorderColor = VboxColors.Divider
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun RoutingScreen(state: UiState, vm: VpnViewModel, onBack: () -> Unit) {
    val s = state.strings
    SubScreen(s.routing, onBack) {
        RadioRow(s.globalRouting, state.settings.routingMode == "global", { vm.updateSettings { it.copy(routingMode = "global") } })
        RadioRow(s.bypassLanRouting, state.settings.routingMode == "bypass-lan", { vm.updateSettings { it.copy(routingMode = "bypass-lan") } }, showDivider = false)
        Text(
            s.routingNote,
            color = VboxColors.GreyText, fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}

@Composable
fun CoreStatusScreen(state: UiState, onBack: () -> Unit) {
    val s = state.strings
    val available = remember { CoreEngineFactory.create().isAvailable }
    SubScreen(s.vpnCore, onBack) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                if (available) "Ready — sing-box core is bundled." else s.coreNotReadyTitle,
                color = if (available) Color(0xFF22C55E) else VboxColors.Red,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                if (available) "Connecting opens a real sing-box tunnel."
                else s.coreNotReadyBody + "\n\n" + s.coreNotReadyHow,
                color = Color.White, fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

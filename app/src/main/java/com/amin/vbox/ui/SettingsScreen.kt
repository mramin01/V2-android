package com.amin.vbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amin.vbox.UiState
import com.amin.vbox.VpnViewModel
import com.amin.vbox.core.CoreEngineFactory
import com.amin.vbox.i18n.AppStrings

/**
 * Settings screen, mirroring screenshot 2:
 * Device section (Language, Device ID pill + copy, Public key pill + copy,
 * Reset) and Settings section (Appearance, Tunnel, DNS, Routing, Core status).
 * HWID and public key are generated fresh on first run on this device —
 * never copied from a screenshot.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    vm: VpnViewModel,
    onOpenAppearance: () -> Unit,
    onOpenTunnel: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenCore: () -> Unit,
    onShowInfo: (String, String) -> Unit
) {
    val s: AppStrings = state.strings
    val context = LocalContext.current
    val identity = vm.deviceIdentity
    var languageOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val hwid = identity.hwid
    val pubKey = identity.publicKeyFull
    // One engine lookup per composition, not one per row recomposition.
    val coreAvailable = remember { CoreEngineFactory.create().isAvailable }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle(s.settings)
        SectionLabel(s.device)
        VboxCard(Modifier.fillMaxWidth()) {
            Column {
                // Language
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { languageOpen = !languageOpen }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.language, color = Color.White, fontSize = 19.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (state.settings.language == "fa") "فارسی" else "English",
                        color = VboxColors.GreyText, fontSize = 19.sp
                    )
                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = VboxColors.GreyText)
                }
                if (languageOpen) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanguageChip("English", selected = state.settings.language == "en") { vm.setLanguage("en") }
                        LanguageChip("فارسی", selected = state.settings.language == "fa") { vm.setLanguage("fa") }
                    }
                }
                androidx.compose.material3.Divider(color = VboxColors.Divider)

                // Device ID
                Text(s.deviceId, color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(top = 16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GreyValuePill(hwid, Modifier.weight(1f))
                    Icon(
                        Icons.Filled.ContentCopy, null, tint = VboxColors.Accent,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable { vm.copyToClipboard(context, hwid) }
                    )
                }
                Text(s.deviceIdNote, color = VboxColors.GreyText, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                androidx.compose.material3.Divider(color = VboxColors.Divider)

                // Public key
                Text(s.publicKey, color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(top = 16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GreyValuePill(
                        if (pubKey.length > 40) pubKey.take(40) + "…" else pubKey,
                        Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ContentCopy, null, tint = VboxColors.Accent,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable { vm.copyToClipboard(context, pubKey) }
                    )
                }
                Row(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                    Text(s.publicKeyNote, color = VboxColors.GreyText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        s.docs, color = VboxColors.Accent, fontSize = 15.sp,
                        modifier = Modifier.clickable {
                            onShowInfo(
                                s.publicKey,
                                "This X25519 public key identifies this device. A seller can encrypt a config file so only this device's private key can open it. The private key never leaves the device."
                            )
                        }
                    )
                }
                androidx.compose.material3.Divider(color = VboxColors.Divider)

                // Reset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmReset = true }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.reset, color = VboxColors.Red, fontSize = 19.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowRight, null, tint = VboxColors.GreyText)
                }
            }
        }

        SectionLabel(s.settings)
        VboxCard(Modifier.fillMaxWidth()) {
            Column {
                CardRow(
                    icon = Icons.Filled.BrightnessMedium,
                    label = s.appearance,
                    onClick = onOpenAppearance,
                    trailing = { Icon(Icons.Filled.KeyboardArrowRight, null, tint = VboxColors.GreyText) }
                )
                CardRow(
                    icon = Icons.Filled.CallSplit,
                    label = s.tunnelSettings,
                    onClick = onOpenTunnel,
                    trailing = { Icon(Icons.Filled.KeyboardArrowRight, null, tint = VboxColors.GreyText) }
                )
                CardRow(
                    icon = Icons.Filled.Language,
                    label = s.dnsSettings,
                    onClick = onOpenDns,
                    trailing = { Icon(Icons.Filled.KeyboardArrowRight, null, tint = VboxColors.GreyText) }
                )
                CardRow(
                    icon = Icons.Filled.CallSplit,
                    iconTint = Color(0xFF22C55E),
                    label = s.routing,
                    onClick = onOpenRouting,
                    trailing = { Icon(Icons.Filled.KeyboardArrowRight, null, tint = VboxColors.GreyText) }
                )
                CardRow(
                    icon = Icons.Filled.Memory,
                    label = s.vpnCore,
                    onClick = onOpenCore,
                    showDivider = false,
                    trailing = {
                        Text(
                            if (coreAvailable) "Ready" else "Not installed",
                            color = if (coreAvailable) Color(0xFF22C55E) else VboxColors.Red,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
        VerticalSpacer(BottomClearanceDp)
    }

    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(s.confirmResetTitle) },
            text = { Text(s.confirmResetBody) },
            confirmButton = {
                Text(
                    s.reset, color = VboxColors.Red, modifier = Modifier
                        .clickable { vm.resetAll(); confirmReset = false }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                Text(s.cancel, modifier = Modifier.clickable { confirmReset = false }.padding(8.dp))
            }
        )
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) VboxColors.Accent else Color.White,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

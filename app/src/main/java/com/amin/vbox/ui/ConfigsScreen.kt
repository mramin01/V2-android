package com.amin.vbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.amin.vbox.UiState
import com.amin.vbox.VpnViewModel
import com.amin.vbox.data.Subscription
import com.amin.vbox.i18n.AppStrings
import com.amin.vbox.util.Format

/**
 * Configs screen, mirroring screenshot 3: QR icon top-left, + / list pill
 * top-right, "SORT BY: Ping" + "Ping All", group cards (Local + one per
 * subscription, with a real usage bar from the subscription-userinfo
 * header), and QR Code / Clipboard buttons at the bottom.
 */
@Composable
fun ConfigsScreen(
    state: UiState,
    vm: VpnViewModel,
    onOpenGroup: (String) -> Unit,
    onAddMenu: () -> Unit,
    onScanQr: () -> Unit,
    onRename: (Subscription) -> Unit
) {
    val s: AppStrings = state.strings
    val context = LocalContext.current
    var sortMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Top row: QR scan (left), + / sort pill (right).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VboxColors.Card)
                    .clickable { onScanQr() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(VboxColors.Card)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onAddMenu() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { sortMenuOpen = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.List, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Ping") }, onClick = { vm.setSortMode("ping"); sortMenuOpen = false })
                    DropdownMenuItem(text = { Text("Name") }, onClick = { vm.setSortMode("name"); sortMenuOpen = false })
                    DropdownMenuItem(text = { Text("None") }, onClick = { vm.setSortMode("none"); sortMenuOpen = false })
                }
            }
        }

        ScreenTitle(s.configs)

        // Sort row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(s.sortBy, color = VboxColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text(
                when (state.settings.sortMode) { "name" -> "Name"; "none" -> "None"; else -> "Ping" },
                color = Color.White, fontSize = 17.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                s.pingAll,
                color = VboxColors.Accent,
                fontSize = 17.sp,
                modifier = Modifier.clickable { vm.pingAll() }
            )
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.Settings, null, tint = VboxColors.GreyText, modifier = Modifier.size(24.dp))
        }
        VerticalSpacer(16)

        // Local group.
        val local = state.localConfigs
        GroupCard(
            title = "${s.local} (${local.size})",
            subtitle = if (local.isEmpty()) s.noInformation else "${local.size} ${s.servers}",
            usageFraction = null,
            usageLeft = null,
            usageRight = null,
            isSubscription = false,
            onClick = { onOpenGroup("local") },
            onPing = { vm.pingAll(null) },
            onMenu = null
        )
        VerticalSpacer(12)

        // Subscription groups.
        var menuSub by remember { mutableStateOf<Subscription?>(null) }
        for (sub in state.subscriptions) {
            val count = state.configsOf(sub.id).size
            val info = sub.info
            GroupCard(
                title = "${sub.name} ($count)",
                subtitle = null,
                usageFraction = info?.usageFraction,
                usageLeft = info?.let { "${Format.bytes(it.used)} / ${Format.bytes(it.total)}" },
                usageRight = info?.daysLeft?.let { "$it ${s.daysLeft}" } ?: info?.let { s.expiresNever },
                isSubscription = true,
                onClick = { onOpenGroup(sub.id) },
                onPing = { vm.pingAll(sub.id) },
                onMenu = { menuSub = sub }
            )
            if (menuSub?.id == sub.id) {
                DropdownMenu(expanded = true, onDismissRequest = { menuSub = null }) {
                    DropdownMenuItem(text = { Text(s.update) }, onClick = { vm.refreshSubscription(sub); menuSub = null })
                    DropdownMenuItem(text = { Text(s.rename) }, onClick = { onRename(sub); menuSub = null })
                    DropdownMenuItem(text = { Text(s.delete) }, onClick = { vm.deleteSubscription(sub); menuSub = null })
                }
            }
            VerticalSpacer(12)
        }

        VerticalSpacer(24)

        // Bottom actions: QR Code / Clipboard.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VboxColors.Card)
                    .clickable { onScanQr() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCodeScanner, null, tint = VboxColors.Accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(s.qrCode, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VboxColors.Card)
                    .clickable { vm.addFromClipboard(context) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ContentCopy, null, tint = VboxColors.Accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(s.clipboard, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        VerticalSpacer(180)
    }
}

@Composable
private fun GroupCard(
    title: String,
    subtitle: String?,
    usageFraction: Float?,
    usageLeft: String?,
    usageRight: String?,
    isSubscription: Boolean,
    onClick: () -> Unit,
    onPing: () -> Unit,
    onMenu: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VboxColors.Card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSubscription) {
                Icon(Icons.Filled.Refresh, null, tint = VboxColors.Accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
            } else {
                Icon(Icons.Filled.ChevronRight, null, tint = VboxColors.GreyText, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp)
                if (usageFraction != null) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { usageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = VboxColors.AccentDeep,
                        trackColor = VboxColors.ProgressTrack
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(usageLeft ?: "", color = VboxColors.GreyText, fontSize = 15.sp)
                        Text(usageRight ?: "", color = VboxColors.GreyText, fontSize = 15.sp)
                    }
                } else if (subtitle != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle, color = VboxColors.GreyText, fontSize = 15.sp)
                }
            }
            Icon(
                Icons.Filled.Speed, null, tint = VboxColors.Accent,
                modifier = Modifier
                    .size(30.dp)
                    .clickable { onPing() }
            )
            Spacer(Modifier.width(12.dp))
            if (onMenu != null) {
                Icon(
                    Icons.Filled.MoreHoriz, null, tint = VboxColors.GreyText,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { onMenu() }
                )
            }
        }
    }
}

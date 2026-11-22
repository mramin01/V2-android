package com.amin.vbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amin.vbox.UiState
import com.amin.vbox.VpnViewModel
import com.amin.vbox.data.PingTester
import com.amin.vbox.data.ProxyConfig

/**
 * Server list inside one group (Local or a subscription).
 * Tap a row to select it as the active server; the ping label is the real
 * last measured TCP latency. Ping All re-tests the whole group.
 */
@Composable
fun ServerListScreen(
    groupId: String,
    state: UiState,
    vm: VpnViewModel,
    onBack: () -> Unit
) {
    val s = state.strings
    val title = if (groupId == "local") s.local
    else state.subscriptions.firstOrNull { it.id == groupId }?.name ?: s.configs
    val list = if (groupId == "local") state.localConfigs else state.configsOf(groupId)
    // The subscription itself, for the empty-state actions below.
    val sub = state.subscriptions.firstOrNull { it.id == groupId }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                s.pingAll, color = VboxColors.Accent, fontSize = 16.sp,
                modifier = Modifier.clickable { vm.pingGroup(groupId) }
            )
        }
        if (list.isEmpty()) {
            // An empty group must explain itself and offer the next step —
            // not just "No information" on a blank screen.
            Column(Modifier.padding(16.dp)) {
                Text(s.noServers, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (sub != null) s.subscriptionNoConfigs else s.noInformation,
                    color = VboxColors.GreyText, fontSize = 16.sp
                )
                if (sub?.info?.daysLeft == 0L) {
                    Spacer(Modifier.height(8.dp))
                    Text(s.expiredNotice, color = VboxColors.Red, fontSize = 16.sp)
                }
                if (sub != null) {
                    Spacer(Modifier.height(16.dp))
                    DarkPill(
                        label = s.update,
                        icon = Icons.Filled.Refresh,
                        onClick = { vm.refreshSubscription(sub) }
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { cfg ->
                ServerRow(cfg, selected = cfg.id == state.settings.selectedConfigId, timeoutLabel = s.timeout, onSelect = {
                    vm.selectConfig(cfg.id)
                }, onDelete = { vm.deleteConfig(cfg) })
            }
            item { VerticalSpacer(BottomClearanceDp) }
        }
    }
}

@Composable
private fun ServerRow(
    cfg: ProxyConfig,
    selected: Boolean,
    timeoutLabel: String,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VboxColors.Card)
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            null,
            tint = if (selected) VboxColors.Accent else VboxColors.GreyText,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cfg.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${cfg.protocol.uppercase()} · ${cfg.displayAddress}",
                color = VboxColors.GreyText, fontSize = 14.sp
            )
        }
        Text(
            PingTester.label(cfg.pingMs, timeoutLabel),
            color = pingColor(cfg.pingMs),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, null, tint = VboxColors.GreyText, modifier = Modifier.size(20.dp))
        }
    }
}

private fun pingColor(ms: Long?): Color = when {
    ms == null -> VboxColors.GreyText
    ms < 0 -> Color(0xFFFF3B30)
    ms < 150 -> Color(0xFF22C55E)
    ms < 400 -> Color(0xFFFFB340)
    else -> Color(0xFFFF3B30)
}

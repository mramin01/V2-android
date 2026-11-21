package com.amin.vbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amin.vbox.VpnViewModel
import com.amin.vbox.data.Subscription
import com.amin.vbox.i18n.AppStrings

/** Bottom-sheet style add menu: Clipboard / QR / Manual link / Subscription. */
@Composable
fun AddConfigMenu(
    s: AppStrings,
    onDismiss: () -> Unit,
    onClipboard: () -> Unit,
    onQr: () -> Unit,
    onManual: () -> Unit,
    onSubscription: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addConfig, color = Color.White) },
        text = {
            Column {
                MenuItem(s.fromClipboard, onClipboard)
                MenuItem(s.scanQrCode, onQr)
                MenuItem(s.addManually, onManual)
                MenuItem(s.addSubscription, onSubscription)
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(s.cancel, color = VboxColors.Accent, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
        containerColor = VboxColors.Card
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 18.sp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp)
    )
}

@Composable
fun ManualLinkDialog(s: AppStrings, vm: VpnViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addManually, color = Color.White) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("vmess://… / vless://… / trojan://… / ss://…", color = VboxColors.GreyText) },
                singleLine = false, minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = VboxColors.Accent, unfocusedBorderColor = VboxColors.Divider
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Text(
                s.save, color = VboxColors.Accent,
                modifier = Modifier.clickable { vm.addLink(text); onDismiss() }.padding(8.dp)
            )
        },
        dismissButton = {
            Text(s.cancel, color = Color.White, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
        containerColor = VboxColors.Card
    )
}

@Composable
fun SubscriptionDialog(s: AppStrings, vm: VpnViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var autoUpdate by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addSubscription, color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(s.subscriptionName, color = VboxColors.GreyText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = VboxColors.Accent, unfocusedBorderColor = VboxColors.Divider
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(s.subscriptionUrl, color = VboxColors.GreyText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = VboxColors.Accent, unfocusedBorderColor = VboxColors.Divider
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.autoUpdate, color = Color.White, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = autoUpdate, onCheckedChange = { autoUpdate = it },
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
            }
        },
        confirmButton = {
            Text(
                s.save, color = VboxColors.Accent,
                modifier = Modifier.clickable {
                    if (url.isNotBlank()) vm.addSubscription(name.trim(), url.trim(), autoUpdate)
                    onDismiss()
                }.padding(8.dp)
            )
        },
        dismissButton = {
            Text(s.cancel, color = Color.White, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
        containerColor = VboxColors.Card
    )
}

@Composable
fun RenameDialog(s: AppStrings, sub: Subscription, vm: VpnViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(sub.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.rename, color = Color.White) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = VboxColors.Accent, unfocusedBorderColor = VboxColors.Divider
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Text(
                s.save, color = VboxColors.Accent,
                modifier = Modifier.clickable {
                    if (name.isNotBlank()) vm.renameSubscription(sub, name.trim())
                    onDismiss()
                }.padding(8.dp)
            )
        },
        dismissButton = {
            Text(s.cancel, color = Color.White, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
        containerColor = VboxColors.Card
    )
}

@Composable
fun InfoDialog(title: String, body: String, okLabel: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = { Text(body, color = Color.White) },
        confirmButton = {
            Text(okLabel, color = VboxColors.Accent, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
        containerColor = VboxColors.Card
    )
}

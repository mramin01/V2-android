package com.amin.vbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Big rounded card like the screenshots: #1C1C1E, ~28dp corners. */
@Composable
fun VboxCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(VboxColors.Card)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) { content() }
}

/** One row inside a card, with an icon, label and optional trailing content. */
@Composable
fun CardRow(
    icon: ImageVector,
    iconTint: Color = VboxColors.White,
    label: String,
    labelColor: Color = VboxColors.White,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, color = labelColor, fontSize = 19.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        if (showDivider) Divider(color = VboxColors.Divider, thickness = 1.dp)
    }
}

/** Toggle row: icon + label + info dot + green switch (matches screenshot). */
@Composable
fun ToggleRow(
    icon: ImageVector,
    iconTint: Color = VboxColors.White,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, color = VboxColors.White, fontSize = 19.sp)
            Spacer(Modifier.weight(1f))
            if (onInfo != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onInfo() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("ⓘ", color = VboxColors.GreyText, fontSize = 20.sp)
                }
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
        if (showDivider) Divider(color = VboxColors.Divider, thickness = 1.dp)
    }
}

/** Small dark pill, e.g. the "Ping" pill under the power button. */
@Composable
fun DarkPill(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(VboxColors.PillBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = VboxColors.PillText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = VboxColors.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** Grey value pill used for the HWID / public key (screenshot style). */
@Composable
fun GreyValuePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF8E8E93).copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text, color = VboxColors.White, fontSize = 15.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = VboxColors.GreyText,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp, top = 8.dp)
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        color = VboxColors.White,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 16.dp)
    )
}

@Composable
fun VerticalSpacer(heightDp: Int) = Spacer(Modifier.height(heightDp.dp))

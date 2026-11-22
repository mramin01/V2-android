package com.amin.vbox

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amin.vbox.core.VboxVpnService
import com.amin.vbox.data.ConfigParser
import com.amin.vbox.data.ConnectionState
import com.amin.vbox.data.Subscription
import com.amin.vbox.ui.AddConfigMenu
import com.amin.vbox.ui.AppearanceScreen
import com.amin.vbox.ui.BottomClearanceDp
import com.amin.vbox.ui.ConfigsScreen
import com.amin.vbox.ui.CoreStatusScreen
import com.amin.vbox.ui.DarkPill
import com.amin.vbox.ui.DnsScreen
import com.amin.vbox.ui.HomeScreen
import com.amin.vbox.ui.InfoDialog
import com.amin.vbox.ui.ManualLinkDialog
import com.amin.vbox.ui.RenameDialog
import com.amin.vbox.ui.RoutingScreen
import com.amin.vbox.ui.ServerListScreen
import com.amin.vbox.ui.SettingsScreen
import com.amin.vbox.ui.SubscriptionDialog
import com.amin.vbox.ui.TunnelScreen
import com.amin.vbox.ui.VboxColors
import com.amin.vbox.ui.VboxTheme
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch

/**
 * Single-activity app. Bottom pill nav (Home / Configs / Settings) with the
 * floating green power button bottom-left and the small "Ping" pill under
 * it — exactly the layout in the screenshots. The power button is visible
 * on every screen.
 */
class MainActivity : ComponentActivity() {

    private val vm: VpnViewModel by viewModels()

    // Internal (not private): VboxRoot is an extension function on this
    // activity, and extensions cannot see private members.
    internal val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) vm.onVpnPermissionGranted()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; VPN works without it */ }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startQrScan()
        else vm.showMessage(vm.state.value.strings.cameraPermissionNeeded)
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VboxVpnService.ACTION_STATE_CHANGED -> vm.onServiceState(
                    intent.getStringExtra(VboxVpnService.EXTRA_STATE).orEmpty(),
                    intent.getStringExtra(VboxVpnService.EXTRA_MESSAGE)
                )
                VboxVpnService.ACTION_TRAFFIC -> vm.onTraffic(
                    intent.getLongExtra(VboxVpnService.EXTRA_UP, 0L),
                    intent.getLongExtra(VboxVpnService.EXTRA_DOWN, 0L)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val filter = IntentFilter().apply {
            addAction(VboxVpnService.ACTION_STATE_CHANGED)
            addAction(VboxVpnService.ACTION_TRAFFIC)
        }
        ContextCompat.registerReceiver(this, serviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        handleIncomingIntent(intent)
        setContent { VboxRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(serviceReceiver) }
        super.onDestroy()
    }

    /** Shared text / proxy-link intents (browser share, link tap). */
    private fun handleIncomingIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        if (ConfigParser.extractLinks(text).isNotEmpty()) {
            vm.addLinks(text)
        }
    }

    @Deprecated("Used by ZXing IntentIntegrator (still the documented API).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            vm.addLinks(result.contents)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun startQrScan() {
        // The QR scanner needs the runtime CAMERA permission; without it the
        // scanner activity used to open on a black preview. Ask first.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            IntentIntegrator(this).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("Scan a proxy QR code")
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
            }.initiateScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
private fun MainActivity.VboxRoot(vm: VpnViewModel) {
    val state by vm.state.collectAsState()
    val s = state.strings
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var addMenuOpen by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    var subscriptionOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Subscription?>(null) }
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var coreDialog by remember { mutableStateOf(false) }
    var noConfigDialog by remember { mutableStateOf(false) }

    val activity = this

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
                is UiEvent.RequestVpnPermission -> activity.vpnPermissionLauncher.launch(event.intent)
                UiEvent.ShowCoreNotReady -> coreDialog = true
                UiEvent.ShowNoConfig -> noConfigDialog = true
                UiEvent.RequestQrScan -> activity.startQrScan()
            }
        }
    }

    val isRtl = state.settings.language == "fa"
    CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        VboxTheme(pureBlack = state.settings.pureBlack) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (state.settings.pureBlack) VboxColors.Black else VboxColors.CardDarkGrey)
            ) {
                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            state, vm,
                            onOpenRouting = { navController.navigate("routing") },
                            onOpenDns = { navController.navigate("dns") },
                            onShowInfo = { title, body -> infoDialog = title to body }
                        )
                    }
                    composable("configs") {
                        ConfigsScreen(
                            state, vm,
                            onOpenGroup = { id -> navController.navigate("servers/$id") },
                            onAddMenu = { addMenuOpen = true },
                            onScanQr = { activity.startQrScan() },
                            onRename = { renameTarget = it }
                        )
                    }
                    composable(
                        "servers/{groupId}",
                        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                    ) { entry ->
                        ServerListScreen(
                            groupId = entry.arguments?.getString("groupId") ?: "local",
                            state, vm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            state, vm,
                            onOpenAppearance = { navController.navigate("appearance") },
                            onOpenTunnel = { navController.navigate("tunnel") },
                            onOpenDns = { navController.navigate("dns") },
                            onOpenRouting = { navController.navigate("routing") },
                            onOpenCore = { navController.navigate("core") },
                            onShowInfo = { title, body -> infoDialog = title to body }
                        )
                    }
                    composable("appearance") { AppearanceScreen(state, vm) { navController.popBackStack() } }
                    composable("tunnel") { TunnelScreen(state, vm) { navController.popBackStack() } }
                    composable("dns") { DnsScreen(state, vm) { navController.popBackStack() } }
                    composable("routing") { RoutingScreen(state, vm) { navController.popBackStack() } }
                    composable("core") { CoreStatusScreen(state) { navController.popBackStack() } }
                }

                // ---- bottom overlay: one Column, no absolute offsets ----
                // Power row (power button + ping pill) above a capped,
                // weight-based nav bar. Nothing here can overlap anything
                // else, whatever the font/display scale is. The old layout
                // used fixed offsets: on narrower screens the ping pill hid
                // behind the nav bar (it looked like a cut-off 4th tab) and
                // the power button could touch the bar when text scaled up.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    val connected = state.connection == ConnectionState.CONNECTED
                    val connecting = state.connection == ConnectionState.CONNECTING
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .shadow(if (connected) 24.dp else 8.dp, CircleShape, spotColor = VboxColors.Green)
                                .clip(CircleShape)
                                .background(if (connected) VboxColors.Green else Color(0xFF2C2C2E))
                                .clickable { vm.onPowerPressed() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (connecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp), strokeWidth = 4.dp)
                            } else {
                                Icon(
                                    Icons.Filled.PowerSettingsNew, null,
                                    tint = Color.White, modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        DarkPill(
                            label = s.ping,
                            icon = Icons.Filled.ShowChart,
                            onClick = { vm.pingSelected() }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(8.dp, RoundedCornerShape(40.dp))
                            .clip(RoundedCornerShape(40.dp))
                            .background(VboxColors.Card)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NavPill(
                                label = s.home, icon = Icons.Filled.Home,
                                selected = route == "home",
                                onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                                modifier = Modifier.weight(1f)
                            )
                            NavPill(
                                label = s.configs, icon = Icons.Filled.List,
                                selected = route == "configs" || route.startsWith("servers/"),
                                onClick = { navController.navigate("configs") { popUpTo("home") } },
                                modifier = Modifier.weight(1f)
                            )
                            NavPill(
                                label = s.settings, icon = Icons.Filled.Settings,
                                selected = route == "settings" || route in listOf("appearance", "tunnel", "dns", "routing", "core"),
                                onClick = { navController.navigate("settings") { popUpTo("home") } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = BottomClearanceDp.dp))
            }
        }
    }

    // ---- dialogs ----
    if (addMenuOpen) {
        AddConfigMenu(
            s,
            onDismiss = { addMenuOpen = false },
            onClipboard = { addMenuOpen = false; vm.addFromClipboard(activity) },
            onQr = { addMenuOpen = false; activity.startQrScan() },
            onManual = { addMenuOpen = false; manualOpen = true },
            onSubscription = { addMenuOpen = false; subscriptionOpen = true }
        )
    }
    if (manualOpen) ManualLinkDialog(s, vm) { manualOpen = false }
    if (subscriptionOpen) SubscriptionDialog(s, vm) { subscriptionOpen = false }
    renameTarget?.let { sub -> RenameDialog(s, sub, vm) { renameTarget = null } }
    infoDialog?.let { (title, body) -> InfoDialog(title, body, s.ok) { infoDialog = null } }
    if (coreDialog) {
        InfoDialog(
            s.coreNotReadyTitle,
            s.coreNotReadyBody + "\n\n" + s.coreNotReadyHow,
            s.ok
        ) { coreDialog = false }
    }
    if (noConfigDialog) {
        InfoDialog(
            s.noConfigSelected,
            s.addConfig,
            s.ok
        ) {
            noConfigDialog = false
            navController.navigate("configs")
        }
    }
}

@Composable
private fun NavPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Weight-distributed (not fixed padding), so the three tabs always fit
    // the screen width, in either language and at any font scale.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(if (selected) Color(0xFF000000) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (selected) VboxColors.Accent else Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) VboxColors.Accent else Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

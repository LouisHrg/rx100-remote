@file:OptIn(ExperimentalMaterial3Api::class)

package fr.rx100remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Material You: wallpaper-based colors on Android 12+, baseline M3 palette before. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(vm: CameraViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showLogs by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            vm.toastShown()
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    val live = !showLogs && (state.phase == Phase.Live || state.phase == Phase.Reconnecting)
    // The viewfinder is always dark, like a camera app; the other screens follow the system.
    AppTheme(dark = if (live) true else isSystemInDarkTheme()) {
        when {
            showLogs -> {
                BackHandler { showLogs = false }
                LogsScreen(vm, snackbar) { showLogs = false }
            }
            live -> LiveScreen(vm, state, snackbar, onLogs = { showLogs = true })
            else -> ConnectScreen(vm, state, snackbar, onLogs = { showLogs = true })
        }
    }
}

private enum class Step { Pending, Working, Done, Failed }

@Composable
private fun ConnectScreen(vm: CameraViewModel, state: UiState, snackbar: SnackbarHostState, onLogs: () -> Unit) {
    val working = state.phase in setOf(Phase.Wifi, Phase.Discovering, Phase.Connecting)
    val failed = state.phase == Phase.Error
    val wifiOk = state.wifi.startsWith("Wi-Fi lié")
    val wifiStep = when {
        wifiOk -> Step.Done
        state.phase == Phase.Wifi -> Step.Working
        failed -> Step.Failed
        else -> Step.Pending
    }
    val deviceStep = when {
        state.apis.isNotEmpty() -> Step.Done
        state.phase == Phase.Discovering || state.phase == Phase.Connecting -> Step.Working
        failed && wifiOk -> Step.Failed
        else -> Step.Pending
    }
    val apiStep = when {
        "startLiveview" in state.apis -> Step.Done
        state.apis.isNotEmpty() && working -> Step.Working
        failed && state.apis.isNotEmpty() -> Step.Failed
        else -> Step.Pending
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("RX100 Remote") },
                actions = {
                    IconButton(onClick = onLogs) { Icon(Icons.Outlined.BugReport, contentDescription = "Logs") }
                },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val content = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            Card(content, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                StepRow(Icons.Outlined.Wifi, "Wi-Fi de l'appareil", state.wifi.removePrefix("Wi-Fi lié — "), wifiStep)
                StepRow(Icons.Outlined.PhotoCamera, "Appareil", state.endpoint ?: "Non trouvé", deviceStep)
                StepRow(
                    Icons.Outlined.Checklist, "Fonctions disponibles",
                    if (state.apis.isEmpty()) "—" else "${state.apis.size} API · ${state.apis.sorted().joinToString(", ")}",
                    apiStep,
                )
            }

            if (state.message.isNotEmpty() && state.phase != Phase.Idle) {
                Card(
                    content,
                    colors = if (failed) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(onClick = { vm.connect() }, enabled = !working, modifier = content, contentPadding = PaddingValues(16.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("Réessayer", Modifier.padding(start = 8.dp))
            }

            Column(content, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connexion manuelle", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = state.manualUrl,
                    onValueChange = vm::setManualUrl,
                    label = { Text("URL de l'API") },
                    supportingText = { Text("Utilisée si la découverte automatique (SSDP) échoue") },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledTonalButton(onClick = { vm.connect(useManual = true) }, enabled = !working, modifier = Modifier.align(Alignment.End)) {
                    Text("Connecter à cette URL")
                }
            }

            Card(content, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Préparer l'appareil", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "MENU › Appli › Contrôle par smartphone (Smart Remote Control).",
                        "Connecte le téléphone au Wi-Fi DIRECT-xxxx:DSC-RX100M3 affiché.",
                        "Si Android signale l'absence d'internet, choisis « Rester connecté ».",
                    ).forEachIndexed { i, step ->
                        Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(icon: ImageVector, title: String, detail: String, step: Step) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail, maxLines = 3) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            when (step) {
                Step.Working -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Step.Done -> Icon(Icons.Outlined.CheckCircle, "OK", tint = MaterialTheme.colorScheme.primary)
                Step.Failed -> Icon(Icons.Outlined.ErrorOutline, "Échec", tint = MaterialTheme.colorScheme.error)
                Step.Pending -> Icon(Icons.Outlined.RadioButtonUnchecked, "En attente", tint = MaterialTheme.colorScheme.outline)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun LogsScreen(vm: CameraViewModel, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(onClick = {
                        // Clipboard goes through Binder (~1 MB limit): keep the most recent part.
                        val text = logs.joinToString("\n").takeLast(500_000)
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("RX100 logs", text))
                        // Android 13+ shows its own clipboard confirmation.
                        if (Build.VERSION.SDK_INT < 33) vm.notify("Logs copiés (${logs.size} lignes)")
                    }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copier") }
                    IconButton(onClick = vm::clearLogs) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Effacer") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (logs.isEmpty()) {
                Text("Aucun log", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SelectionContainer {
                LazyColumn(state = listState, contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(logs) { line ->
                        Text(
                            line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                            color = if (line.contains("ERREUR") || line.contains("erreur")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

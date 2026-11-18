package fr.rx100remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun App(vm: CameraViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showLogs by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toastShown()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            when {
                showLogs -> {
                    BackHandler { showLogs = false }
                    LogsScreen(vm) { showLogs = false }
                }
                state.phase == Phase.Live || state.phase == Phase.Reconnecting ->
                    LiveScreen(vm, state, onLogs = { showLogs = true })
                else -> ConnectScreen(vm, state, onLogs = { showLogs = true })
            }
        }
    }
}

@Composable
private fun ConnectScreen(vm: CameraViewModel, state: UiState, onLogs: () -> Unit) {
    val working = state.phase in setOf(Phase.Wifi, Phase.Discovering, Phase.Connecting)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("RX100 Remote", style = MaterialTheme.typography.headlineSmall)
        Text(state.wifi)
        Text("Appareil : " + (state.endpoint ?: "non trouvé"))
        Text("API disponibles : " + if (state.apis.isEmpty()) "—" else "${state.apis.size}")
        if (state.apis.isNotEmpty()) {
            Text(state.apis.sorted().joinToString(", "), fontSize = 12.sp, color = Color.Gray)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (working) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(state.message, color = if (state.phase == Phase.Error) Color(0xFFFF8A80) else Color.Unspecified)
        }
        Button(onClick = { vm.connect() }, enabled = !working) { Text("Réessayer (SSDP)") }
        OutlinedTextField(
            value = state.manualUrl,
            onValueChange = vm::setManualUrl,
            label = { Text("URL de l'API (repli)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { vm.connect(useManual = true) }, enabled = !working) { Text("Connecter à cette URL") }
        OutlinedButton(onClick = onLogs) { Text("Logs") }
        Text(
            "Sur l'appareil : MENU › Appli › Contrôle par smartphone (Smart Remote Control). " +
                "Puis connecte le téléphone au Wi-Fi DIRECT-xxxx:DSC-RX100M3 affiché, et choisis « rester connecté » si Android signale l'absence d'internet.",
            fontSize = 12.sp, color = Color.Gray,
        )
    }
}

@Composable
private fun LiveScreen(vm: CameraViewModel, state: UiState, onLogs: () -> Unit) {
    val frame by vm.frame.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { vm.disconnect() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Live view",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (state.mirror) -1f else 1f },
            )
        }
        if (state.phase == Phase.Reconnecting) {
            Text(state.message, Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(12.dp))
        }
        Column(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Logs", onLogs)
                SmallButton(if (state.mirror) "Miroir ✓" else "Miroir", vm::toggleMirror)
                SmallButton("Stats", vm::toggleStats)
                SmallButton("Quitter", vm::disconnect)
            }
            if (state.showStats) {
                Text(
                    "%.1f fps · décodage %d ms · âge %d ms · resync %d".format(state.fps, state.decodeMs, state.frameAgeMs, state.resyncs),
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).background(Color(0x88000000)).padding(4.dp),
                )
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val zoom = "actZoom" in state.apis
            if (zoom) SmallButton("Zoom +") { vm.zoom("in") }
            Button(
                onClick = vm::takePicture,
                enabled = !state.busy,
                modifier = Modifier.size(72.dp).border(4.dp, Color.White, CircleShape),
                shape = CircleShape,
            ) { Text("●", fontSize = 24.sp) }
            if (zoom) SmallButton("Zoom −") { vm.zoom("out") }
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) { Text(text, fontSize = 13.sp) }
}

@Composable
private fun LogsScreen(vm: CameraViewModel, onClose: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // Clipboard goes through Binder (~1 MB limit): keep the most recent part.
                val text = logs.joinToString("\n").takeLast(500_000)
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("RX100 logs", text))
                Toast.makeText(context, "Logs copiés (${logs.size} lignes)", Toast.LENGTH_SHORT).show()
            }) { Text("Copier") }
            OutlinedButton(onClick = vm::clearLogs) { Text("Effacer") }
            OutlinedButton(onClick = onClose) { Text("Fermer") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

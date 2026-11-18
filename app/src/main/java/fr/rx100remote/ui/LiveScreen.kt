package fr.rx100remote.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val Scrim = Color.Black.copy(alpha = 0.55f)
private val RecRed = Color(0xFFE53935)

@Composable
fun LiveScreen(vm: CameraViewModel, state: UiState, snackbar: SnackbarHostState, onLogs: () -> Unit) {
    val frame by vm.frame.collectAsStateWithLifecycle()
    ImmersiveKeepScreenOn()
    BackHandler { vm.disconnect() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Visée de l'appareil",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (state.mirror) -1f else 1f },
            )
        }

        TopBar(vm, state, onLogs, Modifier.align(Alignment.TopCenter))

        if (state.phase == Phase.Reconnecting) {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(24.dp), color = Scrim, contentColor = Color.White) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(state.message, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (landscape) {
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Scrim)))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ZoomButton(vm, state, "in")
                ShutterButton(state, vm::shutter)
                ZoomButton(vm, state, "out")
                ModeSelector(vm, state)
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim)))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(top = 32.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModeSelector(vm, state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ZoomButton(vm, state, "out")
                    ShutterButton(state, vm::shutter)
                    ZoomButton(vm, state, "in")
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.Center).padding(top = 160.dp),
        )
    }
}

/** Full-screen viewfinder: system bars hidden (swipe to reveal) and screen kept on, only while live. */
@Composable
private fun ImmersiveKeepScreenOn() {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

@Composable
private fun TopBar(vm: CameraViewModel, state: UiState, onLogs: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = vm::disconnect) { Icon(Icons.Outlined.Close, contentDescription = "Déconnecter") }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconToggleButton(checked = state.mirror, onCheckedChange = { vm.toggleMirror() }) {
                    Icon(Icons.Outlined.Flip, contentDescription = "Miroir horizontal")
                }
                FilledTonalIconToggleButton(checked = state.showStats, onCheckedChange = { vm.toggleStats() }) {
                    Icon(Icons.Outlined.Speed, contentDescription = "Statistiques")
                }
                FilledTonalIconButton(onClick = onLogs) { Icon(Icons.Outlined.BugReport, contentDescription = "Logs") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.recStartedAt?.let { RecChip(it) }
            val storage = storageText(state)
            when {
                storage != null -> InfoChip(Icons.Outlined.SdCard, storage)
                state.eventStatus != null -> InfoChip(Icons.Outlined.Info, state.eventStatus)
                else -> InfoChip(Icons.Outlined.Info, "Capacité de la carte non fournie par l'appareil")
            }
        }
        if (state.showStats) {
            InfoChip(
                Icons.Outlined.Speed,
                "%.1f fps · décodage %d ms · âge %d ms · resync %d".format(state.fps, state.decodeMs, state.frameAgeMs, state.resyncs),
                mono = true,
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, mono: Boolean = false) {
    Surface(shape = RoundedCornerShape(12.dp), color = Scrim, contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(16.dp))
            Text(
                text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                fontFamily = if (mono) FontFamily.Monospace else null,
            )
        }
    }
}

@Composable
private fun RecChip(startedAt: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot",
    )
    val sec = ((now - startedAt) / 1000).coerceAtLeast(0)
    Surface(shape = RoundedCornerShape(12.dp), color = RecRed, contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(blink).clip(CircleShape).background(Color.White))
            Text(
                "REC  %02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60),
                Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Remaining capacity for the current mode, from getEvent's storageInformation. */
private fun storageText(state: UiState): String? {
    val cam = state.camera
    if (cam.hasStorage == false) return "Pas de carte"
    return if (state.movieMode) {
        val min = cam.recordableMinutes ?: return null
        if (min < 0) "Temps restant inconnu" else "%d h %02d min restantes".format(min / 60, min % 60)
    } else {
        val n = cam.recordableImages ?: return null
        if (n < 0) "Photos restantes inconnues" else "$n photos restantes"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(vm: CameraViewModel, state: UiState) {
    // Always shown: the camera's own candidate/API lists vary with the current mode, so we let
    // setShootMode answer instead (a refusal shows up as a message). Disabled while recording.
    SingleChoiceSegmentedButtonRow {
        listOf("still" to ("Photo" to Icons.Outlined.PhotoCamera), "movie" to ("Vidéo" to Icons.Outlined.Videocam))
            .forEachIndexed { i, (mode, ui) ->
                SegmentedButton(
                    selected = state.movieMode == (mode == "movie"),
                    onClick = { if (state.movieMode != (mode == "movie")) vm.setShootMode(mode) },
                    enabled = !state.recording && !state.busy,
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    icon = { Icon(ui.second, contentDescription = null, Modifier.size(18.dp)) },
                    colors = SegmentedButtonDefaults.colors(inactiveContainerColor = Scrim),
                ) { Text(ui.first) }
            }
    }
}

@Composable
private fun ZoomButton(vm: CameraViewModel, state: UiState, direction: String) {
    if ("actZoom" !in state.apis) {
        Spacer(Modifier.size(52.dp))
        return
    }
    FilledTonalIconButton(onClick = { vm.zoom(direction) }, modifier = Modifier.size(52.dp)) {
        Icon(
            if (direction == "in") Icons.Outlined.ZoomIn else Icons.Outlined.ZoomOut,
            contentDescription = if (direction == "in") "Zoom avant" else "Zoom arrière",
        )
    }
}

/**
 * Camera-style shutter: white ring; inner white disc (photo), red disc (video idle),
 * red rounded square (recording, tap to stop). Shape and color animate between states.
 */
@Composable
private fun ShutterButton(state: UiState, onClick: () -> Unit) {
    val view = LocalView.current
    val inner by animateDpAsState(if (state.recording) 30.dp else 62.dp, label = "size")
    val corner by animateDpAsState(if (state.recording) 8.dp else 31.dp, label = "corner")
    val color by animateColorAsState(if (state.movieMode) RecRed else Color.White, label = "color")
    val label = when {
        state.recording -> "Arrêter l'enregistrement"
        state.movieMode -> "Lancer l'enregistrement"
        else -> "Prendre une photo"
    }
    Box(
        Modifier.size(80.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)
            .clickable(enabled = !state.busy, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(color.copy(alpha = if (state.busy) 0.5f else 1f)))
    }
}

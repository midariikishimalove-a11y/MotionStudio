package studio.motion

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(filesDir)
        val editor = Editor(store.loadLast() ?: Project())
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Screen(editor, store) } } }
    }
}

private fun durationOf(ctx: Context, u: Uri): Long = runCatching {
    MediaMetadataRetriever().use { it.setDataSource(ctx, u); it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() }
}.getOrDefault(0L)

@OptIn(UnstableApi::class)
@Composable
private fun Screen(ed: Editor, store: Store) {
    val ctx = LocalContext.current; val p = ed.project
    val player = remember { CompositionPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var pos by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(40f) }
    var height by remember { mutableIntStateOf(1080) }; var fps by remember { mutableIntStateOf(30) }
    var status by remember { mutableStateOf("") }; var tf by remember { mutableStateOf<Transformer?>(null) }
    var targetTrack by remember { mutableIntStateOf(0) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val d = durationOf(ctx, u); if (d > 0) ed.addClip(targetTrack, Clip(uri = u.toString(), durationMs = d))
        }
    }
    val pickLut = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        val id = ed.selected
        if (u != null && id != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            ed.update(id) { it.copy(lutUri = u.toString()) }
        }
    }

    LaunchedEffect(p) { delay(700); store.save(p) }                       // autosave com debounce
    LaunchedEffect(p.tracks, p.aspect) {                                  // preview = mesma composition do export
        if (p.tracks.any { it.isNotEmpty() }) {
            val keep = player.currentPosition
            player.setComposition(Render.composition(ctx, p)); player.prepare(); player.seekTo(keep)
        }
    }
    LaunchedEffect(Unit) { while (true) { pos = player.currentPosition; delay(100) } }
    LaunchedEffect(tf) {
        val t = tf ?: return@LaunchedEffect; val h = ProgressHolder()
        while (tf != null) { if (t.getProgress(h) != Transformer.PROGRESS_STATE_NOT_STARTED) status = "Exportando ${h.progress}%"; delay(300) }
    }

    val total = p.tracks[0].sumOf { it.lengthMs }.coerceAtLeast(1L)
    val sel = p.tracks.flatten().firstOrNull { it.id == ed.selected }

    Column(Modifier.fillMaxSize().padding(8.dp).verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AndroidView({ c -> SurfaceView(c).also { player.setVideoSurfaceView(it) } },
            Modifier.fillMaxWidth().aspectRatio(p.aspect).background(Color.Black))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton({ player.playWhenReady = !player.playWhenReady }) { Text("▶/⏸") }
            Slider(pos.toFloat().coerceIn(0f, total.toFloat()), { pos = it.toLong(); player.seekTo(pos) }, Modifier.weight(1f), valueRange = 0f..total.toFloat())
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button({ targetTrack = 0; pickVideo.launch(arrayOf("video/*")) }) { Text("+ Principal") }
            Button({ targetTrack = 1; pickVideo.launch(arrayOf("video/*")) }) { Text("+ Sobreposição") }
            OutlinedButton({ ed.split(pos) }) { Text("Dividir") }
            OutlinedButton({ sel?.let { ed.delete(it.id) } }) { Text("Excluir") }
            OutlinedButton({ ed.undo() }) { Text("Desfazer") }
            OutlinedButton({ ed.redo() }) { Text("Refazer") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(16f / 9f to "16:9", 9f / 16f to "9:16", 1f to "1:1", 4f / 3f to "4:3").forEach { (r, n) ->
                FilterChip(p.aspect == r, { ed.edit { it.copy(aspect = r) } }, { Text(n) })
            }
            Slider(zoom, { zoom = it }, Modifier.weight(1f), valueRange = 10f..120f)
        }
        p.tracks.forEachIndexed { ti, track ->               // timeline multicamadas
            Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth().height(44.dp).background(Color(0xFF1B1B1F))) {
                track.forEach { c ->
                    Box(Modifier.width((c.lengthMs / 1000f * zoom).dp.coerceAtLeast(12.dp)).fillMaxHeight().padding(1.dp)
                        .background(if (c.id == ed.selected) Color(0xFF7C4DFF) else if (ti == 0) Color(0xFF2E7D6B) else Color(0xFF3D5AFE))
                        .clickable { ed.selected = c.id }) { Text("${c.lengthMs / 1000}s", Modifier.padding(4.dp), color = Color.White) }
                }
            }
        }
        sel?.let { c ->
            Text("Clip selecionado", style = MaterialTheme.typography.labelLarge)
            Param("Velocidade ${"%.2f".format(c.speed)}x", c.speed, 0.25f..4f) { v -> ed.update(c.id) { it.copy(speed = v) } }
            Param("Volume ${"%.0f".format(c.volume * 100)}%", c.volume, 0f..2f) { v -> ed.update(c.id) { it.copy(volume = v) } }
            Param("Brilho", c.brightness, -1f..1f) { v -> ed.update(c.id) { it.copy(brightness = v) } }
            Param("Contraste", c.contrast, -1f..1f) { v -> ed.update(c.id) { it.copy(contrast = v) } }
            Param("Saturação (HSL)", c.saturation, -100f..100f) { v -> ed.update(c.id) { it.copy(saturation = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(c.grayscale, { ed.update(c.id) { it.copy(grayscale = !it.grayscale) } }, { Text("P&B") })
                FilterChip(c.chromaKey != null, { ed.update(c.id) { it.copy(chromaKey = if (it.chromaKey == null) 0xFF00FF00.toInt() else null) } }, { Text("Chroma key (verde)") })
                OutlinedButton({ pickLut.launch(arrayOf("*/*")) }) { Text(if (c.lutUri == null) "LUT .cube" else "LUT ✓") }
            }
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(720, 1080, 1440, 2160).forEach { h -> FilterChip(height == h, { height = h }, { Text(if (h == 2160) "4K" else "${h}p") }) }
            listOf(30, 60).forEach { f -> FilterChip(fps == f, { fps = f }, { Text("${f}fps") }) }
        }
        Button(onClick = {
            val out = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "motion_${System.currentTimeMillis()}.mp4")
            status = "Exportando…"
            tf = Render.export(ctx, p, height, fps, null, hevc = false, out) { r ->
                tf = null; status = r.fold({ "Salvo em ${it.path}" }, { "Erro: ${it.message}" })
            }
        }, enabled = tf == null && total > 1) { Text("Exportar") }
        if (status.isNotEmpty()) Text(status)
    }
}

@Composable private fun Param(label: String, v: Float, r: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Text(label, style = MaterialTheme.typography.bodySmall); Slider(v, onChange, valueRange = r) }
}

@Composable private fun Modifier.verticalScrollCompat() = this.then(Modifier.verticalScroll(rememberScrollState()))

package studio.motion

import androidx.compose.runtime.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/** Estado do editor com desfazer/refazer (snapshots imutáveis do Project). */
class Editor(initial: Project) {
    var project by mutableStateOf(initial); private set
    var selected by mutableStateOf<String?>(null)
    private val past = ArrayDeque<Project>()
    private val future = ArrayDeque<Project>()

    fun edit(f: (Project) -> Project) {
        val n = f(project); if (n == project) return
        past.addLast(project); if (past.size > 100) past.removeFirst()
        future.clear(); project = n
    }
    fun undo() { past.removeLastOrNull()?.let { future.addLast(project); project = it } }
    fun redo() { future.removeLastOrNull()?.let { past.addLast(project); project = it } }

    fun addClip(track: Int, c: Clip) = edit { p ->
        p.copy(tracks = p.tracks.mapIndexed { i, t -> if (i == track) t + c else t })
    }
    fun update(id: String, f: (Clip) -> Clip) = edit { p ->
        p.copy(tracks = p.tracks.map { t -> t.map { if (it.id == id) f(it) else it } })
    }
    fun delete(id: String) = edit { p -> p.copy(tracks = p.tracks.map { t -> t.filter { it.id != id } }) }

    /** Divide o clip da trilha principal que contém [posMs] (posição na timeline). */
    fun split(posMs: Long) = edit { p ->
        var t = 0L; val out = mutableListOf<Clip>()
        for (c in p.tracks[0]) {
            val len = c.lengthMs
            if (posMs > t + 100 && posMs < t + len - 100) {
                val cut = c.inMs + ((posMs - t) * c.speed).toLong()
                out += c.copy(outMs = cut); out += c.copy(id = newId(), inMs = cut)
            } else out += c
            t += len
        }
        p.copy(tracks = listOf<List<Clip>>(out) + p.tracks.drop(1))
    }
}

/** Persistência local em JSON; o autosave é disparado pela UI com debounce. */
class Store(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun save(p: Project) {
        File(dir, "${p.id}.json").writeText(json.encodeToString(p)); File(dir, "last").writeText(p.id)
    }
    fun loadLast(): Project? = runCatching {
        json.decodeFromString<Project>(File(dir, File(dir, "last").readText() + ".json").readText())
    }.getOrNull()
}

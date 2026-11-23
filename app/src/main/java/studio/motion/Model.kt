package studio.motion

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId() = UUID.randomUUID().toString()

/** Um trecho de mídia na timeline. Tempos em ms. Todos os campos são serializáveis (projeto editável). */
@Serializable
data class Clip(
    val id: String = newId(),
    val uri: String,
    val durationMs: Long,
    val inMs: Long = 0,
    val outMs: Long = durationMs,
    val speed: Float = 1f,        // 0.25..4
    val volume: Float = 1f,       // 0..2
    val brightness: Float = 0f,   // -1..1
    val contrast: Float = 0f,     // -1..1
    val saturation: Float = 0f,   // -100..100 (HSL)
    val grayscale: Boolean = false,
    val chromaKey: Int? = null,   // cor ARGB a remover
    val lutUri: String? = null,   // arquivo .cube
) {
    val lengthMs: Long get() = ((outMs - inMs) / speed).toLong()
}

/** tracks[0] = trilha principal; as demais são sobreposições (compostas por cima). */
@Serializable
data class Project(
    val id: String = newId(),
    val name: String = "Projeto",
    val aspect: Float = 16f / 9f,
    val tracks: List<List<Clip>> = listOf(emptyList(), emptyList()),
)

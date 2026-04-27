package com.mauro.api.service

import com.mauro.api.model.*
import com.mauro.api.repository.ExperimentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class ExperimentService(private val repository: ExperimentRepository) {

    fun findAll(): List<Experiment> = repository.findAll()

    fun findById(id: Long): Experiment =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment $id not found")
        }

    fun findFavorites(): List<Experiment> = repository.findByFavoriteTrue()

    fun findByType(type: ExperimentType): List<Experiment> = repository.findByType(type)

    fun search(query: String): List<Experiment> = repository.findByTitleContainingIgnoreCase(query)

    fun findByTag(tag: String): List<Experiment> = repository.findByTag(tag)

    fun getTypes(): List<ExperimentType> = repository.findDistinctTypes()

    fun getStats(): Map<String, Any> {
        val total = repository.count()
        val favorites = repository.countByFavoriteTrue()
        // Can't easily count by type dynamically in one query, so we do it per type
        val byType = ExperimentType.entries.associate { it.name.lowercase() to repository.countByType(it) }
        return mapOf(
            "total" to total,
            "favorites" to favorites,
            "byType" to byType
        )
    }

    fun create(request: CreateExperimentRequest): Experiment {
        return repository.save(
            Experiment(
                title = request.title,
                description = request.description,
                type = request.type,
                bpm = request.bpm,
                tags = request.tags,
            )
        )
    }

    fun update(id: Long, request: UpdateExperimentRequest): Experiment {
        val exp = findById(id)
        val updated = exp.copy(
            title = request.title ?: exp.title,
            description = request.description ?: exp.description,
            type = request.type ?: exp.type,
            bpm = request.bpm ?: exp.bpm,
            chainJson = request.chainJson ?: exp.chainJson,
            waveformData = request.waveformData ?: exp.waveformData,
            audioDataUrl = request.audioDataUrl ?: exp.audioDataUrl,
            favorite = request.favorite ?: exp.favorite,
            tags = request.tags ?: exp.tags,
        )
        return repository.save(updated)
    }

    fun toggleFavorite(id: Long): Experiment {
        val exp = findById(id)
        return repository.save(exp.copy(favorite = !exp.favorite))
    }

    fun duplicate(id: Long): Experiment {
        val exp = findById(id)
        return repository.save(
            exp.copy(
                id = null,
                title = "${exp.title} (copy)",
                createdAt = null,
            )
        )
    }

    fun random(): Experiment = repository.findAll().randomOrNull()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No experiments yet. Create one!")

    fun delete(id: Long) {
        if (!repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment $id not found")
        }
        repository.deleteById(id)
    }

    // Count favorites
    private fun countByFavoriteTrue() = repository.findByFavoriteTrue().size.toLong()

    /**
     * Renders audio server-side: generates a WAV as base64 data URL.
     * Uses pure math — no external audio libraries needed.
     */
    fun render(request: RenderRequest): RenderResponse {
        val sampleRate = 44100
        val numSamples = (sampleRate * request.duration).toInt()
        val samples = FloatArray(numSamples)
        val freqBase = 110.0 // A2
        val params = request.params

        when (request.type) {
            ExperimentType.DRONE -> {
                val freq = (params["freq"] as? Number)?.toDouble() ?: freqBase
                val detune = (params["detune"] as? Number)?.toDouble() ?: 0.5
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val lfo = 1.0 + detune * Math.sin(2.0 * Math.PI * 0.25 * t)
                    samples[i] = (0.3 * Math.sin(2.0 * Math.PI * freq * lfo * t)
                            + 0.15 * Math.sin(2.0 * Math.PI * freq * 1.5 * t)
                            + 0.1 * Math.sin(2.0 * Math.PI * freq * 2.0 * t)).toFloat()
                }
            }
            ExperimentType.RHYTHM -> {
                val stepsPerBeat = 4
                val totalSteps = (request.duration * request.bpm / 60.0 * stepsPerBeat).toInt()
                val kickFreq = (params["kickFreq"] as? Number)?.toDouble() ?: 60.0
                val hihatDecay = (params["hihatDecay"] as? Number)?.toDouble() ?: 0.05
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val step = ((t * request.bpm / 60.0 * stepsPerBeat) % stepsPerBeat).toInt()
                    val stepT = (t * request.bpm / 60.0 * stepsPerBeat) % 1.0
                    var sample = 0.0
                    // Kick on steps 0, 2
                    if (step == 0 || step == 2) {
                        val env = Math.exp(-stepT * 15.0)
                        sample += 0.5 * env * Math.sin(2.0 * Math.PI * kickFreq * (1.0 + 2.0 * Math.exp(-stepT * 20.0)) * stepT)
                    }
                    // Hihat on every step
                    val hhEnv = Math.exp(-stepT / hihatDecay)
                    sample += 0.2 * hhEnv * (Math.random() * 2.0 - 1.0)
                    samples[i] = sample.toFloat()
                }
            }
            ExperimentType.MELODY -> {
                val scale = (params["scale"] as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toDouble() }
                    ?: listOf(0.0, 2.0, 4.0, 5.0, 7.0, 9.0, 11.0) // major scale in semitones
                val notesPerBeat = (params["notesPerBeat"] as? Number)?.toDouble() ?: 2.0
                val baseFreq = (params["baseFreq"] as? Number)?.toDouble() ?: 261.63 // C4
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val notePos = t * notesPerBeat * request.bpm / 60.0
                    val noteIdx = notePos.toInt() % scale.size
                    val noteT = notePos - notePos.toInt()
                    val semitone = scale[noteIdx]
                    val freq = baseFreq * Math.pow(2.0, semitone / 12.0)
                    val env = Math.exp(-noteT * 3.0) * (1.0 - Math.exp(-noteT * 50.0))
                    samples[i] = (0.3 * env * Math.sin(2.0 * Math.PI * freq * t)).toFloat()
                }
            }
            ExperimentType.AMBIENT -> {
                val baseFreq = (params["baseFreq"] as? Number)?.toDouble() ?: 220.0
                val modRate = (params["modRate"] as? Number)?.toDouble() ?: 0.1
                val modDepth = (params["modDepth"] as? Number)?.toDouble() ?: 20.0
                val filterQ = (params["filterQ"] as? Number)?.toDouble() ?: 5.0
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val slowLfo = Math.sin(2.0 * Math.PI * modRate * t)
                    val freq = baseFreq + modDepth * slowLfo
                    val tremolo = 0.7 + 0.3 * Math.sin(2.0 * Math.PI * 0.3 * t)
                    samples[i] = (0.25 * tremolo * Math.sin(2.0 * Math.PI * freq * t)).toFloat()
                }
            }
            ExperimentType.NOISE -> {
                val density = (params["density"] as? Number)?.toDouble() ?: 0.5
                val filterFreq = (params["filterFreq"] as? Number)?.toDouble() ?: 1000.0
                var prev = 0.0
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val raw = if (Math.random() > density) 0.0 else Math.random() * 2.0 - 1.0
                    // Simple low-pass filter
                    prev = prev + (filterFreq / sampleRate) * (raw - prev)
                    samples[i] = (0.4 * prev).toFloat()
                }
            }
            ExperimentType.GLITCH -> {
                val glitchProb = (params["glitchProb"] as? Number)?.toDouble() ?: 0.05
                val sliceLen = (params["sliceLen"] as? Number)?.toDouble() ?: 0.05
                var phase = 0.0
                var slicePos = 0.0
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val sliceIdx = (t / sliceLen).toInt()
                    if (sliceIdx != (t - 1.0 / sampleRate) / sliceLen).toInt() && sliceIdx > 0) {
                        if (Math.random() < glitchProb) {
                            slicePos = Math.random() * request.duration
                            phase = Math.random() * 1000.0
                        }
                    }
                    phase += 440.0 / sampleRate
                    val base = 0.3 * Math.sin(2.0 * Math.PI * phase)
                    val crush = (Math.floor(base * 4.0) / 4.0)
                    samples[i] = crush.toFloat()
                }
            }
            ExperimentType.GENERATIVE -> {
                // Cellular automaton-inspired generative audio
                val rule = (params["rule"] as? Number)?.toInt()?.and(0xFF) ?: 30
                val cellDuration = (params["cellDuration"] as? Number)?.toDouble() ?: 0.1
                val baseFreq = (params["baseFreq"] as? Number)?.toDouble() ?: 110.0
                val cellsPerSec = (1.0 / cellDuration).toInt()
                var cellState = (Math.random() * 256).toInt()
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val cellIdx = (t * cellsPerSec).toInt()
                    if (cellIdx > (t - 1.0 / sampleRate) * cellsPerSec) {
                        // Advance automaton
                        val newState = StringBuilder()
                        for (bit in 0 until 8) {
                            val left = (cellState ushr ((bit - 1 + 8) % 8)) and 1
                            val center = (cellState ushr bit) and 1
                            val right = (cellState ushr ((bit + 1) % 8)) and 1
                            val idx = (left shl 2) or (center shl 1) or right
                            newState.append((rule ushr idx) and 1)
                        }
                        cellState = newState.toString().toInt(2)
                    }
                    val activeBits = Integer.bitCount(cellState)
                    val freq = baseFreq * (1.0 + activeBits.toDouble() / 8.0)
                    val amp = activeBits.toDouble() / 8.0 * 0.3
                    samples[i] = (amp * Math.sin(2.0 * Math.PI * freq * t)).toFloat()
                }
            }
        }

        // Normalize
        val maxAbs = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        if (maxAbs > 0) {
            for (i in samples.indices) samples[i] = samples[i] / maxAbs * 0.8f
        }

        // Generate waveform as SVG path (simplified)
        val waveformPoints = mutableListOf<String>()
        val step = maxOf(1, numSamples / 200)
        for (i in 0 until 200 step 1) {
            val idx = (i * step).coerceIn(0, numSamples - 1)
            val x = i * 2
            val y = 50 - (samples[idx] * 40)
            waveformPoints.add("$x,$y")
        }
        val waveformSvg = "<svg xmlns='http://www.w3.org/2000/svg' width='400' height='100'><polyline points='${waveformPoints.join(" ")}' fill='none' stroke='#00ff88' stroke-width='1.5'/></svg>"
        val waveformB64 = Base64.getEncoder().encodeToString(waveformSvg.toByteArray())

        // Generate WAV
        val wavBytes = generateWav(samples, sampleRate)
        val wavB64 = Base64.getEncoder().encodeToString(wavBytes)

        return RenderResponse(
            audioDataUrl = "data:audio/wav;base64,$wavB64",
            waveformData = "data:image/svg+xml;base64,$waveformB64",
            sampleRate = sampleRate,
            duration = request.duration,
            type = request.type,
        )
    }

    private fun generateWav(samples: FloatArray, sampleRate: Int): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * blockAlign
        val fileSize = 36 + dataSize

        val out = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(out)

        // RIFF header
        d.writeBytes("RIFF")
        d.writeInt(java.lang.Integer.reverseBytes(fileSize))
        d.writeBytes("WAVE")
        // fmt chunk
        d.writeBytes("fmt ")
        d.writeInt(java.lang.Integer.reverseBytes(16))
        d.writeShort(java.lang.Short.reverseBytes(1.toShort())) // PCM
        d.writeShort(java.lang.Short.reverseBytes(numChannels.toShort()))
        d.writeInt(java.lang.Integer.reverseBytes(sampleRate))
        d.writeInt(java.lang.Integer.reverseBytes(byteRate))
        d.writeShort(java.lang.Short.reverseBytes(blockAlign.toShort()))
        d.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()))
        // data chunk
        d.writeBytes("data")
        d.writeInt(java.lang.Integer.reverseBytes(dataSize))
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            val pcm = (clamped * 32767).toInt().toShort()
            d.writeShort(java.lang.Short.reverseBytes(pcm))
        }

        return out.toByteArray()
    }
}

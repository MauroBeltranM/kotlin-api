package com.mauro.api.service

import com.mauro.api.model.ExperimentType
import kotlin.random.Random
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Pure-math audio synthesis engine. No external audio libraries required.
 *
 * Each synth method fills the [samples] array in-place.
 * After synthesis, call [normalize] then export via [toWavBase64] / [toWaveformSvg].
 */
class AudioEngine(private val sampleRate: Int = SAMPLE_RATE) {

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val TAU = 2.0 * Math.PI
        private const val NORMALIZE_TARGET = 0.8f

        // Harmonic ratios
        private const val FIFTH = 1.5
        private const val OCTAVE = 2.0

        // Waveform SVG
        private const val WAVEFORM_WIDTH = 400
        private const val WAVEFORM_HEIGHT = 100
        private const val WAVEFORM_POINTS = 200
        private const val WAVEFORM_COLOR = "#00ff88"
    }

    // --- Public API ---

    fun synthesize(type: ExperimentType, bpm: Int, durationSec: Float, params: Map<String, Any>): FloatArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(numSamples)
        val ctx = RenderContext(bpm = bpm, durationSec = durationSec, sampleRate = sampleRate)

        when (type) {
            ExperimentType.DRONE -> synthesizeDrone(samples, ctx, params)
            ExperimentType.RHYTHM -> synthesizeRhythm(samples, ctx, params)
            ExperimentType.MELODY -> synthesizeMelody(samples, ctx, params)
            ExperimentType.AMBIENT -> synthesizeAmbient(samples, ctx, params)
            ExperimentType.NOISE -> synthesizeNoise(samples, ctx, params)
            ExperimentType.GLITCH -> synthesizeGlitch(samples, ctx, params)
            ExperimentType.GENERATIVE -> synthesizeGenerative(samples, ctx, params)
        }

        normalize(samples)
        return samples
    }

    fun toWavBase64(samples: FloatArray): String {
        val bytes = generateWavBytes(samples)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun toWaveformSvgBase64(samples: FloatArray): String {
        val svg = buildWaveformSvg(samples)
        return Base64.getEncoder().encodeToString(svg.toByteArray())
    }

    // --- Synthesizers ---

    private fun synthesizeDrone(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val freq = params.double("freq", 110.0)
        val detune = params.double("detune", 0.5)

        for (i in samples.indices) {
            val t = ctx.time(i)
            val lfo = 1.0 + detune * Math.sin(TAU * 0.25 * t)
            samples[i] = (
                0.30 * Math.sin(TAU * freq * lfo * t) +
                0.15 * Math.sin(TAU * freq * FIFTH * t) +
                0.10 * Math.sin(TAU * freq * OCTAVE * t)
                ).toFloat()
        }
    }

    private fun synthesizeRhythm(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val beatDur = ctx.beatDur
        val stepsPerBeat = 4
        val kickFreq = params.double("kickFreq", 60.0)
        val hihatDecay = params.double("hihatDecay", 0.05)

        for (i in samples.indices) {
            val t = ctx.time(i)
            val step = ((t / beatDur * stepsPerBeat) % stepsPerBeat).toInt()
            val stepT = (t / beatDur * stepsPerBeat) % 1.0

            var sample = 0.0

            // Kick on beats 0, 2
            if (step == 0 || step == 2) {
                val kickEnv = Math.exp(-stepT * 15.0)
                val pitchSweep = 1.0 + 2.0 * Math.exp(-stepT * 20.0)
                sample += 0.5 * kickEnv * Math.sin(TAU * kickFreq * pitchSweep * stepT)
            }

            // Hihat on every step
            val hhEnv = Math.exp(-stepT / hihatDecay)
            sample += 0.2 * hhEnv * (rng.nextDouble() * 2.0 - 1.0)

            samples[i] = sample.toFloat()
        }
    }

    private fun synthesizeMelody(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val beatDur = ctx.beatDur
        val scale = params.doubleList("scale", DEFAULT_MAJOR_SCALE)
        val notesPerBeat = params.double("notesPerBeat", 2.0)
        val baseFreq = params.double("baseFreq", 261.63) // C4

        for (i in samples.indices) {
            val t = ctx.time(i)
            val notePos = t / beatDur * notesPerBeat
            val noteIdx = notePos.toInt() % scale.size
            val noteT = notePos - notePos.toInt()

            val semitone = scale[noteIdx]
            val freq = baseFreq * Math.pow(2.0, semitone / 12.0)
            val env = Math.exp(-noteT * 3.0) * (1.0 - Math.exp(-noteT * 50.0))

            samples[i] = (0.3 * env * Math.sin(TAU * freq * t)).toFloat()
        }
    }

    private fun synthesizeAmbient(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val baseFreq = params.double("baseFreq", 220.0)
        val modRate = params.double("modRate", 0.1)
        val modDepth = params.double("modDepth", 20.0)

        for (i in samples.indices) {
            val t = ctx.time(i)
            val slowLfo = Math.sin(TAU * modRate * t)
            val freq = baseFreq + modDepth * slowLfo
            val tremolo = 0.7 + 0.3 * Math.sin(TAU * 0.3 * t)
            samples[i] = (0.25 * tremolo * Math.sin(TAU * freq * t)).toFloat()
        }
    }

    private fun synthesizeNoise(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val density = params.double("density", 0.5)
        val filterFreq = params.double("filterFreq", 1000.0)
        val filterCoeff = filterFreq / sampleRate
        var prev = 0.0

        for (i in samples.indices) {
            val raw = if (rng.nextDouble() > density) 0.0 else rng.nextDouble() * 2.0 - 1.0
            prev += filterCoeff * (raw - prev) // simple one-pole low-pass
            samples[i] = (0.4 * prev).toFloat()
        }
    }

    private fun synthesizeGlitch(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val glitchProb = params.double("glitchProb", 0.05)
        val sliceLen = params.double("sliceLen", 0.05)
        var phase = 0.0
        var prevSliceIdx = -1

        for (i in samples.indices) {
            val t = ctx.time(i)
            val sliceIdx = (t / sliceLen).toInt()

            if (sliceIdx != prevSliceIdx && sliceIdx > 0) {
                if (rng.nextDouble() < glitchProb) {
                    phase = rng.nextDouble() * 1000.0
                }
            }
            prevSliceIdx = sliceIdx

            phase += 440.0 / sampleRate
            val raw = 0.3 * Math.sin(TAU * phase)
            samples[i] = (Math.floor(raw * 4.0) / 4.0).toFloat() // bitcrush
        }
    }

    private fun synthesizeGenerative(samples: FloatArray, ctx: RenderContext, params: Map<String, Any>) {
        val rule = params.int("rule", 30) and 0xFF
        val cellDuration = params.double("cellDuration", 0.1)
        val baseFreq = params.double("baseFreq", 110.0)
        val cellsPerSec = (1.0 / cellDuration).toInt()
        var cellState = (rng.nextDouble() * 256).toInt()
        var prevCellIdx = -1

        for (i in samples.indices) {
            val t = ctx.time(i)
            val cellIdx = (t * cellsPerSec).toInt()

            if (cellIdx != prevCellIdx) {
                cellState = advanceAutomaton(cellState, rule)
                prevCellIdx = cellIdx
            }

            val activeBits = Integer.bitCount(cellState)
            val freq = baseFreq * (1.0 + activeBits.toDouble() / 8.0)
            val amp = activeBits.toDouble() / 8.0 * 0.3
            samples[i] = (amp * Math.sin(TAU * freq * t)).toFloat()
        }
    }

    /** Advance an 8-bit elementary cellular automaton by one step. */
    private fun advanceAutomaton(state: Int, rule: Int): Int {
        var newState = 0
        for (bit in 0 until 8) {
            val left = (state ushr ((bit - 1 + 8) % 8)) and 1
            val center = (state ushr bit) and 1
            val right = (state ushr ((bit + 1) % 8)) and 1
            val neighborhood = (left shl 2) or (center shl 1) or right
            if (((rule ushr neighborhood) and 1) == 1) {
                newState = newState or (1 shl bit)
            }
        }
        return newState
    }

    // --- Post-processing ---

    private fun normalize(samples: FloatArray) {
        val maxAbs = samples.maxOfOrNull { kotlin.math.abs(it) } ?: return
        if (maxAbs <= 0f) return
        for (i in samples.indices) {
            samples[i] = samples[i] / maxAbs * NORMALIZE_TARGET
        }
    }

    // --- Export ---

    private fun generateWavBytes(samples: FloatArray): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val blockAlign = numChannels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val dataSize = samples.size * blockAlign
        val fileSize = 36 + dataSize

        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            // RIFF header
            d.writeBytes("RIFF")
            d.writeIntLe(fileSize)
            d.writeBytes("WAVE")
            // fmt sub-chunk
            d.writeBytes("fmt ")
            d.writeIntLe(16)
            d.writeShortLe(1) // PCM
            d.writeShortLe(numChannels)
            d.writeIntLe(sampleRate)
            d.writeIntLe(byteRate)
            d.writeShortLe(blockAlign)
            d.writeShortLe(bitsPerSample)
            // data sub-chunk
            d.writeBytes("data")
            d.writeIntLe(dataSize)
            for (s in samples) {
                val pcm = (s.coerceIn(-1f, 1f) * 32767).toInt()
                d.writeShortLe(pcm)
            }
        }

        return out.toByteArray()
    }

    private fun buildWaveformSvg(samples: FloatArray): String {
        val points = samples.resample(WAVEFORM_POINTS).mapIndexed { i, s ->
            val x = (i * WAVEFORM_WIDTH / WAVEFORM_POINTS)
            val y = (WAVEFORM_HEIGHT / 2 - s * WAVEFORM_HEIGHT * 0.4)
            "$x,$y"
        }.joinToString(" ")
        return "<svg xmlns='http://www.w3.org/2000/svg' width='$WAVEFORM_WIDTH' height='$WAVEFORM_HEIGHT'>" +
            "<polyline points='$points' fill='none' stroke='$WAVEFORM_COLOR' stroke-width='1.5'/></svg>"
    }

    // --- Helpers ---

    private fun FloatArray.resample(targetCount: Int): FloatArray {
        val step = maxOf(1, size / targetCount)
        return FloatArray(targetCount) { i ->
            this[(i * step).coerceIn(0, size - 1)]
        }
    }

    private fun Map<String, Any>.double(key: String, default: Double): Double =
        (this[key] as? Number)?.toDouble() ?: default

    private fun Map<String, Any>.int(key: String, default: Int): Int =
        (this[key] as? Number)?.toInt() ?: default

    private fun Map<String, Any>.doubleList(key: String, default: List<Double>): List<Double> =
        (this[key] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toDouble() }
            ?: default

    /** Render context to avoid recalculating constants. */
    private data class RenderContext(val bpm: Int, val durationSec: Float, val sampleRate: Int) {
        fun time(sampleIndex: Int): Double = sampleIndex.toDouble() / sampleRate
        val beatDur: Double get() = 60.0 / bpm
    }

    companion object {
        val DEFAULT_MAJOR_SCALE = listOf(0.0, 2.0, 4.0, 5.0, 7.0, 9.0, 11.0)
    }
}

// --- Little-endian extension for DataOutputStream ---

private fun DataOutputStream.writeIntLe(value: Int) = writeInt(Integer.reverseBytes(value))

private fun DataOutputStream.writeShortLe(value: Int) {
    val s = value.toShort()
    val reversed = java.lang.Short.reverseBytes(s)
    write(reversed.toInt() and 0xFF)
    write((reversed.toInt() shr 8) and 0xFF)
}

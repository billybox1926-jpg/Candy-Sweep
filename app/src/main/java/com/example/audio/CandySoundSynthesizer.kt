package com.example.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Distinct sound effect clips synthesized as 16-bit PCM WAV audio for Candy Sweep.
 */
enum class SoundClip(val fileName: String) {
    SWAP_SUCCESS("sfx_swap_success.wav"),
    SWAP_INVALID("sfx_swap_invalid.wav"),
    CANDY_MATCH("sfx_candy_match.wav"),
    SPECIAL_MATCH("sfx_special_match.wav"),
    LOW_MOVES("sfx_low_moves.wav"),
    GAME_OVER("sfx_game_over.wav"),
    VICTORY("sfx_victory.wav"),
    SHUFFLE("sfx_shuffle.wav")
}

/**
 * High-level semantic audio events dispatched by gameplay state transitions.
 */
sealed interface SoundEvent {
    data object SwapSuccess : SoundEvent
    data object SwapInvalid : SoundEvent
    data class CandyMatch(val comboLevel: Int = 1, val isSpecial: Boolean = false) : SoundEvent
    data object LowMovesWarning : SoundEvent
    data object GameOver : SoundEvent
    data object Victory : SoundEvent
    data object Shuffle : SoundEvent
}

/**
 * Pure Kotlin synthesizer that builds warm, musical 16-bit PCM RIFF WAV clips for game audio.
 * Produces lightweight (~8–32 KB) polyphony-friendly samples for Android SoundPool playback.
 */
object CandySoundSynthesizer {
    const val SAMPLE_RATE = 22050
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1

    /**
     * Pentatonic pitch multipliers for rising cascade combos, clamped to SoundPool's [0.5f, 2.0f] range.
     */
    fun comboPlaybackRate(comboLevel: Int): Float {
        val rates = floatArrayOf(1.0f, 1.122f, 1.260f, 1.498f, 1.682f, 1.888f, 2.0f)
        val index = (comboLevel - 1).coerceIn(0, rates.lastIndex)
        return rates[index]
    }

    fun synthesizeWav(clip: SoundClip): ByteArray {
        val samples = when (clip) {
            SoundClip.SWAP_SUCCESS -> generateSwapSuccess()
            SoundClip.SWAP_INVALID -> generateSwapInvalid()
            SoundClip.CANDY_MATCH -> generateCandyMatch()
            SoundClip.SPECIAL_MATCH -> generateSpecialMatch()
            SoundClip.LOW_MOVES -> generateLowMoves()
            SoundClip.GAME_OVER -> generateGameOver()
            SoundClip.VICTORY -> generateVictory()
            SoundClip.SHUFFLE -> generateShuffle()
        }
        return encodePcm16Wav(samples, SAMPLE_RATE)
    }

    /** Crisp upward silky marimba glide when a valid swap connects. */
    private fun generateSwapSuccess(): ShortArray {
        val durationSec = 0.12
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / total
            val freq = 380.0 + 240.0 * progress * progress
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = envelope(t, attack = 0.008, decay = 0.055)
            val tone = sin(phase) * 0.78 + sin(phase * 2.0) * 0.22
            out[i] = (tone * env * 0.65).toFloat()
        }
        return floatToPcm16(out)
    }

    /** Soft playful low bounce-back thump for invalid swaps. */
    private fun generateSwapInvalid(): ShortArray {
        val durationSec = 0.16
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        mixNote(out, startSec = 0.0, durationSec = 0.075, freqHz = 250.0, endFreqHz = 195.0, gain = 0.55)
        mixNote(out, startSec = 0.075, durationSec = 0.085, freqHz = 190.0, endFreqHz = 150.0, gain = 0.50)
        return floatToPcm16(out)
    }

    /** Bright bubbly candy-pop bell chime for standard matches (pitch-shifted per combo). */
    private fun generateCandyMatch(): ShortArray {
        val durationSec = 0.24
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        // Quick bubble pop transient + sparkling C5 bell overtones
        mixNote(out, startSec = 0.0, durationSec = 0.045, freqHz = 360.0, endFreqHz = 780.0, gain = 0.35)
        mixBellNote(out, startSec = 0.012, durationSec = 0.22, freqHz = 523.25, gain = 0.68)
        mixBellNote(out, startSec = 0.035, durationSec = 0.18, freqHz = 783.99, gain = 0.32)
        return floatToPcm16(out)
    }

    /** Shimmering ascending major-triad sparkle burst for special candies & frosting clears. */
    private fun generateSpecialMatch(): ShortArray {
        val durationSec = 0.38
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        notes.forEachIndexed { idx, freq ->
            mixBellNote(
                buffer = out,
                startSec = idx * 0.045,
                durationSec = 0.24,
                freqHz = freq,
                gain = 0.45
            )
        }
        return floatToPcm16(out)
    }

    /** Crisp gentle double-ping warning when running low on moves. */
    private fun generateLowMoves(): ShortArray {
        val durationSec = 0.24
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        mixBellNote(out, startSec = 0.0, durationSec = 0.10, freqHz = 659.25, gain = 0.48)
        mixBellNote(out, startSec = 0.10, durationSec = 0.13, freqHz = 880.0, gain = 0.56)
        return floatToPcm16(out)
    }

    /** Expressive descending 4-note cadence when the player runs out of moves (Game Over). */
    private fun generateGameOver(): ShortArray {
        val durationSec = 0.78
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        // G4 -> F4 -> Eb4 -> C4 (warm melancholic arcade marimba descent)
        val cadence = listOf(
            Triple(0.00, 0.18, 392.00),
            Triple(0.15, 0.18, 349.23),
            Triple(0.30, 0.18, 311.13),
            Triple(0.45, 0.33, 261.63)
        )
        for ((start, dur, freq) in cadence) {
            mixWarmMarimba(out, startSec = start, durationSec = dur, freqHz = freq, gain = 0.58)
        }
        return floatToPcm16(out)
    }

    /** Celebratory ascending major fanfare when clearing a stage or completing Score Rush. */
    private fun generateVictory(): ShortArray {
        val durationSec = 0.80
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        // C5 -> E5 -> G5 -> C6 + warm G5 harmony on final beat
        mixBellNote(out, startSec = 0.00, durationSec = 0.18, freqHz = 523.25, gain = 0.48)
        mixBellNote(out, startSec = 0.11, durationSec = 0.18, freqHz = 659.25, gain = 0.50)
        mixBellNote(out, startSec = 0.22, durationSec = 0.20, freqHz = 783.99, gain = 0.52)
        mixBellNote(out, startSec = 0.34, durationSec = 0.44, freqHz = 1046.50, gain = 0.56)
        mixBellNote(out, startSec = 0.34, durationSec = 0.44, freqHz = 783.99, gain = 0.30)
        mixBellNote(out, startSec = 0.34, durationSec = 0.44, freqHz = 523.25, gain = 0.26)
        return floatToPcm16(out)
    }

    /** Quick playful fluttering chime for board shuffles. */
    private fun generateShuffle(): ShortArray {
        val durationSec = 0.26
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(total)
        val notes = doubleArrayOf(440.0, 523.25, 587.33, 659.25, 783.99)
        notes.forEachIndexed { idx, freq ->
            mixNote(
                buffer = out,
                startSec = idx * 0.036,
                durationSec = 0.09,
                freqHz = freq,
                endFreqHz = freq * 1.04,
                gain = 0.36
            )
        }
        return floatToPcm16(out)
    }

    private fun mixNote(
        buffer: FloatArray,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        endFreqHz: Double,
        gain: Double
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt().coerceAtLeast(0)
        val len = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until len) {
            val idx = startIdx + i
            if (idx >= buffer.size) break
            val t = i.toDouble() / SAMPLE_RATE
            val frac = i.toDouble() / len
            val freq = freqHz + (endFreqHz - freqHz) * frac
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = envelope(t, attack = 0.006, decay = durationSec * 0.48)
            buffer[idx] += (sin(phase) * env * gain).toFloat()
        }
    }

    private fun mixBellNote(
        buffer: FloatArray,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        gain: Double
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt().coerceAtLeast(0)
        val len = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (i in 0 until len) {
            val idx = startIdx + i
            if (idx >= buffer.size) break
            val t = i.toDouble() / SAMPLE_RATE
            val baseEnv = envelope(t, attack = 0.005, decay = durationSec * 0.38)
            val overtoneEnv = envelope(t, attack = 0.004, decay = durationSec * 0.20)
            val wave = sin(2.0 * PI * freqHz * t) * 0.72 * baseEnv +
                sin(2.0 * PI * (freqHz * 2.0) * t) * 0.22 * overtoneEnv +
                sin(2.0 * PI * (freqHz * 3.0) * t) * 0.08 * overtoneEnv
            buffer[idx] += (wave * gain).toFloat()
        }
    }

    private fun mixWarmMarimba(
        buffer: FloatArray,
        startSec: Double,
        durationSec: Double,
        freqHz: Double,
        gain: Double
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt().coerceAtLeast(0)
        val len = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        for (i in 0 until len) {
            val idx = startIdx + i
            if (idx >= buffer.size) break
            val t = i.toDouble() / SAMPLE_RATE
            val env = envelope(t, attack = 0.008, decay = durationSec * 0.45)
            val strike = envelope(t, attack = 0.003, decay = 0.03)
            val wave = sin(2.0 * PI * freqHz * t) * 0.80 * env +
                sin(2.0 * PI * (freqHz * 4.0) * t) * 0.14 * strike +
                sin(2.0 * PI * (freqHz * 2.0) * t) * 0.16 * env
            buffer[idx] += (wave * gain).toFloat()
        }
    }

    private fun envelope(t: Double, attack: Double, decay: Double): Double {
        val attackFactor = if (t < attack) t / attack else 1.0
        val decayFactor = exp(-t / decay.coerceAtLeast(0.01))
        return attackFactor * decayFactor
    }

    private fun floatToPcm16(samples: FloatArray): ShortArray {
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-0.98f, 0.98f)
            pcm[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    private fun encodePcm16Wav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)
        val out = ByteArrayOutputStream(44 + dataSize)

        // RIFF header
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt subchunk
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16) // PCM chunk size
        writeShortLE(out, 1) // AudioFormat = 1 (PCM)
        writeShortLE(out, CHANNELS)
        writeIntLE(out, sampleRate)
        writeIntLE(out, byteRate)
        writeShortLE(out, blockAlign)
        writeShortLE(out, BITS_PER_SAMPLE)

        // data subchunk
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, dataSize)
        for (sample in samples) {
            writeShortLE(out, sample.toInt())
        }
        return out.toByteArray()
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}

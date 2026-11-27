package com.example

import com.example.audio.CandySoundSynthesizer
import com.example.audio.SoundClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandySoundSynthesizerTest {

    @Test
    fun allClipsProduceValidPcm16WavHeaders() {
        for (clip in SoundClip.entries) {
            val wav = CandySoundSynthesizer.synthesizeWav(clip)
            assertTrue("WAV too short for $clip", wav.size > 44)
            assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

            val audioFormat = (wav[20].toInt() and 0xFF) or ((wav[21].toInt() and 0xFF) shl 8)
            val channels = (wav[22].toInt() and 0xFF) or ((wav[23].toInt() and 0xFF) shl 8)
            val bitsPerSample = (wav[34].toInt() and 0xFF) or ((wav[35].toInt() and 0xFF) shl 8)
            assertEquals(1, audioFormat)
            assertEquals(1, channels)
            assertEquals(16, bitsPerSample)
        }
    }

    @Test
    fun swapMatchAndGameOverClipsAreDistinctWaveforms() {
        val swap = CandySoundSynthesizer.synthesizeWav(SoundClip.SWAP_SUCCESS)
        val match = CandySoundSynthesizer.synthesizeWav(SoundClip.CANDY_MATCH)
        val gameOver = CandySoundSynthesizer.synthesizeWav(SoundClip.GAME_OVER)

        assertFalse(swap.contentEquals(match))
        assertFalse(match.contentEquals(gameOver))
        assertFalse(swap.contentEquals(gameOver))
        // Game-over cadence is noticeably longer than quick swap chime
        assertTrue(gameOver.size > swap.size * 3)
    }

    @Test
    fun comboPlaybackRateRisesMonotonicallyWithinSoundPoolBounds() {
        var previous = 0f
        for (combo in 1..8) {
            val rate = CandySoundSynthesizer.comboPlaybackRate(combo)
            assertTrue("Rate out of SoundPool bounds: $rate", rate in 0.5f..2.0f)
            assertTrue("Rate should not decrease on higher combos", rate >= previous)
            previous = rate
        }
        assertEquals(1.0f, CandySoundSynthesizer.comboPlaybackRate(1), 0.001f)
        assertTrue(CandySoundSynthesizer.comboPlaybackRate(3) > CandySoundSynthesizer.comboPlaybackRate(1))
    }
}

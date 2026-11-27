package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Contract for game sound playback so audio interactions remain decoupled and testable.
 */
interface SoundController {
    val soundEnabled: StateFlow<Boolean>
    fun setSoundEnabled(enabled: Boolean)
    fun play(event: SoundEvent)
    fun playSwapSuccess()
    fun playSwapInvalid()
    fun playCandyMatch(comboLevel: Int = 1, isSpecial: Boolean = false)
    fun playLowMovesWarning()
    fun playGameOver()
    fun playVictory()
    fun playShuffle()
    fun release()
}

/**
 * Low-latency game audio manager backed by Android SoundPool.
 * Synthesizes and caches lightweight WAV clips on an I/O coroutine, supports polyphonic
 * playback with rising pentatonic pitch scaling for match combos, and persists mute state.
 */
class CandySoundManager(context: Context) : SoundController {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ENABLED, true))
    override val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val released = AtomicBoolean(false)
    private val sampleIds = ConcurrentHashMap<SoundClip, Int>()
    private val loadedSampleIds = ConcurrentHashMap.newKeySet<Int>()

    private val soundPool: SoundPool? = runCatching {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attrs)
            .build()
            .apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        loadedSampleIds.add(sampleId)
                    }
                }
            }
    }.onFailure { err ->
        Log.w(TAG, "SoundPool unavailable on this device", err)
    }.getOrNull()

    init {
        prepareAudioClipsAsync()
    }

    private fun prepareAudioClipsAsync() {
        val pool = soundPool ?: return
        scope.launch {
            runCatching {
                val dir = File(appContext.cacheDir, SFX_CACHE_DIR).apply { mkdirs() }
                for (clip in SoundClip.entries) {
                    if (released.get()) return@launch
                    val wavFile = File(dir, clip.fileName)
                    if (!wavFile.exists() || wavFile.length() < MIN_VALID_WAV_BYTES) {
                        wavFile.writeBytes(CandySoundSynthesizer.synthesizeWav(clip))
                    }
                    if (!released.get()) {
                        val id = pool.load(wavFile.absolutePath, 1)
                        if (id != 0) {
                            sampleIds[clip] = id
                        }
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "Failed preparing sound clips", err)
            }
        }
    }

    override fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        if (!enabled) {
            runCatching { soundPool?.autoPause() }
        } else {
            runCatching { soundPool?.autoResume() }
        }
    }

    override fun play(event: SoundEvent) {
        when (event) {
            SoundEvent.SwapSuccess -> playSwapSuccess()
            SoundEvent.SwapInvalid -> playSwapInvalid()
            is SoundEvent.CandyMatch -> playCandyMatch(event.comboLevel, event.isSpecial)
            SoundEvent.LowMovesWarning -> playLowMovesWarning()
            SoundEvent.GameOver -> playGameOver()
            SoundEvent.Victory -> playVictory()
            SoundEvent.Shuffle -> playShuffle()
        }
    }

    override fun playSwapSuccess() {
        playClip(clip = SoundClip.SWAP_SUCCESS, volume = 0.72f, rate = 1.0f)
    }

    override fun playSwapInvalid() {
        playClip(clip = SoundClip.SWAP_INVALID, volume = 0.60f, rate = 1.0f)
    }

    override fun playCandyMatch(comboLevel: Int, isSpecial: Boolean) {
        val pitchRate = CandySoundSynthesizer.comboPlaybackRate(comboLevel)
        if (isSpecial) {
            playClip(clip = SoundClip.SPECIAL_MATCH, volume = 0.85f, rate = pitchRate.coerceAtMost(1.5f))
        } else {
            playClip(clip = SoundClip.CANDY_MATCH, volume = 0.82f, rate = pitchRate)
        }
    }

    override fun playLowMovesWarning() {
        playClip(clip = SoundClip.LOW_MOVES, volume = 0.68f, rate = 1.0f)
    }

    override fun playGameOver() {
        playClip(clip = SoundClip.GAME_OVER, volume = 0.90f, rate = 1.0f)
    }

    override fun playVictory() {
        playClip(clip = SoundClip.VICTORY, volume = 0.92f, rate = 1.0f)
    }

    override fun playShuffle() {
        playClip(clip = SoundClip.SHUFFLE, volume = 0.65f, rate = 1.0f)
    }

    private fun playClip(clip: SoundClip, volume: Float, rate: Float) {
        if (released.get() || !_soundEnabled.value) return
        val pool = soundPool ?: return
        val sampleId = sampleIds[clip] ?: return
        if (sampleId !in loadedSampleIds) return

        val clampedVolume = volume.coerceIn(0f, 1f)
        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        runCatching {
            pool.play(sampleId, clampedVolume, clampedVolume, 1, 0, clampedRate)
        }.onFailure { err ->
            Log.w(TAG, "Error playing sound clip $clip", err)
        }
    }

    override fun release() {
        if (released.compareAndSet(false, true)) {
            scope.cancel()
            loadedSampleIds.clear()
            sampleIds.clear()
            runCatching { soundPool?.release() }
        }
    }

    private companion object {
        const val TAG = "CandySoundManager"
        const val PREFS_NAME = "candy_sweep_audio_prefs"
        const val KEY_SOUND_ENABLED = "sound_effects_enabled"
        const val SFX_CACHE_DIR = "candy_sfx_v1"
        const val MAX_STREAMS = 6
        const val MIN_VALID_WAV_BYTES = 100L
    }
}

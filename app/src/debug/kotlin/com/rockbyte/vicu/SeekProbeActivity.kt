package com.rockbyte.vicu

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.rockbyte.vicu.player.PlayerEvent
import com.rockbyte.vicu.player.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Opt-in, debug-only native playback regression; no work during normal app playback. */
class SeekProbeActivity : Activity(), SurfaceHolder.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: PlayerRepository
    private var started = false
    private var failed = false
    private var position = -1L
    private var updated = 0L
    private var target = -1L
    private var requested = 0L
    private var arrived = false
    private var duration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PlayerRepository(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scope.launch {
            repo.events.collect { event ->
                when (event) {
                    is PlayerEvent.Position -> {
                        position = event.positionMs
                        updated = SystemClock.elapsedRealtime()
                        if (target >= 0 && !arrived && position in target..target + 1000) {
                            arrived = true
                            Log.i(TAG, "ARRIVED target=$target position=$position latencyMs=${updated - requested}")
                        }
                    }
                    is PlayerEvent.Prepared -> duration = event.durationMs
                    is PlayerEvent.Failed -> fail("playback=${event.error}")
                    PlayerEvent.Ended -> fail("unexpected end position=$position")
                }
            }
        }
        setContentView(SurfaceView(this).also { it.holder.addCallback(this) })
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        repo.setSurface(holder.surface)
        if (started) return
        started = true
        scope.launch {
            try {
                val uri = requireNotNull(intent.data) { "Supply the readable media content URI with -d" }
                require(uri.scheme == "content") { "Only content URIs are supported" }
                val gap = intent.getLongExtra("gap", 2000L)
                val repeats = intent.getIntExtra("repeats", 1)
                val raw = intent.getStringExtra("targets") ?: "5000,10000,20000,10000,5000,20000,5000,10000,20000,5000"
                require(raw.length <= 1024 && gap in 0..10000 && repeats in 1..10)
                val targets = raw.split(',').map { it.trim().toLong() }
                require(targets.size in 1..100 && targets.all { it in 0..86_400_000 })
                require(targets.size.toLong() * repeats * gap <= 300_000) { "Run exceeds five minutes" }
                Log.i(TAG, "START uri=$uri gap=$gap repeats=$repeats targets=$targets")
                repo.open(uri)
                delay(2000)
                check(!failed && updated > 0) { "Playback did not start" }
                require(targets.max() + 10_000 < duration) { "Keep targets at least ten seconds before EOF" }
                repeat(repeats) { round ->
                    for ((index, next) in targets.withIndex()) {
                        target = next
                        arrived = false
                        requested = SystemClock.elapsedRealtime()
                        Log.i(TAG, "REQUEST round=$round n=$index target=$target before=$position")
                        repo.seekTo(target)
                        delay(gap)
                        val age = SystemClock.elapsedRealtime() - updated
                        Log.i(TAG, "SAMPLE target=$target arrived=$arrived position=$position ageMs=$age")
                        // Bursts may supersede a seek before its target is decoded; assert final recovery instead.
                        if (gap >= 1500 && (!arrived || age > 1500)) fail("seek target=$target arrived=$arrived ageMs=$age")
                    }
                }
                var previous = position
                repeat(6) { second ->
                    delay(1000)
                    val age = SystemClock.elapsedRealtime() - updated
                    Log.i(TAG, "RECOVERY second=$second target=$target arrived=$arrived position=$position ageMs=$age")
                    if (second >= 2 && (!arrived || age > 1500 || position <= previous)) {
                        fail("recovery did not advance: previous=$previous position=$position ageMs=$age")
                    }
                    previous = position
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                fail("${error.javaClass.simpleName}: ${error.message}")
            }
            Log.i(TAG, "VERDICT=${if (failed) "FAIL" else "PASS"} final=$position")
            repo.pause()
        }
    }

    private fun fail(reason: String) {
        failed = true
        Log.e(TAG, "FAIL $reason")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { repo.setSurface(null) }

    override fun onDestroy() {
        scope.cancel()
        repo.release()
        super.onDestroy()
    }

    private companion object { const val TAG = "SeekProbe" }
}

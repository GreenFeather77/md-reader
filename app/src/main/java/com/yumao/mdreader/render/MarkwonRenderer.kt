package com.yumao.mdreader.render

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.noties.markwon.Markwon
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared no-coroutine markdown render pipeline.
 *
 * kotlinx-coroutines resumption between Dispatchers.Default and Compose was
 * silently lost under both debug and release (stuck renders), so rendering runs
 * on a single daemon executor thread and results are posted back with a main
 * thread Handler, guarded by a 20s watchdog with 3 attempts and a null-result
 * fallback (callers render plain fallback on null).
 *
 * Each instance sequences its own submissions; [cancel] invalidates only that
 * instance's in-flight work, so reader / editor preview / compare panes can
 * share the one executor thread without interfering.
 */
class MarkwonRenderer {

    private val seq = AtomicLong(0)

    fun nextSeq(): Long = seq.incrementAndGet()

    fun isCurrent(s: Long): Boolean = seq.get() == s

    /** Invalidates all in-flight submissions of this renderer instance. */
    fun cancel() {
        seq.incrementAndGet()
    }

    fun submit(
        seq: Long,
        markwon: Markwon,
        content: String,
        attempt: Int,
        done: AtomicBoolean,
        onDone: (CharSequence?) -> Unit,
    ) {
        Pool.executor.execute {
            var out: CharSequence? = null
            try {
                out = markwon.toMarkdown(content)
            } catch (t: Throwable) {
                Log.w("MDReader", "markdown render failed (attempt=$attempt)", t)
            }
            Pool.handler.post {
                if (done.compareAndSet(false, true) && this.seq.get() == seq) onDone(out)
            }
        }
        Pool.handler.postDelayed({
            if (done.get() || this.seq.get() != seq) return@postDelayed
            Log.w("MDReader", "render watchdog fired (seq=$seq attempt=$attempt)")
            if (attempt < 3) {
                submit(seq, markwon, content, attempt + 1, done, onDone)
            } else {
                Pool.handler.post {
                    if (done.compareAndSet(false, true) && this.seq.get() == seq) onDone(null)
                }
            }
        }, RENDER_TIMEOUT_MS)
    }

    companion object {
        private const val RENDER_TIMEOUT_MS = 20_000L
    }

    private object Pool {
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "md-render").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        }
        val handler = Handler(Looper.getMainLooper())
    }
}

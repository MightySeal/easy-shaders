package io.easyshaders.lib.processing.concurrent

import android.os.Handler
import android.os.HandlerThread
import io.easyshaders.lib.processing.utils.TAG
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class EffectHandlerExecutorService private constructor(
    private val glThread: HandlerThread,
    override val handler: Handler
): AbstractEffectHandlerExecutorService() {

    override fun execute(command: Runnable) {
        if (!handler.post(command)) {
            throw RejectedExecutionException("$handler is shutting down")
        }
    }

    // TODO: make proper implementation?
    override fun shutdown() {
        throw UnsupportedOperationException("Use Looper.quitSafely().")
    }

    override fun shutdownNow(): List<Runnable>? {
        throw UnsupportedOperationException("Use Looper.quitSafely().")
    }

    override fun isShutdown(): Boolean {
        return false
    }

    override fun isTerminated(): Boolean {
        return false
    }

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit?
    ): Boolean {
        throw UnsupportedOperationException("Use Looper.quitSafely().")
    }

    fun quitThread() {
        glThread.quit()
    }

    companion object Factory {
        private val instance by lazy {
            val glThread: HandlerThread = HandlerThread(TAG + "GL Thread").also(HandlerThread::start)
            val handler = Handler(glThread.looper)
            EffectHandlerExecutorService(glThread, handler)
        }

        fun instance(): EffectHandlerExecutorService = instance
    }
}
package io.easyshaders.lib.processing.concurrent

import android.os.Handler
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

object LibExecutors {

    fun newHandlerExecutor(handler: Handler): ExecutorService {
        return HandlerExecutorService(handler)
    }

}

class HandlerExecutorService(private val handler: Handler): AbstractExecutorService() {

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
}
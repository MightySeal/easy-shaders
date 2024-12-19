package io.easyshaders.lib.processing

import android.os.Handler
import android.os.Looper
import io.easyshaders.lib.processing.concurrent.EffectHandlerExecutorService

object EasyShaders {

    fun init(handler: Handler? = null, onComplete: () -> Unit) {
        val resultHandler = handler ?: Looper.myLooper()?.let(::Handler)

        val executor = EffectHandlerExecutorService.instance()
        executor.execute {

            // TODO: init here
            // val openGlEnvironment = OpenglEnvironment()
            // openGlEnvironment.init()

            try {
                if (resultHandler != null) {
                    resultHandler.post {
                        onComplete()
                    }
                } else {
                    onComplete()
                }
            } catch (e: Exception) {
                // TODO: handle exception
            }
        }
    }
}
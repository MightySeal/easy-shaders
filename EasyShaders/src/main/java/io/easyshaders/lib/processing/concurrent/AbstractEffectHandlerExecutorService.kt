package io.easyshaders.lib.processing.concurrent

import android.os.Handler
import java.util.concurrent.AbstractExecutorService

abstract class AbstractEffectHandlerExecutorService: AbstractExecutorService() {
    abstract val handler: Handler
}
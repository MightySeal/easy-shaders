package io.shaderfoundry.lib.concurrent

import android.os.Handler
import java.util.concurrent.AbstractExecutorService

internal abstract class AbstractEffectHandlerExecutorService: AbstractExecutorService() {
    abstract val handler: Handler
}
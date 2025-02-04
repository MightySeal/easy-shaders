package io.shaderfoundry.lib.util

internal val Any.TAG: String
    get() = this::class.java.simpleName

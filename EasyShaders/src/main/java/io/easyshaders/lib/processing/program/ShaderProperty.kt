package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


// TODO: https://github.com/Kotlin/KEEP/blob/context-parameters/proposals/context-parameters.md
//  Consider using it since kotlin 2.2.x
//  https://youtrack.jetbrains.com/issue/KT-67119/Migration-warning-from-context-receivers-to-context-parameters


interface ShaderProperty<T : Number> {
    var value: T
    fun isInitialized(): Boolean
}


internal abstract class LazyShaderProperty<T : Number>(
    private val propertyName: String,
    private val shaderProgramIdProp: FragmentShaderProgramIdProperty,
    private val type: ShaderPropertyType,
) : ShaderProperty<T> {

    internal var isInitialized = false
    internal val location: Int by lazy(LazyThreadSafetyMode.NONE) {
        when (type) {
            ShaderPropertyType.ATTRIBUTE -> GLES31.glGetAttribLocation(
                shaderProgramIdProp.shaderProgramId.handle,
                propertyName
            )

            ShaderPropertyType.UNIFORM -> GLES31.glGetUniformLocation(
                shaderProgramIdProp.shaderProgramId.handle,
                propertyName
            )
        }.also {
            isInitialized = true
            flush(shaderProgramIdProp.shaderProgramId, it, value)
        }
    }

    internal fun flush() {
        flush(shaderProgramIdProp.shaderProgramId, location, value)
    }

    internal abstract fun flush(programId: FragmentShaderProgramId, location: Int, newValue: T)
    override fun isInitialized(): Boolean = isInitialized
}

internal class IntLazyShaderProperty(
    private val propertyName: String,
    private val shaderProgramIdProp: FragmentShaderProgramIdProperty,
    private val type: ShaderPropertyType,
    private val default: Int? = null
) : LazyShaderProperty<Int>(
    propertyName,
    shaderProgramIdProp,
    type,
) {
    private val valueHolder = AtomicInteger(default ?: 0)
    private val isDirty = AtomicBoolean(default != null)

    override var value: Int
        get() = valueHolder.get()
        set(value) {
            valueHolder.set(value)
            isDirty.set(true)
        }

    override fun flush(programId: FragmentShaderProgramId, location: Int, newValue: Int) {
        if (isDirty.getAndSet(true)) {
            GLES31.glProgramUniform1i(programId.handle, location, newValue)
        }
    }

    /*fun a() {
        GLES31.getActiveUniform
    }*/
}

internal class FloatLazyShaderProperty(
    private val propertyName: String,
    private val shaderProgramIdProp: FragmentShaderProgramIdProperty,
    private val type: ShaderPropertyType,
    private val default: Float? = null
) : LazyShaderProperty<Float>(
    propertyName,
    shaderProgramIdProp,
    type,
) {

    private val valueHolder = default?.asAtomic() ?: AtomicInteger(0)
    private val isDirty = AtomicBoolean(default != null)

    override var value: Float
        get() = valueHolder.getAsFloat()
        set(value) {
            valueHolder.setAsFloat(value)
            isDirty.set(true)
        }

    override fun flush(programId: FragmentShaderProgramId, location: Int, newValue: Float) {
        if (isDirty.getAndSet(false)) {
            GLES31.glProgramUniform1f(programId.handle, location, newValue)
        }
    }
}

private fun AtomicInteger.getAsFloat(): Float = java.lang.Float.intBitsToFloat(this.get())
private fun AtomicInteger.setAsFloat(value: Float) = this.set(java.lang.Float.floatToIntBits(value))

private fun Float.asAtomic(): AtomicInteger = AtomicInteger(java.lang.Float.floatToIntBits(this))
internal enum class ShaderPropertyType { ATTRIBUTE, UNIFORM }
package io.easyshaders.lib.processing.program

import android.opengl.GLES31
import kotlin.reflect.KProperty


// TODO: https://github.com/Kotlin/KEEP/blob/context-parameters/proposals/context-parameters.md
//  Consider using it since kotlin 2.2.x
//  https://youtrack.jetbrains.com/issue/KT-67119/Migration-warning-from-context-receivers-to-context-parameters

fun uniformProperty(
    name: String,
    shaderProgramId: ShaderProgramId
): ShaderProperty<Int> = IntegerPropertyImpl(name, shaderProgramId, ShaderPropertyType.UNIFORM)

interface ShaderProperty<T> {
    val value: Int
    fun isInitialized(): Boolean
    operator fun getValue(thisRef: Any, property: KProperty<*>): T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

internal class IntegerPropertyImpl(
    private val propertyName: String,
    private val shaderProgramId: ShaderProgramId,
    private val type: ShaderPropertyType,
): ShaderProperty<Int> {
    private var _value: Int? = null

    override val value: Int
        get() = _value ?: when(type) {
            ShaderPropertyType.ATTRIBUTE -> GLES31.glGetAttribLocation(shaderProgramId.handle, propertyName)
            ShaderPropertyType.UNIFORM -> GLES31.glGetUniformLocation(shaderProgramId.handle, propertyName)
        }

    override fun isInitialized(): Boolean = _value !== null

    override operator fun getValue(thisRef: Any, property: KProperty<*>): Int = value
    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {

    }
}

enum class ShaderPropertyType { ATTRIBUTE, UNIFORM }
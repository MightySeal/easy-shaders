package io.easyshaders.lib.processing.program

import android.opengl.GLES32
import kotlin.reflect.KProperty


inline operator fun <T: ShaderProperty> T.getValue(thisRef: Any, property: KProperty<*>): Int = value

// TODO: https://github.com/Kotlin/KEEP/blob/context-parameters/proposals/context-parameters.md
//  Consider using it since kotlin 2.2.x
//  https://youtrack.jetbrains.com/issue/KT-67119/Migration-warning-from-context-receivers-to-context-parameters

interface ShaderProperty {
    val value: Int
    fun isInitialized(): Boolean
}

// TODO: use special types for vertex and fragment shader, instead of a whole program
internal class ShaderPropertyLocation constructor(
    private val program: ProgramId,
    private val name: String,
    private val type: ShaderPropertyType,
) : ShaderProperty {
    // private var initializer: (() -> Int)? = initializer
    private var _value: Int? = null

    override val value: Int
        get() {
            if (_value === null) {
                _value = when (type) {
                    ShaderPropertyType.ATTRIBUTE -> GLES32.glGetAttribLocation(program.programHandle, name)
                    ShaderPropertyType.UNIFORM -> GLES32.glGetUniformLocation(program.programHandle, name)
                }
            }
            @Suppress("UNCHECKED_CAST")
            return _value!!
        }

    override fun isInitialized(): Boolean = _value !== null

    override fun toString(): String = if (isInitialized()) value.toString() else "ShaderProperty value not initialized yet."
}

enum class ShaderPropertyType { ATTRIBUTE, UNIFORM }
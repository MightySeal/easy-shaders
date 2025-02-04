package io.shaderfoundry.lib.program

internal interface ShaderProgramId {
    val handle: Int
}

@JvmInline
internal value class VertexShaderProgramId(override val handle: Int): ShaderProgramId

@JvmInline
internal value class FragmentShaderProgramId(override val handle: Int): ShaderProgramId {
    companion object {
        val INVALID_SHADER = FragmentShaderProgramId(0)
    }
}

@JvmInline
internal value class ProgramId(val programHandle: Int) {
    companion object {
        val INVALID_PROGRAM = ProgramId(0)
    }
}

internal val ProgramId.isValid
    get() = this != ProgramId.INVALID_PROGRAM

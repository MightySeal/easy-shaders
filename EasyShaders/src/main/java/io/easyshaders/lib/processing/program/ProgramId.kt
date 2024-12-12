package io.easyshaders.lib.processing.program



@JvmInline
value class ProgramId(val programHandle: Int) {
    companion object {
        val INVALID_PROGRAM = ProgramId(0)
    }
}

interface ShaderProgramId {
    val handle: Int
}

@JvmInline
value class VertexShaderProgramId(override val handle: Int): ShaderProgramId
@JvmInline
value class FragmentShaderProgramId(override val handle: Int): ShaderProgramId {
    companion object
}

val ProgramId.isValid
    get() = this != ProgramId.INVALID_PROGRAM

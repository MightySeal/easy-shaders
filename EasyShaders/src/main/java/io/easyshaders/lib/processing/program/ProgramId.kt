package io.easyshaders.lib.processing.program

@JvmInline
value class ProgramId(val programHandle: Int) {
    companion object {
        val INVALID_PROGRAM = ProgramId(0)
    }
}

val ProgramId.isValid
    get() = this != ProgramId.INVALID_PROGRAM
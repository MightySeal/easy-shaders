package io.easyshaders.lib.processing.program

interface FragmentShaderProgram {

    // TODO: add other property types
    fun setProperty(name: String, value: Float)
    fun setProperty(name: String, value: Int)
    fun setVectorProperty(name: String, x: Float, y: Float)
    fun setVectorProperty(name: String, x: Int, y: Int)
}

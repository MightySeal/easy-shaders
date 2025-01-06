package io.easyshaders.legacy

sealed interface LegacyCameraViewState {
    data object Loading: LegacyCameraViewState
    data class Ready(
        val controls: List<Control> = emptyList()
    ): LegacyCameraViewState
}

sealed interface Control {
    val title: String
    val id: String

    data class FloatSeek(
        override val title: String,
        override val id: String,
        val range: ClosedFloatingPointRange<Float>,
        val initial: Float,
        val step: Float,
    ): Control

    data class CheckBox(
        override val title: String,
        override val id: String,
    ) : Control
}

sealed interface ControlValue {
    val id: String

    data class FloatValue(override val id: String, val value: Float): ControlValue
    data class BooleanValue(override val id: String, val value: Boolean): ControlValue
}

package io.easyshaders.legacy

sealed interface LegacyCameraViewState {
    data object Loading: LegacyCameraViewState
    data object Ready: LegacyCameraViewState
}
package io.easyshaders.camera

import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.easyshaders.data.processor.DefaultCameraEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val cameraProviderManager: CameraXProcessCameraProviderManager,
    private val savedStateHandle: SavedStateHandle,
): ViewModel() {

    private lateinit var camera: Camera
    private lateinit var extensionsManager: ExtensionsManager

    var viewFinderState = MutableStateFlow(ViewFinderState())
    val aspectRatioStrategy =
        AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_NONE)
    var resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(aspectRatioStrategy)
        .build()

    private val previewUseCase = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build()

    private val imageCaptureUseCase = ImageCapture.Builder()
        .setResolutionSelector(resolutionSelector)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        captureMode: CaptureMode,
        cameraSelector: CameraSelector,
        rotation: Int,
    ) {
        viewModelScope.launch {
            val cameraProvider = cameraProviderManager.getCameraProvider()
            val extensionManagerJob = viewModelScope.launch {
                extensionsManager = ExtensionsManager.getInstanceAsync(
                    application,
                    cameraProvider,
                ).await()
            }
            var extensionsCameraSelector: CameraSelector? = null
            val useCaseGroupBuilder = UseCaseGroup.Builder()

            previewUseCase.surfaceProvider = surfaceProvider
            useCaseGroupBuilder.addUseCase(previewUseCase)

            if (captureMode == CaptureMode.PHOTO) {
                try {
                    extensionManagerJob.join()

                    // Query if extension is available.
                    if (extensionsManager.isExtensionAvailable(
                            cameraSelector,
                            ExtensionMode.NIGHT,
                        )
                    ) {
                        // Retrieve extension enabled camera selector
                        extensionsCameraSelector =
                            extensionsManager.getExtensionEnabledCameraSelector(
                                cameraSelector,
                                ExtensionMode.NIGHT,
                            )
                    }
                } catch (e: InterruptedException) {
                    // This should not happen unless the future is cancelled or the thread is
                    // interrupted by applications.
                }

                imageCaptureUseCase.targetRotation = rotation
                useCaseGroupBuilder.addUseCase(imageCaptureUseCase)
            }

            cameraProvider.unbindAll()
            val activeCameraSelector = extensionsCameraSelector ?: cameraSelector
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                activeCameraSelector,
                useCaseGroupBuilder
                    .addEffect(DefaultCameraEffect.create())
                    .build(),
            )
            viewFinderState.value.cameraState = CameraState.READY
        }
    }
}

data class ViewFinderState(
    var cameraState: CameraState = CameraState.NOT_READY,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
)

/**
 * Defines the current state of the camera.
 */
enum class CameraState {
    /**
     * Camera hasn't been initialized.
     */
    NOT_READY,

    /**
     * Camera is open and presenting a preview stream.
     */
    READY,

    /**
     * Camera is initialized but the preview has been stopped.
     */
    PREVIEW_STOPPED,
}

enum class CaptureMode {
    PHOTO,
    // VIDEO_READY,
    // VIDEO_RECORDING,
}
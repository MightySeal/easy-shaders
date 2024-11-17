package io.easyshaders.legacy

import android.content.Context
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.easyshaders.data.processor.DefaultCameraEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LegacyCameraViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val cameraProviderManager: CameraXProcessCameraProviderManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera

    private val aspectRatioStrategy =
        AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_NONE)
    private val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(aspectRatioStrategy)
        .build()

    private val previewUseCase = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build()

    private val imageCaptureUseCase = ImageCapture.Builder()
        .setResolutionSelector(resolutionSelector)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    val uiState: Flow<LegacyCameraViewState>
        field = MutableStateFlow<LegacyCameraViewState>(LegacyCameraViewState.Loading)

    init {
        viewModelScope.launch {
            cameraProvider = ProcessCameraProvider.getInstance(application).await()
            uiState.emit(LegacyCameraViewState.Ready)
        }
    }


    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        cameraProvider.unbindAll()

        val useCaseGroup = UseCaseGroup.Builder()
            // .setViewPort(previewView.viewPort!!)
            .addUseCase(imageCaptureUseCase)
            .addUseCase(previewUseCase)
            .addEffect(DefaultCameraEffect.create())
            .build()

        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup = useCaseGroup,
        )

        previewUseCase.surfaceProvider = surfaceProvider
        viewModelScope.launch {
            // uiState.emit(LegacyCameraViewState.Active)
        }
    }
}
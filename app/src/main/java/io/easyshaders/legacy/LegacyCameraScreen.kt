@file:OptIn(ExperimentalPermissionsApi::class)

package io.easyshaders.legacy

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.nfc.Tag
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.easyshaders.data.processor.utils.TAG
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun LegacyCameraScreen(
    viewModel: LegacyCameraViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    controller: LifecycleCameraController,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        val state by viewModel.uiState.collectAsState(LegacyCameraViewState.Loading)

        when (state) {
            LegacyCameraViewState.Loading -> {
                // Show loading state
            }

            LegacyCameraViewState.Ready -> {
                val lifecycleOwner = LocalLifecycleOwner.current
                val context = LocalContext.current
                val previewView = remember { PreviewView(context) }

                LegacyCameraView(
                    controller = controller,
                    context = context,
                    aspect = 0.75f,
                    modifier = modifier,
                    onPhotoTaken = viewModel::onTakenPhoto
                )

                LaunchedEffect(previewView) {
                    viewModel.startPreview(
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = previewView.surfaceProvider
                    )
                }
            }
        }

    } else {
        CameraPermission(cameraPermissionState)
    }
}

@Composable
private fun LegacyCameraView(
    controller: LifecycleCameraController,
    context: Context,
    aspect: Float,
    modifier: Modifier = Modifier,
    onPhotoTaken: (Bitmap) -> Unit,
) {

    Box(
        modifier = modifier
            .background(color = Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column {

            Spacer(modifier = Modifier
                .background(Color.Green)
                .height(10.dp)
                .background(Color.Blue)
            )
            CameraPreview(
                controller = controller,
                modifier = Modifier
                    .aspectRatio(aspect)
                    .fillMaxSize()
            )

            Spacer(modifier = Modifier
                .background(Color.Red)
                .height(10.dp)
                .background(Color.Yellow)
            )

        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            IconButton(
                onClick = {
                    controller.cameraSelector =
                        if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else CameraSelector.DEFAULT_BACK_CAMERA
                },
                modifier = Modifier
                    .offset(16.dp, 16.dp)
                    .padding(bottom = 16.dp, end = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera"
                )
            }
            IconButton(
                onClick = {
                    takePhoto(
                        context = context,
                        controller = controller,
                        onPhotoTaken = {
                            onPhotoTaken(it)
                        }
                    )
                },
                modifier = Modifier
                    .offset(16.dp, 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Take a photo"
                )
            }

//            CaptureButton(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .padding(bottom = 16.dp),
//            ) {
//
//            }
        }
    }
}

@Composable
private fun CaptureButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .height(75.dp)
                .width(75.dp),
        ) {}
    }
}

private fun takePhoto(
    context: Context,
    controller: LifecycleCameraController,
    onPhotoTaken: (Bitmap) -> Unit
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)

                onPhotoTaken(image.toBitmap())
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)

                Log.e(TAG, "exception: $exception")
            }

        }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProvider ->
        cameraProvider.addListener({
            continuation.resume(cameraProvider.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

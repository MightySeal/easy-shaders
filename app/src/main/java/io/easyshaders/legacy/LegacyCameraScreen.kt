@file:OptIn(ExperimentalPermissionsApi::class)

package io.easyshaders.legacy

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun LegacyCameraScreen(
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    viewModel: LegacyCameraViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            CameraController.IMAGE_CAPTURE
        }
    }

    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
        .build()

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }

    if (cameraPermissionState.status.isGranted) {
        val state by viewModel.uiState.collectAsState(LegacyCameraViewState.Loading)

        when (state) {
            LegacyCameraViewState.Loading -> {
                // Show loading state
            }

            LegacyCameraViewState.Ready -> {
                val lifecycleOwner = LocalLifecycleOwner.current
//                val previewView = remember { PreviewView(context) }

                LegacyCameraView(
                    imageCapture = imageCapture,
                    outputDirectory = outputDirectory,
                    executor = executor,
                    onImageCaptured = onImageCaptured,
                    previewView = previewView,
                    controller = controller,
                    context = context,
                    aspect = 0.75f,
                    viewModel = viewModel,
                    modifier = modifier
                )

//                LaunchedEffect(previewView) {
//                    viewModel.startPreview(
//                        lifecycleOwner = lifecycleOwner,
//                        surfaceProvider = previewView.surfaceProvider
//                    )
//                }
                LaunchedEffect(cameraSelector) {
                    val cameraProvider = context.getCameraProvider()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    preview.surfaceProvider = previewView.surfaceProvider
                }
            }
        }

    } else {
        CameraPermission(cameraPermissionState)
    }
}

@Composable
private fun LegacyCameraView(
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    previewView: PreviewView,
    controller: LifecycleCameraController,
    context: Context,
    aspect: Float,
    viewModel: LegacyCameraViewModel,
    modifier: Modifier = Modifier
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
            AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
//            CameraPreview(
//                controller = controller,
//                modifier = Modifier
//                    .aspectRatio(aspect)
//                    .fillMaxSize()
//            )
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
                        imageCapture = imageCapture,
                        outputDirectory = outputDirectory,
                        executor = executor,
                        onImageCaptured = onImageCaptured,
                        controller = controller,
                        onPhotoTaken = viewModel::onTakePhoto,
                        context = context
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
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    controller: LifecycleCameraController,
    onPhotoTaken: (Bitmap) -> Unit,
    context: Context
) {
//    val photoFile = File(
//        outputDirectory,
//        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
//    )
//
//    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
//
//    imageCapture.takePicture(outputOptions, executor, object: ImageCapture.OnImageSavedCallback {
//        override fun onError(exception: ImageCaptureException) {
//            Log.e("kilo", "Take photo error:", exception)
//            onError(exception)
//        }
//
//        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//            val savedUri = Uri.fromFile(photoFile)
//            onImageCaptured(savedUri)
//        }
//    })
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                val matrix = Matrix().apply {
                    postRotate(image.imageInfo.rotationDegrees.toFloat())
                    // postScale(-1f, 1f) handle for front camera mirror issue
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    image.toBitmap(),
                    0,
                    0,
                    image.width,
                    image.height,
                    matrix,
                    true
                )
                onPhotoTaken(rotatedBitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Log.e("Camera","Couldn't take photo: ", exception)
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

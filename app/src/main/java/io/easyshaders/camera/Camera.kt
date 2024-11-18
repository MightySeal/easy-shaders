@file:OptIn(ExperimentalPermissionsApi::class)

package io.easyshaders.camera

import android.Manifest
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun Camera(
    viewModel: CameraViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val lifecycleOwner = LocalLifecycleOwner.current
    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var rotation by remember { mutableStateOf(Surface.ROTATION_0) }


    if (cameraPermissionState.status.isGranted) {
        Box(
            modifier = modifier
                .background(color = Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CameraView(
                modifier = Modifier
                    .align(Alignment.Center)
                    .aspectRatio(3f/4),
                onSurfaceProviderReady = { surfaceProvider ->
                    viewModel.startPreview(
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = surfaceProvider,
                        captureMode = captureMode,
                        cameraSelector = cameraSelector,
                        rotation = rotation,
                    )
                }
            )

            CaptureButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {

            }
        }
    } else {
        CameraPermission(
            cameraPermissionState
        )
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



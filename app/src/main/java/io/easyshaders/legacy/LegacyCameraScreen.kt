@file:OptIn(ExperimentalPermissionsApi::class)

package io.easyshaders.legacy

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun LegacyCameraScreen(
    viewModel: LegacyCameraViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
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
                    previewView = previewView,
                    aspect = 0.75f,
                    modifier = modifier
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
    previewView: PreviewView,
    aspect: Float,
    modifier: Modifier = Modifier,
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
            AndroidView(
                { previewView },
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

        CaptureButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {

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
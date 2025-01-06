@file:OptIn(ExperimentalPermissionsApi::class)

package io.easyshaders.legacy

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        val state = viewModel.uiState.collectAsState(LegacyCameraViewState.Loading).value

        when (state) {
            is LegacyCameraViewState.Loading -> {
                // Show loading state
            }

            is LegacyCameraViewState.Ready -> {
                val lifecycleOwner = LocalLifecycleOwner.current
                val context = LocalContext.current
                val previewView = remember { PreviewView(context) }

                LegacyCameraView(
                    previewView = previewView,
                    aspect = 0.75f,
                    modifier = modifier
                )

                OverlayControls(
                    controls = state.controls,
                    modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    viewModel.onControlChange(it)
                }

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

            Spacer(
                modifier = Modifier
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


            Spacer(
                modifier = Modifier
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
private fun OverlayControls(
    controls: List<Control>,
    modifier: Modifier = Modifier,
    onChange: (ControlValue) -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn {
            items(controls) { control ->
                when (control) {
                    is Control.FloatSeek -> {
                        FloatSeekControl(control) {
                            onChange(ControlValue.FloatValue(control.id, it))
                        }
                    }

                    is Control.CheckBox -> {
                        CheckBoxControl(control) {
                            onChange(ControlValue.BooleanValue(control.id, it))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatSeekControl(control: Control.FloatSeek, onChange: (Float) -> Unit) {
    var sliderPosition by remember { mutableFloatStateOf(control.initial) }

    Column {
        Text(
            text = "${control.title}: ${"%.2f".format(sliderPosition)}",
            modifier = Modifier.align(Alignment.Start),
        )
        Slider(
            modifier = Modifier.width(300.dp),
            value = sliderPosition,
            valueRange = control.range,
            onValueChange = {
                sliderPosition = it
                onChange(it)
            }
        )
    }
}

@Composable
private fun CheckBoxControl(control: Control.CheckBox, onChange: (Boolean) -> Unit) {
    var checkboxState by remember { mutableStateOf(false) }
    Row {
        Checkbox(
            checked = checkboxState,
            onCheckedChange = { enabled ->
                checkboxState = enabled
                onChange(enabled)
            },
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Text(
            text = control.title,
            modifier = Modifier.align(Alignment.CenterVertically),
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
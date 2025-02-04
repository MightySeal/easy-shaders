package io.shaderfoundry.demo.legacy

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import android.Manifest
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.shaderfoundry.lib.CameraEffectManager
import io.shaderfoundry.lib.FragmentShader
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LegacyCameraScreen(
    modifier: Modifier,
    viewModel: LegacyCameraViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val cameraEffectManager = CameraEffectManager.create()
    val previewView = remember { PreviewView(context) }
    val gallery by viewModel.gallery.collectAsState()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        val controller = remember {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE)
                bindToLifecycle(lifecycleOwner)

                previewView.controller = this
                setEffects(setOf(cameraEffectManager))
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetContent = {
                PhotoBottomSheetContent(
                    gallery = gallery,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        val intent = Intent()
                        intent.setAction(Intent.ACTION_VIEW)
                        intent.setDataAndType(
                            it,
                            "image/*"
                        )
                        startActivity(context, intent, null)
                    }
                )
            }
        ) { padding ->
            LegacyCameraView(
                modifier = modifier,
                padding = padding,
                context = context,
                controller = controller,
                previewView = previewView,
                onExpand = {
                    scope.launch {
                        scaffoldState.bottomSheetState.expand()
                    }
                },
                onTakePhoto = viewModel::onTakePhoto
            )

            LaunchedEffect(cameraEffectManager) {
                cameraEffectManager.setEffectShaderSource(FragmentShader(GRAYSCALE, "sTexture"))
            }
        }
    } else {
        CameraPermission(cameraPermissionState)
    }
}

private val GRAYSCALE = """
    #version 310 es
    #extension GL_OES_EGL_image_external_essl3 : require
    
    precision mediump float;
    uniform samplerExternalOES sTexture;
    in vec2 vTextureCoord;
    
    out vec4 outColor;
    
    vec3 grayscale(vec3 color) {
        return vec3(dot(color, vec3(0.2126, 0.7152, 0.0722)));
    }
    
    void main() {
        vec4 color = texture(sTexture, vTextureCoord);
    
        outColor = vec4(grayscale(color.rgb), 1.0);
    }
""".trimIndent()

@Composable
fun LegacyCameraView(
    modifier: Modifier,
    padding: PaddingValues,
    context: Context,
    controller: LifecycleCameraController,
    previewView: PreviewView,
    onExpand: () -> Unit,
    onTakePhoto: (LegacyCameraViewModel.LocalPicture) -> Unit
) {
    val zoom = remember { mutableFloatStateOf(1.0f) }
    val isVideo = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Spacer(modifier = Modifier.height(10.dp))
            Box {
                AndroidView(
                    { previewView },
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        modifier = Modifier.padding(end = 8.dp),
                        shape = CircleShape,
                        onClick = {
                            controller.setZoomRatio(1.0f)
                            zoom.floatValue = 1.0f
                        },
                        elevation = null,
                        colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (zoom.floatValue == 1.0f) Color.White else Color.Transparent,
                            contentColor = if (zoom.floatValue == 1.0f) Color.Black else Color.White,
                        )
                    ) {
                        Text("1.0")
                    }
                    Button(
                        shape = CircleShape,
                        onClick = {
                            controller.setZoomRatio(2.0f)
                            zoom.floatValue = 2.0f
                        },
                        elevation = null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (zoom.floatValue == 2.0f) Color.White else Color.Transparent,
                            contentColor = if (zoom.floatValue == 2.0f) Color.Black else Color.White,
                        )
                    ) {
                        Text("2.0")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { isVideo.value = false }) {
                    Text("Photo")
                }
                Button(
                    onClick = {
                        // isVideo.value = true
                    },
                    enabled = false
                ) {
                    Text("Video")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ControlButton(
                    icon = Icons.Default.PhotoLibrary,
                    description = "Open Library",
                    onClick = {
                        onExpand()
                    },
                )
                Button(
                    onClick = {
                        if (isVideo.value) {
                            // TODO
                        } else {
                            takePicture(
                                controller = controller,
                                context = context,
                                onSave = {
                                    onTakePhoto(it)
                                }
                            )
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalContentColor.current),
                    modifier = Modifier
                        .height(75.dp)
                        .width(75.dp),
                ) {}
                ControlButton(
                    icon = Icons.Default.Cameraswitch,
                    description = "Switch camera",
                    onClick = { switchCamera(controller) },
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    description: String = "",
    onClick: () -> Unit
) {
    IconButton(
        onClick = { onClick() },
        modifier = Modifier
            .offset(16.dp, 16.dp)
            .padding(bottom = 16.dp, end = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description
        )
    }
}

private fun switchCamera(
    controller: LifecycleCameraController,
) {
    controller.cameraSelector =
        if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
}

private fun takePicture(
    controller: LifecycleCameraController,
    context: Context,
    onSave: (picture: LegacyCameraViewModel.LocalPicture) -> Unit = {}
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                Log.e("Camera", "onCaptureSuccess")

                val matrix = Matrix().apply {
                    postRotate(image.imageInfo.rotationDegrees.toFloat())
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

                val filename = "${System.currentTimeMillis()}.jpg"

                var outputStream: OutputStream? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver?.also { resolver ->
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        }

                        val imageUri: Uri? = resolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )

                        outputStream = imageUri?.let { resolver.openOutputStream(it) }
                        onSave(
                            LegacyCameraViewModel.LocalPicture(
                                rotatedBitmap, imageUri
                            )
                        )
                    }
                } else {
                    val imagesDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val file = File(imagesDir, filename)

                    onSave(
                        LegacyCameraViewModel.LocalPicture(
                            rotatedBitmap, file.toUri()
                        )
                    )
                    outputStream = FileOutputStream(file)
                }

                outputStream?.use {
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Log.e("Camera", "Couldn't take photo: ", exception)
            }
        }
    )
}

private fun recordVideo(
    controller: LifecycleCameraController,
    context: Context,
) { }

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

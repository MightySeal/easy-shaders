package io.easyshaders

import android.R
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.MatrixExt.postRotate
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.graphics.Matrix
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import io.easyshaders.MainActivity.Companion.CAMERAX_PERMISSIONS
import io.easyshaders.MainActivity.Companion.PROVIDER_AUTHORITY
import io.easyshaders.legacy.LegacyCameraViewModel
import io.easyshaders.legacy.PhotoBottomSheetContent
import io.easyshaders.ui.theme.EasyShadersTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


@AndroidEntryPoint
//class MainActivity : ComponentActivity() {
//
//    private lateinit var outputDirectory: File
//    private lateinit var cameraExecutor: ExecutorService
//
//    private var shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)
//
//    private lateinit var photoUri: Uri
//    private var shouldShowPhoto: MutableState<Boolean> = mutableStateOf(false)
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContent {
//            EasyShadersTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    LegacyCameraScreen(
//                        outputDirectory = outputDirectory,
//                        executor = cameraExecutor,
//                        onImageCaptured = ::handleImageCapture,
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(innerPadding)
//                    )
//                }
//            }
//        }
//
//        outputDirectory = getOutputDirectory()
//        cameraExecutor = Executors.newSingleThreadExecutor()
//    }
//
//    private fun handleImageCapture(uri: Uri) {
//        Log.i(TAG, "Image captured: $uri")
//        shouldShowCamera.value = false
//
//        photoUri = uri
//        shouldShowPhoto.value = true
//    }
//
//    private fun getOutputDirectory(): File {
//        val mediaDir = externalMediaDirs.firstOrNull()?.let {
//            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
//        }
//
//        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
//    }
//}
class MainActivity : ComponentActivity() {

    companion object {
        const val PROVIDER_AUTHORITY = "io.easyshaders.fileprovider"
        val CAMERAX_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!hasRequiredPermissions()) {
            requestPermissions(
                this, CAMERAX_PERMISSIONS, 0
            )
        }
        setContent {
            EasyShadersTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAppScreen(
    modifier: Modifier,
    viewModel: LegacyCameraViewModel = hiltViewModel(),
) {
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoomLevel by remember { mutableFloatStateOf(0.0f) }
    val imageCaptureUseCase = remember { ImageCapture.Builder().build() }

    val context = LocalContext.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            CameraController.IMAGE_CAPTURE
        }
    }
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val bitmaps by viewModel.bitmaps.collectAsState()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            PhotoBottomSheetContent(
                bitmaps = bitmaps,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column {
            Spacer(
                modifier = Modifier
                    .height(10.dp)
            )
            io.easyshaders.legacy.CameraPreview(
                modifier = Modifier
                    .aspectRatio(0.75f)
                    .fillMaxSize(),
                controller = controller,
//                lensFacing = lensFacing,
//                zoomLevel = zoomLevel,
//                imageCaptureUseCase = imageCaptureUseCase
            )
            Spacer(
                modifier = Modifier
                    .height(10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = { controller.setZoomRatio(1.0f) }) {
                    Text("Zoom 1.0")
                }
                Button(onClick = { controller.setZoomRatio(2.0f) }) {
                    Text("Zoom 2.0")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            scaffoldState.bottomSheetState.expand()
                        }
                    },
                    modifier = Modifier
                        .offset(16.dp, 16.dp)
                        .padding(bottom = 16.dp, end = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Open Library"
                    )
                }
                Button(
                    onClick = {
//                        val outputFileOptions =
//                            ImageCapture.OutputFileOptions.Builder(File(context.externalCacheDir, "image.jpg"))
//                                .build()
//                        val callback = object : ImageCapture.OnImageSavedCallback {
//                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                                outputFileResults.savedUri?.shareAsImage(context)
//                                outputFileResults.savedUri?.let { }
//                            }
//
//                            override fun onError(exception: ImageCaptureException) {
//                            }
//                        }
//                        imageCaptureUseCase.takePicture(
//                            outputFileOptions,
//                            ContextCompat.getMainExecutor(context),
//                            callback
//                        )

                        controller.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    super.onCaptureSuccess(image)
                                    Log.e("Camera", "Success")

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

                                    //Output stream
                                    var fos: OutputStream? = null

                                    //For devices running android >= Q
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        //getting the contentResolver
                                        context.contentResolver?.also { resolver ->

                                            //Content resolver will process the contentvalues
                                            val contentValues = ContentValues().apply {

                                                //putting file information in content values
                                                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                            }

                                            //Inserting the contentValues to contentResolver and getting the Uri
                                            val imageUri: Uri? =
                                                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                                            //Opening an outputstream with the Uri that we got
                                            fos = imageUri?.let { resolver.openOutputStream(it) }
                                            viewModel.onTakePhoto(rotatedBitmap, imageUri)
                                        }
                                    } else {
                                        //These for devices running on android < Q
                                        //So I don't think an explanation is needed here
                                        val imagesDir =
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                        val image = File(imagesDir, filename)
                                        viewModel.onTakePhoto(rotatedBitmap, image.toUri())
                                        fos = FileOutputStream(image)
                                    }

                                    fos?.use {
                                        //Finally writing the bitmap to the output stream that we opened
                                        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    super.onError(exception)
                                    Log.e("Camera", "Couldn't take photo: ", exception)
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier
                        .height(75.dp)
                        .width(75.dp),
                ) {}
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
            }
        }
    }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    controller: LifecycleCameraController,
    lensFacing: Int,
    zoomLevel: Float,
    imageCaptureUseCase: ImageCapture
) {
    val previewUseCase = remember { androidx.camera.core.Preview.Builder().build() }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun rebindCameraProvider() {
        cameraProvider?.let { cameraProvider ->
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase, imageCaptureUseCase
            )
            controller.bindToLifecycle(lifecycleOwner)
            cameraControl = camera.cameraControl
        }
    }

    LaunchedEffect(Unit) {
        cameraProvider = ProcessCameraProvider.awaitInstance(localContext)
        rebindCameraProvider()
    }

    LaunchedEffect(lensFacing) {
        rebindCameraProvider()
    }

    LaunchedEffect(zoomLevel) {
        cameraControl?.setLinearZoom(zoomLevel)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PreviewView(context).also {
                it.controller = controller
                // this.surfaceProvider =
                previewUseCase.surfaceProvider = it.surfaceProvider
                controller.bindToLifecycle(lifecycleOwner)
                rebindCameraProvider()
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    EasyShadersTheme {
        CameraAppScreen(Modifier)
    }
}

fun Uri.shareAsImage(context: Context) {
    val contentUri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, toFile())
    val shareIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, contentUri)
        type = "image/jpeg"
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
}

package com.example.mlkit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.mlkit.ui.theme.MLkitTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var imageUri: Uri
    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var detectionObjects by mutableStateOf(emptyList<DetectedObject>())


    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture(), ::handleImageCapture)

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { selectedImageUri = it }

    private val objectDetector by lazy {
        val options =
            ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects().enableClassification().build()
        ObjectDetection.getClient(options)
    }


    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MLkitTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    ImageSelectorScreen(
                        onCameraClick = { requestCameraPermission() },
                        onGalleryClick = { launchGallery() },
                        onDetectObjectsClick = { detectObjects() },
                        selectedImageUri,
                        detectionObjects
                    )
                }
            }
        }
    }

    private fun detectObjects() {
        selectedImageUri?.let {
            InputImage.fromFilePath(this, it).let(objectDetector::process)
                .addOnSuccessListener { objects ->
                    detectionObjects = objects
                }.addOnFailureListener {
                    detectionObjects = emptyList()
                }
        }
    }

    private fun requestCameraPermission() {
        if (isCameraPermissionGranted()) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun isCameraPermissionGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun handleImageCapture(success: Boolean) {
        selectedImageUri = if (success) {
            imageUri
        } else {
            null
        }
    }


    private fun launchCamera() {
        imageUri = FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.fileprovider", createImageFile()
        )
        cameraLauncher.launch(imageUri)
    }

    private fun launchGallery() = galleryLauncher.launch("image/*")

    private fun createImageFile(): File = File.createTempFile(
        "Image_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}_",
        ".jpg",
        cacheDir
    )

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                showToast("Camera permission denied")
            }
        }

}

@Composable
fun ImageSelectorScreen(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDetectObjectsClick: () -> Unit,
    selectedImageUri: Uri?,
    detectedObjects: List<DetectedObject>
) {
    var localImageUri by remember {
        mutableStateOf(selectedImageUri)
    }

    var localDetectedObjects by remember {
        mutableStateOf(detectedObjects)
    }

    // Used this approach to learn LaunchedEffect otherwise passing lambda maybe better in this scenario
    LaunchedEffect(selectedImageUri) {
        localImageUri = selectedImageUri
        localDetectedObjects = emptyList()
    }

    LaunchedEffect(detectedObjects) {
        localDetectedObjects = detectedObjects
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onCameraClick) { Text(text = "Open Camera") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGalleryClick) { Text(text = "Open Gallery") }
        Spacer(modifier = Modifier.height(16.dp))
        localImageUri?.let { uri ->
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .size(400.dp)
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )
            if (localDetectedObjects.isEmpty()) {
                Button(onClick = onDetectObjectsClick) {
                    Text(text = "Start Object Detection")
                }
            } else {
                localDetectedObjects.forEach {
                    if (it.labels.isNotEmpty()) {
                        Text(text = it.labels.first().text + ":" + it.labels.first().confidence)
                    }
                }
            }
        }
    }
}


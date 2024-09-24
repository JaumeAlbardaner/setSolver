package com.jalbardaner.android_setSolver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jalbardaner.android_setSolver.Constants.LABELS_PATH
import com.jalbardaner.android_setSolver.Constants.MODEL_PATH
import com.jalbardaner.android_setSolver.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: androidx.camera.core.Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector

    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun runSatSolver(detections: List<String>?, bboxes: List<BoundingBox>?){

        val best_bboxes : MutableList<BoundingBox> = mutableListOf()

        val imageViews = listOf(
            findViewById<ImageView>(R.id.card1),
            findViewById<ImageView>(R.id.card2),
            findViewById<ImageView>(R.id.card3)
        )

        if(detections.isNullOrEmpty()){
                runOnUiThread {
                    imageViews.forEach { imageView ->
                        imageView.setImageResource(R.drawable.red_cross)
                    }
                }
                return
        }
        if(bboxes.isNullOrEmpty()){
            return
        }

        val output = SATSolver.runSatSolver(detections)

        var text = ""
        var cardNum = 0
        if (output != null && output.isNotEmpty()){
            for (i in detections.indices) {
                // Accessing elements in the output list
                val element = output[i]
                if (element>0){

                    // text += detections[i]
                    best_bboxes.add(bboxes[i])
                    // Concatenate detection name and ".jpg"
                    val imageName = detections[i]

                    // Get the resource ID of the image dynamically using its name
                    val imageResId = resources.getIdentifier(imageName, "drawable", this.packageName)

                    // If the resource is found (valid resource ID), update the ImageView
                    if (imageResId != 0) {
                        runOnUiThread {
                            imageViews[cardNum].setImageResource(imageResId)
                            cardNum +=1
                        }

                    } else {
                        // Handle the case where the image is not found
                        Log.e("ImageError", "Image not found: $imageName")
                    }

                }

                println("Element at index $i: $element")
            }
        }
        else {
            runOnUiThread {
                imageViews.forEach { imageView ->
                    imageView.setImageResource(R.drawable.red_cross)
                }
            }

            text = "No match"
        }

        runOnUiThread {
            findViewById<TextView>(R.id.output).text = text
            runOnUiThread {
                binding.overlay.apply {
                    setBestResults(best_bboxes)
//                    invalidate()
                }
            }
        }
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  androidx.camera.core.Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            val detections = detector.detect(rotatedBitmap)

            // Initialize a mutable list for detection names
            val detectionNames: MutableList<String> = mutableListOf()

            // Loop through detections and add class names to detectionNames
            detections?.forEach { detection ->
                var drawableText = detection.clsName
                drawableText = drawableText.filter { !it.isWhitespace() }
                detectionNames.add(drawableText)
            }

            // Pass the detectionNames to runSatSolver
            runSatSolver(detectionNames, detections)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { startCamera() }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        binding.overlay.invalidate()
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {

        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
        }
    }

}


package com.example.divyanyanreader

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.divyanyanreader.databinding.FragmentScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class ScanFragment : Fragment(R.layout.fragment_scan), InstanceSegmentation.InstanceSegmentationListener {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels()
    private lateinit var drawImages: DrawImages
    private lateinit var imageCapture: ImageCapture
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraStarted = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var tts: TextToSpeech
    private var lastSpeechTime: Long = 0
    private val speechDelay = 1500L
    private var isTtsReady = false
    private var isProcessingDocument = false
    @Volatile
    private var latestPoints: List<org.opencv.core.Point>? = null

    private var stableFrameCount = 0
    private var lastArea = 0.0
    private var isLocked = false
    private var isCapturing = false

    private val areaThreshold = 0.10
    private val requiredStableFrames = 15

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanBinding.bind(view)
        drawImages = DrawImages(requireContext().applicationContext)

        tts = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = java.util.Locale.US
                isTtsReady = true
            }
        }

        viewModel.initializeModel(this)
        checkPermission()
    }
    override fun onResume() {
        super.onResume()
        if (isProcessingDocument) {
            resetStateAfterProcessing()
            startCamera()
        }
    }
    private fun startCamera() {
        if (cameraStarted || _binding == null) return
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val aspectRatio = AspectRatio.RATIO_4_3

            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build()
                .also { it.surfaceProvider = binding.previewView.surfaceProvider }

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val analyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ImageAnalyzer()) }

            provider.unbindAll()
            try {
                provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer, imageCapture)
                cameraStarted = true
            } catch (exc: Exception) {
                Log.e("CameraX", "Binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private fun stopCameraPipeline() {
        cameraProvider?.unbindAll()
        cameraStarted = false
    }
    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            if (!viewModel.isModelReady || isCapturing || isProcessingDocument) {
                image.close()
                return
            }

            val bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

            viewModel.runSegmentation(rotatedBitmap)
            image.close()
        }
    }

    private fun provideAudioGuidance(state: DetectionState) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpeechTime < speechDelay || isCapturing || isProcessingDocument || !isTtsReady) return

        val points = state.quadPoints
        if (points == null || points.size < 4) {
            speak("Searching for document")
            lastSpeechTime = currentTime
            return
        }

        val edgeMargin = 20.0
        val frameWidth = 450.0
        val frameHeight = 600.0

        val touchesEdge = points.any {
            it.x < edgeMargin || it.x > (frameWidth - edgeMargin) ||
                    it.y < edgeMargin || it.y > (frameHeight - edgeMargin)
        }

        val centerX = points.map { it.x }.average()
        val centerY = points.map { it.y }.average()
        val screenMidX = frameWidth / 2.0
        val screenMidY = frameHeight / 2.0
        val centerThreshold = 60.0

        val instruction = when {
            touchesEdge -> "Move further away"
            state.area < 18000.0 -> "Move closer"
            centerX < screenMidX - centerThreshold -> "Move Left"
            centerX > screenMidX + centerThreshold -> "Move Right"
            centerY < screenMidY - centerThreshold -> "Move Up"
            centerY > screenMidY + centerThreshold -> "Move Down"
            else -> "Hold still"
        }

        speak(instruction)
        lastSpeechTime = currentTime
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuidanceID")
        }
    }

    override fun onDetect(interfaceTime: Long, results: List<SegmentationResult>, preProcessTime: Long, postProcessTime: Long) {
        if (isProcessingDocument) return
        val state = drawImages.invoke(results, isLocked)
        if (state.isQuadFound) {
            latestPoints = state.quadPoints
        }

        activity?.runOnUiThread {
            updateStability(state, results)
            binding.ivOverlay.setImageBitmap(state.bitmap)
            provideAudioGuidance(state)
        }
    }

    private fun updateStability(state: DetectionState, results: List<SegmentationResult>) {
        if (isCapturing || isProcessingDocument) return

        if (state.isQuadFound) {
            val diff = if (lastArea > 0) kotlin.math.abs(state.area - lastArea) / lastArea else 0.0
            stableFrameCount = if (diff <= areaThreshold) stableFrameCount + 1 else 0
            lastArea = state.area
        } else {
            stableFrameCount = 0
            lastArea = 0.0
        }

        isLocked = stableFrameCount >= requiredStableFrames

        if (isLocked && results.isNotEmpty()) {
            captureDocument()
        }
    }

    private fun captureDocument() {
        if (isCapturing || isProcessingDocument) return
        isCapturing = true

        requireActivity().window.decorView.playSoundEffect(android.view.SoundEffectConstants.CLICK)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "SCAN_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                val uri = result.savedUri ?: run {
                    isCapturing = false
                    return
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                        val points = latestPoints ?: run {
                            withContext(Dispatchers.Main) { isCapturing = false }
                            return@launch
                        }

                        val cropped = crop(bitmap, points)

                        requireContext().contentResolver.openOutputStream(uri)?.use {
                            cropped.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        }

                        val imageFile = File(requireContext().cacheDir, "latest_cropped.jpg")
                        imageFile.outputStream().use { out ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Document Saved!", Toast.LENGTH_SHORT).show()
                            isCapturing = false
                            isProcessingDocument = true
                            stopCameraPipeline()
                            startActivity(ProcessImageActivity.createIntent(requireContext(), imageFile.absolutePath))
                        }
                    } catch (exception: Exception) {
                        withContext(Dispatchers.Main) {
                            isCapturing = false
                            Log.e("Capture", "Error processing capture", exception)
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                isCapturing = false
                Log.e("Capture", "Error: ${exception.message}")
            }
        })
    }
    private fun resetStateAfterProcessing() {
        isProcessingDocument = false
        stableFrameCount = 0
        lastArea = 0.0
        isLocked = false
        latestPoints = null
    }
    private fun crop(bitmap: Bitmap, points: List<org.opencv.core.Point>): Bitmap {
        val srcMat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, srcMat)

        val mappedPoints = points.map {
            org.opencv.core.Point(it.x * bitmap.width / 450.0, it.y * bitmap.height / 600.0)
        }

        val sortedByY = mappedPoints.sortedBy { it.y }
        val topHalf = sortedByY.take(2).sortedBy { it.x }
        val bottomHalf = sortedByY.takeLast(2).sortedByDescending { it.x }
        val sortedQuad = listOf(topHalf[0], topHalf[1], bottomHalf[0], bottomHalf[1])

        val srcPointsMat = org.opencv.utils.Converters.vector_Point2f_to_Mat(sortedQuad)

        val resultWidth = 1200.0
        val resultHeight = 1650.0
        val dstPointsMat = org.opencv.utils.Converters.vector_Point2f_to_Mat(
            listOf(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(resultWidth, 0.0),
                org.opencv.core.Point(resultWidth, resultHeight),
                org.opencv.core.Point(0.0, resultHeight)
            )
        )

        val perspectiveMatrix = org.opencv.imgproc.Imgproc.getPerspectiveTransform(srcPointsMat, dstPointsMat)
        val dstMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.warpPerspective(
            srcMat,
            dstMat,
            perspectiveMatrix,
            org.opencv.core.Size(resultWidth, resultHeight)
        )

        val resultBitmap = Bitmap.createBitmap(resultWidth.toInt(), resultHeight.toInt(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(dstMat, resultBitmap)

        srcMat.release()
        dstMat.release()
        perspectiveMatrix.release()
        srcPointsMat.release()
        dstPointsMat.release()
        return resultBitmap
    }

    private fun checkPermission() {
        val granted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.all { it.value }) startCamera()
    }

    override fun onError(error: String) {}

    override fun onEmpty() {}

    override fun onDestroyView() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        stopCameraPipeline()
        cameraExecutor.shutdown()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val requiredPermissions = arrayOf(Manifest.permission.CAMERA)
    }
}
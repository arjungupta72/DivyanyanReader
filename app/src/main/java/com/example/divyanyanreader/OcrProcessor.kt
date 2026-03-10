package com.example.divyanyanreader

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrProcessor(private val context: Context) {

    suspend fun run(bitmap: Bitmap, engine: OcrEngine, language: OcrLanguage): String {
        return when (engine) {
            OcrEngine.ML_KIT -> runMlKit(bitmap, language)
            OcrEngine.TESSERACT -> runTesseract(bitmap, language)
        }
    }

    private suspend fun runMlKit(bitmap: Bitmap, language: OcrLanguage): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = when (language) {
            OcrLanguage.HINDI -> TextRecognition.getClient(
                DevanagariTextRecognizerOptions.Builder().build()
            )
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }

        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    cont.resume(result.text)
                }
                .addOnFailureListener { error ->
                    recognizer.close()
                    cont.resumeWithException(error)
                }
        }
    }

    private suspend fun runTesseract(bitmap: Bitmap, language: OcrLanguage): String = withContext(Dispatchers.IO) {
        val trainedDataFile = ensureTrainedData(language)

        val baseApi = TessBaseAPI()
        val filesDir = File(context.filesDir, "tesseract")

        val initialized = baseApi.init(
            filesDir.absolutePath,
            language.tesseractTag
        )

        if (!initialized) {
            baseApi.recycle()
            throw IllegalStateException(
                "Failed to initialize Tesseract for ${language.tesseractTag}. " +
                        "Expected data: ${trainedDataFile.absolutePath}"
            )
        }

        try {
            baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            baseApi.setImage(bitmap)
            baseApi.getUTF8Text() ?: ""
        } finally {
            baseApi.recycle()
        }
    }

    private fun ensureTrainedData(language: OcrLanguage): File {
        val root = File(context.filesDir, "tesseract")
        val tessData = File(root, "tessdata")
        if (!tessData.exists()) {
            tessData.mkdirs()
        }

        val target = File(tessData, "${language.tesseractTag}.traineddata")
        if (target.exists() && target.length() > 0L) return target

        context.assets.open("tessdata/${language.tesseractTag}.traineddata").use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }
}

package com.example.divyanyanreader

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.divyanyanreader.databinding.ActivityProcessImageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ProcessImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProcessImageBinding
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProcessImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath.isNullOrBlank()) {
            Toast.makeText(this, "Image path missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.tvStatus.text = getString(R.string.processing_image)

        initializeTts()
        runOcrAndTts(imagePath)
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            isTtsReady = status != TextToSpeech.ERROR
        }
    }

    private fun runOcrAndTts(imagePath: String) {
        lifecycleScope.launch {
            val engine = ReaderPreferences.getOcrEngine(this@ProcessImageActivity)
            val language = ReaderPreferences.getOcrLanguage(this@ProcessImageActivity)

            val text = try {
                withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                        ?: throw IllegalStateException("Unable to decode image")
                    OcrProcessor(this@ProcessImageActivity).run(bitmap, engine, language)
                }
            } catch (error: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.processing_failed)
                binding.tvOutput.text = error.message ?: "OCR failed"
                return@launch
            }

            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = getString(R.string.ocr_complete)
            binding.tvOutput.text = text.ifBlank { getString(R.string.no_text_found) }
            speakNow(text, language)
        }
    }

    private fun speakNow(text: String, language: OcrLanguage) {
        if (!isTtsReady || text.isBlank()) return
        tts?.language = Locale.forLanguageTag(language.localeTag)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "OCR_TTS")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_IMAGE_PATH = "extra_image_path"

        fun createIntent(context: Context, imagePath: String): Intent {
            return Intent(context, ProcessImageActivity::class.java).putExtra(EXTRA_IMAGE_PATH, imagePath)
        }
    }
}

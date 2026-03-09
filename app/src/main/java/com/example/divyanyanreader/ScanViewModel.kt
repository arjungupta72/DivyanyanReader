package com.example.divyanyanreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRepository(application.applicationContext)
    private var segmenter: InstanceSegmentation? = null

    @Volatile
    var isModelReady: Boolean = false
        private set

    fun initializeModel(listener: InstanceSegmentation.InstanceSegmentationListener) {
        if (isModelReady && segmenter != null) return

        viewModelScope.launch(Dispatchers.IO) {
            OpenCVLoader.initDebug()
            segmenter = repository.createSegmenter(listener)
            isModelReady = true
        }
    }

    fun runSegmentation(bitmap: android.graphics.Bitmap) {
        segmenter?.invoke(bitmap)
    }

    fun close() {
        segmenter?.close()
        segmenter = null
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
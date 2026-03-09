package com.example.divyanyanreader

import android.content.Context

class ScanRepository(private val appContext: Context) {

    fun createSegmenter(listener: InstanceSegmentation.InstanceSegmentationListener): InstanceSegmentation {
        return InstanceSegmentation(
            context = appContext,
            modelPath = "v6.tflite",
            labelPath = null,
            instanceSegmentationListener = listener,
            message = {}
        )
    }
}
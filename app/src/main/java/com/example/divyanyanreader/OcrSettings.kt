package com.example.divyanyanreader

enum class OcrEngine(val storageValue: String) {
    ML_KIT("ml_kit"),
    TESSERACT("tesseract");

    companion object {
        fun fromStorageValue(value: String?): OcrEngine {
            return entries.firstOrNull { it.storageValue == value } ?: ML_KIT
        }
    }
}

enum class OcrLanguage(
    val storageValue: String,
    val displayName: String,
    val mlKitTag: String,
    val tesseractTag: String,
    val localeTag: String
) {
    ENGLISH("english", "English", "en", "eng", "en-US"),
    HINDI("hindi", "Hindi", "hi", "hin", "hi-IN");

    companion object {
        fun fromStorageValue(value: String?): OcrLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: ENGLISH
        }

        fun displayNames(): Array<String> = entries.map { it.displayName }.toTypedArray()
    }
}

object ReaderPreferences {
    private const val PREFS_NAME = "reader_preferences"
    private const val KEY_OCR_ENGINE = "ocr_engine"
    private const val KEY_OCR_LANGUAGE = "ocr_language"

    fun getOcrEngine(context: android.content.Context): OcrEngine {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return OcrEngine.fromStorageValue(prefs.getString(KEY_OCR_ENGINE, OcrEngine.ML_KIT.storageValue))
    }

    fun setOcrEngine(context: android.content.Context, engine: OcrEngine) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OCR_ENGINE, engine.storageValue)
            .apply()
    }

    fun getOcrLanguage(context: android.content.Context): OcrLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return OcrLanguage.fromStorageValue(prefs.getString(KEY_OCR_LANGUAGE, OcrLanguage.ENGLISH.storageValue))
    }

    fun setOcrLanguage(context: android.content.Context, language: OcrLanguage) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OCR_LANGUAGE, language.storageValue)
            .apply()
    }
}

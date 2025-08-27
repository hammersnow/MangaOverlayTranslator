package com.ekrem.mangaoverlay.ocr
import android.content.Context; import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.*; import kotlinx.coroutines.tasks.await
class OcrTranslator(context: Context) {
  private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  private val translator: Translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.TURKISH).build())
  suspend fun recognizeAndTranslate(bmp: Bitmap): String {
    translator.downloadModelIfNeeded().await()
    val image = InputImage.fromBitmap(bmp, 0)
    val result = recognizer.process(image).await()
    val raw = result.text.trim()
    if (raw.isBlank()) return ""
    return translator.translate(raw).await()
  }
}

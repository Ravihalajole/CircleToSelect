package com.akslabs.circletosearch.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.akslabs.circletosearch.ui.components.TextNode
import com.akslabs.circletosearch.ui.components.Word
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

object MlKitEngine {
    // Initializing the recognizer with default Latin options
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap): List<TextNode> {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        return try {
            val result = recognizer.process(image).await()
            result.textBlocks.map { block ->
                
                val words = block.lines.flatMap { line ->
                    line.elements.mapIndexed { index, element ->
                        Word(
                            text = element.text,
                            index = index,
                            startIndex = 0, // ML Kit doesn't provide absolute start index per block easily
                            endIndex = element.text.length,
                            bounds = android.graphics.RectF(element.boundingBox ?: Rect())
                        )
                    }
                }

                TextNode(
                    id = UUID.randomUUID().toString(),
                    fullText = block.text,
                    bounds = block.boundingBox ?: Rect(),
                    words = words
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MlKitEngine", "OCR Error: ${e.message}")
            emptyList()
        }
    }
}

package com.akslabs.circletosearch.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.akslabs.circletosearch.ui.components.TextNode
import com.akslabs.circletosearch.ui.components.Word
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

object MlKitEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap): List<TextNode> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val nodes = result.textBlocks.map { block ->
                    val words = block.lines.flatMap { line ->
                        line.elements.mapIndexed { index, element ->
                            Word(
                                text = element.text,
                                index = index,
                                startIndex = 0,
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
                // Resume the coroutine with the results
                if (continuation.isActive) continuation.resume(nodes)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MlKitEngine", "OCR Error: ${e.message}")
                // Resume with an empty list on failure
                if (continuation.isActive) continuation.resume(emptyList())
            }
    }
}

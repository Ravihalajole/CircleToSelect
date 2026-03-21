package com.akslabs.circletosearch.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer

// Sealed class representing all supported QR / barcode result types
sealed class QrResult {
    data class Url(val url: String, val displayUrl: String) : QrResult()
    data class WiFi(val ssid: String, val password: String?, val security: String) : QrResult()
    data class Phone(val number: String) : QrResult()
    data class Product(val barcode: String) : QrResult()
    data class VCard(val name: String?, val phone: String?, val email: String?, val raw: String) : QrResult()
    data class GeoPoint(val lat: Double, val lng: Double) : QrResult()
    data class PlainText(val text: String) : QrResult()
}

object QrScanner {

    fun scanBitmap(bitmap: Bitmap): QrResult? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.PDF_417
                )
            )

            val reader = MultiFormatReader()
            val result: Result = reader.decode(binaryBitmap, hints)
            parseResult(result.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResult(text: String): QrResult {
        return when {
            // URL
            text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) -> {
                val display = text.removePrefix("http://").removePrefix("https://").trimEnd('/')
                QrResult.Url(text, display)
            }
            // WiFi: WIFI:S:SSID;T:WPA;P:password;;
            text.startsWith("WIFI:", ignoreCase = true) -> parseWifi(text)
            // Tel
            text.startsWith("tel:", ignoreCase = true) -> {
                QrResult.Phone(text.removePrefix("tel:").trim())
            }
            // vCard
            text.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCard(text)
            // Geo
            text.startsWith("geo:", ignoreCase = true) -> parseGeo(text)
            // Barcode-only (all digits, length 8-14)
            text.matches(Regex("\\d{8,14}")) -> QrResult.Product(text)
            // Plain text fallback
            else -> QrResult.PlainText(text)
        }
    }

    private fun parseWifi(text: String): QrResult {
        val ssid = Regex("S:([^;]*)").find(text)?.groupValues?.get(1) ?: ""
        val pass = Regex("P:([^;]*)").find(text)?.groupValues?.get(1)
        val sec  = Regex("T:([^;]*)").find(text)?.groupValues?.get(1) ?: "WPA"
        return QrResult.WiFi(ssid, pass, sec)
    }

    private fun parseVCard(text: String): QrResult {
        val name  = Regex("FN:([^\r\n]+)").find(text)?.groupValues?.get(1)
        val phone = Regex("TEL[^:]*:([^\r\n]+)").find(text)?.groupValues?.get(1)
        val email = Regex("EMAIL[^:]*:([^\r\n]+)").find(text)?.groupValues?.get(1)
        return QrResult.VCard(name, phone, email, text)
    }

    private fun parseGeo(text: String): QrResult {
        return try {
            val coords = text.removePrefix("geo:").split(",")
            val lat = coords[0].toDouble()
            val lng = coords[1].split("?")[0].toDouble()
            QrResult.GeoPoint(lat, lng)
        } catch (e: Exception) {
            QrResult.PlainText(text)
        }
    }
}

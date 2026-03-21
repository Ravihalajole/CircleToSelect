package com.akslabs.circletosearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akslabs.circletosearch.utils.QrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

@Composable
fun QrCodeResultSheet(
    context: Context,
    bitmap: android.graphics.Bitmap?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var qrResult by remember { mutableStateOf<QrResult?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    var notFound by remember { mutableStateOf(false) }

    // Animate scanline
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanlineY"
    )

    LaunchedEffect(bitmap) {
        if (bitmap == null) { isScanning = false; notFound = true; return@LaunchedEffect }
        isScanning = true
        notFound = false
        qrResult = null
        val result = withContext(Dispatchers.Default) {
            com.akslabs.circletosearch.utils.QrScanner.scanBitmap(bitmap)
        }
        isScanning = false
        if (result == null) notFound = true else qrResult = result
    }

    // Overall container
    val slideIn = slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 2 }
    val fadeIn = fadeIn(tween(400))

    AnimatedVisibility(
        visible = true,
        enter = slideIn + fadeIn
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "QR / Barcode",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Content area
                AnimatedContent(
                    targetState = when {
                        isScanning -> "scanning"
                        notFound -> "notfound"
                        else -> "result"
                    },
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "qrContent"
                ) { state ->
                    when (state) {
                        "scanning" -> ScanningIndicator(scanlineY)
                        "notfound" -> NotFoundContent()
                        else -> qrResult?.let { QrResultContent(context, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningIndicator(scanlineY: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            // Scanline effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = (80.dp * scanlineY) - 40.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.primary, Color.Transparent)
                        )
                    )
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Scanning for QR codes…", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotFoundContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔍", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("No QR code found", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Try circling the QR code more precisely",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun QrResultContent(context: Context, result: QrResult) {
    val slideIn = slideInVertically(tween(350, delayMillis = 50)) { it / 3 } + fadeIn(tween(300))
    AnimatedVisibility(visible = true, enter = slideIn) {
        when (result) {
            is QrResult.Url     -> UrlResult(context, result)
            is QrResult.WiFi    -> WifiResult(context, result)
            is QrResult.Phone   -> PhoneResult(context, result)
            is QrResult.Product -> ProductResult(context, result)
            is QrResult.VCard   -> VCardResult(context, result)
            is QrResult.GeoPoint -> GeoResult(context, result)
            is QrResult.PlainText -> PlainTextResult(context, result)
        }
    }
}

// ── Result types ────────────────────────────────────────────────────────────

@Composable
private fun UrlResult(context: Context, result: QrResult.Url) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ResultHeader("🔗", result.displayUrl.substringBefore("/"), result.displayUrl)
        Spacer(Modifier.height(16.dp))
        ActionRow {
            PrimaryAction("Open in Browser") { openUrl(context, result.url) }
            SecondaryAction("Copy Link") { copyToClipboard(context, "URL", result.url) }
        }
    }
}

@Composable
private fun WifiResult(context: Context, result: QrResult.WiFi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        Text(result.ssid, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text("${result.security} · Password: ${result.password ?: "None"}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        ActionRow {
            if (result.password != null) {
                SecondaryAction("Copy Password") { copyToClipboard(context, "WiFi Password", result.password) }
            }
        }
    }
}

@Composable
private fun PhoneResult(context: Context, result: QrResult.Phone) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        Text(result.number, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        ActionRow {
            PrimaryAction("Call") { openUrl(context, "tel:${result.number}") }
            SecondaryAction("Send SMS") { openUrl(context, "sms:${result.number}") }
            SecondaryAction("Copy") { copyToClipboard(context, "Phone", result.number) }
        }
    }
}

@Composable
private fun ProductResult(context: Context, result: QrResult.Product) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ShoppingBag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        Text(result.barcode, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        ActionRow {
            PrimaryAction("Search Amazon") {
                openUrl(context, "https://www.amazon.com/s?k=${result.barcode}")
            }
            SecondaryAction("Google") {
                openUrl(context, "https://www.google.com/search?q=${result.barcode}")
            }
            SecondaryAction("Copy") { copyToClipboard(context, "Barcode", result.barcode) }
        }
    }
}

@Composable
private fun VCardResult(context: Context, result: QrResult.VCard) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("👤", style = MaterialTheme.typography.headlineLarge)
        if (result.name != null) Text(result.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        val sub = listOfNotNull(result.phone, result.email).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        ActionRow {
            PrimaryAction("Save Contact") {
                val i = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    result.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                    result.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                    result.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
            }
            SecondaryAction("Copy") { copyToClipboard(context, "Contact", result.raw) }
        }
    }
}

@Composable
private fun GeoResult(context: Context, result: QrResult.GeoPoint) {
    val coordStr = "%.4f° N, %.4f° E".format(result.lat, result.lng)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        Text(coordStr, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        ActionRow {
            PrimaryAction("Open in Maps") {
                openUrl(context, "geo:${result.lat},${result.lng}?q=${result.lat},${result.lng}")
            }
            SecondaryAction("Copy") { copyToClipboard(context, "Coordinates", coordStr) }
        }
    }
}

@Composable
private fun PlainTextResult(context: Context, result: QrResult.PlainText) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📋", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "\"${result.text}\"",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        ActionRow {
            SecondaryAction("Copy") { copyToClipboard(context, "Text", result.text) }
            SecondaryAction("Search") {
                openUrl(context, "https://www.google.com/search?q=${Uri.encode(result.text)}")
            }
            SecondaryAction("Translate") {
                openUrl(context, "https://translate.google.com/?text=${Uri.encode(result.text)}")
            }
        }
    }
}

// ── Shared UI helpers ───────────────────────────────────────────────────────

@Composable
private fun ResultHeader(emoji: String, title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

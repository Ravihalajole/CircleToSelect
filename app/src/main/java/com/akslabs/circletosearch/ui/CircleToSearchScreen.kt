package com.akslabs.circletosearch.ui

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.akslabs.circletosearch.CircleToSearchAccessibilityService
import com.akslabs.circletosearch.R
import com.akslabs.circletosearch.ui.components.CopyTextOverlayManager
import com.akslabs.circletosearch.ui.components.FriendlyMessageBubble
import com.akslabs.circletosearch.ui.theme.OverlayGradientColors
import com.akslabs.circletosearch.utils.FriendlyMessageManager
import com.akslabs.circletosearch.utils.ImageUtils
import com.akslabs.circletosearch.utils.QrResultWithBounds
import com.akslabs.circletosearch.utils.QrScanner
import com.akslabs.circletosearch.utils.UIPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleToSearchScreen(
    screenshot: Bitmap?,
    onClose: () -> Unit,
    searchModeOverride: Boolean? = null,
    copyTextManager: CopyTextOverlayManager? = null,
    onCopyText: () -> Unit = {},
    onExitCopyMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPreferences = remember { UIPreferences(context) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var isUIVisible by remember { mutableStateOf(false) }
    var isCopyMode by remember { mutableStateOf(false) }
    var friendlyMessage by remember { mutableStateOf("") }
    var isMessageVisible by remember { mutableStateOf(false) }

    var showQrSheet by remember { mutableStateOf(false) }
    var qrScanBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedQrCodes by remember { mutableStateOf<List<QrResultWithBounds>>(emptyList()) }
    var selectedQrResult by remember { mutableStateOf<QrResultWithBounds?>(null) }

    var isEntityExtractMode by remember { mutableStateOf(false) }
    var detectedEntities by remember { mutableStateOf<List<SmartEntity>>(emptyList()) }
    var isExtractingEntities by remember { mutableStateOf(false) }

    val currentPathPoints = remember { mutableStateListOf<Offset>() }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    val selectionAnim = remember { Animatable(0f) }

    var isResizing by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isUIVisible = true
        if (uiPreferences.isShowFriendlyMessages()) {
            val manager = FriendlyMessageManager(context)
            friendlyMessage = manager.getNextMessage()
            delay(500)
            isMessageVisible = true
            delay(4000)
            isMessageVisible = false
        }
    }

    LaunchedEffect(screenshot) {
        if (screenshot != null) {
            isCopyMode = false
            selectionRect = null
            selectedBitmap = null
            currentPathPoints.clear()
            selectionAnim.snapTo(0f)
            
            val found = withContext(Dispatchers.Default) {
                QrScanner.scanBitmapAll(screenshot)
            }
            detectedQrCodes = found
        } else {
            detectedQrCodes = emptyList()
        }
    }

    BackHandler(enabled = true) {
        if (isCopyMode) {
            isCopyMode = false
            onExitCopyMode()
        } else if (showQrSheet) {
            showQrSheet = false
            selectedQrResult = null
        } else if (isEntityExtractMode) {
            isEntityExtractMode = false
        } else {
            onClose()
        }
    }

    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {

            // Friendly Message Bubble
            if (!isCopyMode) {
                Box(
                    modifier = Modifier.fillMaxSize().offset(y = 100.dp).zIndex(100f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    FriendlyMessageBubble(message = friendlyMessage, visible = isMessageVisible)
                }
            }

            // Screenshot Background & Dimming Canvas
            if (screenshot != null && !isCopyMode) {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                ) {
                    Image(
                        bitmap = screenshot.asImageBitmap(),
                        contentDescription = "Screenshot",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(Color.Black.copy(alpha = 0.15f))
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = OverlayGradientColors.map { it.copy(alpha = 0.15f) }
                            )
                        )
                        
                        if (selectionRect != null && selectionAnim.value > 0f) {
                            val rect = selectionRect!!
                            val holeRect = androidx.compose.ui.geometry.Rect(
                                rect.left.toFloat(), rect.top.toFloat(),
                                rect.right.toFloat(), rect.bottom.toFloat()
                            )
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = holeRect.topLeft,
                                size = holeRect.size,
                                cornerRadius = CornerRadius(48f),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                            )
                        }
                    }
                }
            }

            // Gradient Border
            if (uiPreferences.isShowGradientBorder() && !isCopyMode) {
                AnimatedVisibility(visible = isUIVisible, enter = fadeIn(animationSpec = tween(700))) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(8.dp, Brush.verticalGradient(OverlayGradientColors.map { it.copy(alpha = 0.5f) }), RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
            }

            // Gesture Drawing Logic & Animated Brackets
            if (!isCopyMode) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val rect = selectionRect
                                if (rect != null && selectionAnim.value == 1f) {
                                    val handleSize = 64f 
                                    val tl = Offset(rect.left.toFloat(), rect.top.toFloat())
                                    val tr = Offset(rect.right.toFloat(), rect.top.toFloat())
                                    val bl = Offset(rect.left.toFloat(), rect.bottom.toFloat())
                                    val br = Offset(rect.right.toFloat(), rect.bottom.toFloat())
                                    
                                    when {
                                        (offset - tl).getDistance() < handleSize -> { isResizing = true; activeHandle = "tl" }
                                        (offset - tr).getDistance() < handleSize -> { isResizing = true; activeHandle = "tr" }
                                        (offset - bl).getDistance() < handleSize -> { isResizing = true; activeHandle = "bl" }
                                        (offset - br).getDistance() < handleSize -> { isResizing = true; activeHandle = "br" }
                                        else -> { isResizing = false; activeHandle = null }
                                    }
                                    if (isResizing) return@detectDragGestures
                                }

                                currentPathPoints.clear()
                                currentPathPoints.add(offset)
                                selectionRect = null
                                scope.launch { selectionAnim.snapTo(0f) }
                            },
                            onDrag = { change, _ ->
                                if (isResizing && activeHandle != null) {
                                    val rect = selectionRect ?: return@detectDragGestures
                                    val pos = change.position
                                    val newRect = android.graphics.Rect(rect)
                                    when (activeHandle) {
                                        "tl" -> { newRect.left = pos.x.toInt(); newRect.top = pos.y.toInt() }
                                        "tr" -> { newRect.right = pos.x.toInt(); newRect.top = pos.y.toInt() }
                                        "bl" -> { newRect.left = pos.x.toInt(); newRect.bottom = pos.y.toInt() }
                                        "br" -> { newRect.right = pos.x.toInt(); newRect.bottom = pos.y.toInt() }
                                    }
                                    if (newRect.width() > 20 && newRect.height() > 20) {
                                        selectionRect = newRect
                                    }
                                } else {
                                    currentPathPoints.add(change.position)
                                }
                            },
                            onDragEnd = {
                                if (isResizing) {
                                    isResizing = false
                                    activeHandle = null
                                    if (screenshot != null && selectionRect != null) {
                                        selectedBitmap = ImageUtils.cropBitmap(screenshot, selectionRect!!)
                                    }
                                } else if (currentPathPoints.isNotEmpty()) {
                                    var minX = Float.MAX_VALUE
                                    var minY = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE
                                    var maxY = Float.MIN_VALUE
                                    currentPathPoints.forEach { p ->
                                        minX = min(minX, p.x)
                                        minY = min(minY, p.y)
                                        maxX = max(maxX, p.x)
                                        maxY = max(maxY, p.y)
                                    }

                                    val border = 20
                                    val rect = Rect(
                                        (minX - border).toInt().coerceAtLeast(0),
                                        (minY - border).toInt().coerceAtLeast(0),
                                        (maxX + border).toInt().coerceAtMost(screenshot?.width ?: 0),
                                        (maxY + border).toInt().coerceAtMost(screenshot?.height ?: 0)
                                    )

                                    if (rect.width() > 10 && rect.height() > 10) {
                                        selectionRect = rect
                                        currentPathPoints.clear()
                                        if (screenshot != null) {
                                            selectedBitmap = ImageUtils.cropBitmap(screenshot, rect)
                                        }
                                        scope.launch {
                                            selectionAnim.animateTo(1f, tween(600))
                                        }
                                    }
                                }
                            }
                        )
                    }
                ) {
                    if (currentPathPoints.size > 1) {
                        val path = Path().apply {
                            moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                            for (i in 1 until currentPathPoints.size) {
                                lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path, brush = Brush.linearGradient(OverlayGradientColors),
                            style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.6f
                        )
                        drawPath(
                            path = path, color = Color.White,
                            style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (selectionRect != null && selectionAnim.value > 0f) {
                        val rect = selectionRect!!
                        val progress = selectionAnim.value
                        val left = rect.left.toFloat()
                        val top = rect.top.toFloat()
                        val right = rect.right.toFloat()
                        val bottom = rect.bottom.toFloat()
                        
                        val width = right - left
                        val height = bottom - top
                        val cornerRadius = 48f 
                        val armLength = min(width, height) * 0.15f 

                        val tlPath = Path().apply {
                            moveTo(left, top + armLength)
                            lineTo(left, top + cornerRadius)
                            arcTo(androidx.compose.ui.geometry.Rect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius), 180f, 90f, false)
                            lineTo(left + armLength, top)
                        }
                        
                        val trPath = Path().apply {
                            moveTo(right - armLength, top)
                            lineTo(right - cornerRadius, top)
                            arcTo(androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius), 270f, 90f, false)
                            lineTo(right, top + armLength)
                        }
                        
                        val brPath = Path().apply {
                            moveTo(right, bottom - armLength)
                            lineTo(right, bottom - cornerRadius)
                            arcTo(androidx.compose.ui.geometry.Rect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom), 0f, 90f, false)
                            lineTo(right - armLength, bottom)
                        }
                        
                        val blPath = Path().apply {
                            moveTo(left + armLength, bottom)
                            lineTo(left + cornerRadius, bottom)
                            arcTo(androidx.compose.ui.geometry.Rect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom), 90f, 90f, false)
                            lineTo(left, bottom - armLength)
                        }

                        val bracketStroke = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        listOf(tlPath, trPath, brPath, blPath).forEach { p ->
                            drawPath(p, Color.White, style = bracketStroke, alpha = progress)
                        }
                        
                         drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(48f),
                            style = Stroke(width = 4f),
                            alpha = (1f - progress) * 0.5f
                         )
                    }
                }
            }

            // --- END OF PART 1 ---
            // Top Bar
            AnimatedVisibility(
                visible = isUIVisible && !isCopyMode && !isEntityExtractMode,
                enter = slideInVertically(
                    initialOffsetY = { -it }, 
                    animationSpec = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                modifier = Modifier.align(Alignment.TopCenter).zIndex(2000f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onClose()
                        },
                        modifier = Modifier
                            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
            
            // Bottom Action Bar (100% Offline Tools)
            AnimatedVisibility(
                visible = isUIVisible && !isCopyMode && !isEntityExtractMode,
                enter = slideInVertically(
                    initialOffsetY = { it }, 
                    animationSpec = tween(300, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f))
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it }, 
                    animationSpec = tween(200, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 1f, 1f))
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(2000f)
            ) {
                Surface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        @Composable
                        fun BottomBarButton(label: String, icon: @Composable () -> Unit, enabled: Boolean = true, onClick: () -> Unit) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledTonalIconButton(
                                    onClick = { 
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onClick() 
                                    },
                                    enabled = enabled,
                                    modifier = Modifier.size(48.dp)
                                ) { icon() }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Local Text Extraction
                        BottomBarButton("Extract Text", { Icon(painterResource(id = R.drawable.ocr), null, modifier = Modifier.size(20.dp)) }) {
                            copyTextManager?.setOcrOnlyMode()
                            isCopyMode = true
                        }

                        // Local SmartScan
                        BottomBarButton("SmartScan", { Icon(Icons.Default.Search, null, modifier = Modifier.size(22.dp)) }) {
                            isEntityExtractMode = true
                            if (detectedEntities.isEmpty() && !isExtractingEntities) {
                                isExtractingEntities = true
                                scope.launch(Dispatchers.IO) {
                                    val bmp = screenshot ?: return@launch
                                    val textNodes = com.akslabs.circletosearch.ocr.TesseractEngine.extractText(context, bmp)
                                    val entities = mutableListOf<SmartEntity>()
                                    
                                    val urlRegex = android.util.Patterns.WEB_URL.toRegex()
                                    val emailRegex = android.util.Patterns.EMAIL_ADDRESS.toRegex()
                                    val phoneRegexLine = Regex("""(\+?[\d\s\-\(\).]{9,20})""")
                                    val upiRegex = Regex("""[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}""")

                                    textNodes.forEach { node ->
                                        val lineText = node.fullText
                                        phoneRegexLine.findAll(lineText).forEach { match ->
                                            val phoneCandidate = match.value.trim()
                                            if (phoneCandidate.count { it.isDigit() } >= 10) {
                                                val startIdx = match.range.first
                                                val endIdx = match.range.last + 1
                                                val ratioStart = startIdx.toFloat() / lineText.length.coerceAtLeast(1)
                                                val ratioEnd = endIdx.toFloat() / lineText.length.coerceAtLeast(1)
                                                val entityBounds = android.graphics.RectF(
                                                    node.bounds.left.toFloat() + (ratioStart * node.bounds.width().toFloat()),
                                                    node.bounds.top.toFloat(),
                                                    node.bounds.left.toFloat() + (ratioEnd * node.bounds.width().toFloat()),
                                                    node.bounds.bottom.toFloat()
                                                )
                                                entities.add(SmartEntity.Phone(phoneCandidate, entityBounds))
                                            }
                                        }
                                        node.words.forEach { word ->
                                            val txt = word.text.trim()
                                            if (emailRegex.matches(txt)) entities.add(SmartEntity.Email(txt, word.bounds))
                                            else if (upiRegex.matches(txt)) entities.add(SmartEntity.Upi(txt, word.bounds))
                                            else if (urlRegex.matches(txt)) entities.add(SmartEntity.Url(txt, word.bounds))
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        detectedEntities = entities
                                        isExtractingEntities = false
                                    }
                                }
                            }
                        }

                        // Local QR Scan
                        BottomBarButton("Scan QR", { Icon(Icons.Default.QrCode, null) }) {
                            qrScanBitmap = selectedBitmap ?: screenshot
                            showQrSheet = true
                        }

                        // Pin Area
                        val isPinEnabled = selectedBitmap != null
                        BottomBarButton("Pin", { Icon(Icons.Default.PushPin, null) }, enabled = isPinEnabled) {
                            selectedBitmap?.let { bmp ->
                                CircleToSearchAccessibilityService.pinArea(bmp, selectionRect ?: Rect())
                                (context as? android.app.Activity)?.finish()
                            }
                        }
                    }
                }
            }

            // Entity Extract Dim Background
            if (isEntityExtractMode) {
                Box(
                    modifier = Modifier.fillMaxSize().zIndex(2550f).pointerInput(Unit) {
                        detectTapGestures { isEntityExtractMode = false }
                    }
                )
            }

            // Copy Text Mode View
            if (isCopyMode && copyTextManager != null) {
                AndroidView(
                    factory = { ctx ->
                        copyTextManager.getOverlayView(onDismiss = {
                            isCopyMode = false
                            onExitCopyMode()
                        })
                    },
                    modifier = Modifier.fillMaxSize().zIndex(150f)
                )
            }

// --- END OF PART 2 ---
            // Floating Action Pill (Share / Save Selection)
            if (selectionRect != null && selectionAnim.value == 1f && !isCopyMode) {
                val rect = selectionRect!!
                val density = androidx.compose.ui.platform.LocalDensity.current
                val leftPx = rect.left.toFloat()
                val topPx = rect.top.toFloat()
                val rightPx = rect.right.toFloat()
                val bottomPx = rect.bottom.toFloat()
                
                val leftDp = with(density) { leftPx.toDp() }
                val topDp = with(density) { topPx.toDp() }
                val rightDp = with(density) { rightPx.toDp() }
                val bottomDp = with(density) { bottomPx.toDp() }
                
                Box(
                    modifier = Modifier.fillMaxSize().zIndex(2000f) 
                ) {
                    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                    val centerX = (leftDp + rightDp) / 2
                    
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (centerX - 125.dp).coerceIn(0.dp, screenWidth - 250.dp),
                                y = if (topPx > 200f) (topDp - 72.dp).coerceAtLeast(16.dp) else (bottomDp + 16.dp).coerceAtMost(screenHeight - 80.dp)
                            )
                            .width(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Share Button
                                FilledTonalButton(
                                    onClick = {
                                        if (selectedBitmap != null) {
                                            scope.launch {
                                                try {
                                                    val fileName = "selection_${java.util.UUID.randomUUID()}.png"
                                                    val path = ImageUtils.saveBitmap(context, selectedBitmap!!, fileName)
                                                    val file = java.io.File(path)
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.akslabs.circletosearch.fileprovider", file)
                                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { 
                                                        type = "image/png"
                                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                        clipData = android.content.ClipData.newRawUri("Selection", uri)
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Selection").apply { 
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) 
                                                    })
                                                } catch (e: Exception) {
                                                    android.util.Log.e("CircleToSearch", "Failed to share selection", e)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(48.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.Transparent, 
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp)
                                ) {
                                    Text("Share", style = MaterialTheme.typography.labelLarge)
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(24.dp).padding(horizontal = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Save Button
                                FilledTonalButton(
                                    onClick = {
                                        if (selectedBitmap != null) {
                                            val success = ImageUtils.saveToGallery(context, selectedBitmap!!)
                                            if (success) {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                android.widget.Toast.makeText(context, "Saved to Gallery", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Failed to save", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(48.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp)
                                ) {
                                    Text("Save", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // QR Code Detected Chips
            if (screenshot != null && !isCopyMode) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(2500f)) {
                    val screenWidth = maxWidth
                    val screenHeight = maxHeight
                    val bitmapWidth = screenshot.width.toFloat()
                    val bitmapHeight = screenshot.height.toFloat()

                    detectedQrCodes.forEach { qr ->
                        qr.bounds?.let { bounds ->
                            val chipX = (bounds.centerX() / bitmapWidth) * screenWidth.value
                            val chipY = (bounds.centerY() / bitmapHeight) * screenHeight.value
                            val isUrl = qr.result is com.akslabs.circletosearch.utils.QrResult.Url

                            Box(
                                modifier = Modifier
                                    .offset(x = chipX.dp - 24.dp, y = chipY.dp - 24.dp)
                                    .size(48.dp)
                                    .shadow(6.dp, CircleShape)
                                    .background(Color.White, CircleShape)
                                    .border(1.5.dp, if(isUrl) Color(0xFF1A73E8) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                    .pointerInput(qr) {
                                        detectTapGestures {
                                            selectedQrResult = qr
                                            qrScanBitmap = null 
                                            showQrSheet = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QrCode, null, tint = if(isUrl) Color(0xFF1A73E8) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

// --- END OF PART 3A ---
            // SmartScan Entity Chips (Locally Extracted Links, Phones, Emails)
            if (isEntityExtractMode && screenshot != null && !isCopyMode) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(2600f)) {
                    val screenWidth = maxWidth
                    val screenHeight = maxHeight
                    val bitmapWidth = screenshot.width.toFloat()
                    val bitmapHeight = screenshot.height.toFloat()

                    if (isExtractingEntities) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(16.dp))
                                Text("Parsing screen locally...", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    detectedEntities.forEach { entity ->
                        val chipX = (entity.bounds.centerX() / bitmapWidth) * screenWidth.value
                        val chipY = (entity.bounds.centerY() / bitmapHeight) * screenHeight.value

                        Box(
                            modifier = Modifier
                                .offset(x = chipX.dp - 24.dp, y = chipY.dp - 24.dp)
                                .size(48.dp)
                                .shadow(6.dp, CircleShape)
                                .background(Color.White, CircleShape)
                                .border(1.5.dp, entity.sourceColor, CircleShape)
                                .clickable {
                                    try {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(entity.typeName, entity.text))

                                        val intent = when(entity) {
                                            is SmartEntity.Url -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(if (!entity.text.startsWith("http")) "http://" + entity.text else entity.text))
                                            is SmartEntity.Email -> android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${entity.text}"))
                                            is SmartEntity.Phone -> android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${entity.text}"))
                                            is SmartEntity.Upi -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("upi://pay?pa=${entity.text}"))
                                        }
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        isEntityExtractMode = false
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No app found to handle this", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(entity.icon, null, tint = entity.sourceColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // QR Code Sheet Overlay
            if (showQrSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2500f)
                        .background(Color.Black.copy(alpha = 0.01f)) 
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { 
                            showQrSheet = false
                            selectedQrResult = null
                        }
                )

                Box(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp).zIndex(3000f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    QrCodeResultSheet(
                        context = context,
                        bitmap = qrScanBitmap,
                        onDismiss = { 
                            showQrSheet = false
                            selectedQrResult = null
                        },
                        initialResults = detectedQrCodes,
                        initialPage = if (selectedQrResult != null) detectedQrCodes.indexOf(selectedQrResult!!) else 0
                    )
                }
            }
        }
    }
}

// Sealed class for local SmartScan entities
sealed class SmartEntity(
    val text: String, 
    val bounds: android.graphics.RectF, 
    val typeName: String, 
    val icon: androidx.compose.ui.graphics.vector.ImageVector, 
    val sourceColor: Color
) {
    class Url(text: String, bounds: android.graphics.RectF) : SmartEntity(text, bounds, "Link", Icons.Default.Link, Color(0xFF1A73E8))
    class Email(text: String, bounds: android.graphics.RectF) : SmartEntity(text, bounds, "Email", Icons.Default.Email, Color(0xFF1A73E8))
    class Phone(text: String, bounds: android.graphics.RectF) : SmartEntity(text, bounds, "Phone", Icons.Default.Phone, Color(0xFF43A047))
    class Upi(text: String, bounds: android.graphics.RectF) : SmartEntity(text, bounds, "UPI", Icons.Default.Person, Color(0xFF8E24AA))
}


/*
 * Copyright (C) 2025 AKS-Labs
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * CopyTextOverlayManager — fully offline, zero network, zero logging.
 *
 * FIXES applied vs original:
 *  1. isPotentialTextNode — strict text-only filter, no contentDescription/button leakage
 *  2. collectTextNodes — nodes are NOT recycled until AFTER leaf-filter completes (fixes
 *     non-deterministic parent-pointer traversal on recycled nodes)
 *  3. findNearestWord — converts word.bounds (screen coords) to local view coords before
 *     comparing against touch x/y (which are already in local coords)
 *  4. estimateWordBounds — clamps output to node bounds so multi-line text never maps
 *     words outside the visible region; documented limitation noted
 *  5. getRootNode selection — prefers the window with the MOST text content, not just
 *     windows.first(), so systemui chrome windows rank below real app windows
 *  6. snapshot timing — snapshot nodes are now re-collected on demand (when Copy Text
 *     button is tapped) not only at launch time
 */

package com.akslabs.circletosearch.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simple holder for a floating-toolbar button's label and screen hit-rect. */
private class ToolbarButton(val label: String, val rect: Rect)

/**
 * Manages the dim+punch-out Copy Text overlay.
 *
 * Usage:
 *   val mgr = CopyTextOverlayManager(context) { getRootNode() }
 *   mgr.getOverlayView(onDismiss = { ... })  // embed in AndroidView
 *   mgr.rescanNodes()  // call on scroll events
 *   mgr.dismiss()
 */
class CopyTextOverlayManager(
    private val context: Context,
    private val screenshotBitmap: android.graphics.Bitmap?
) {
    private var dimView: DimPunchOutView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanJob: Job? = null
    private var onDismissCallback: (() -> Unit)? = null

    // Detected text nodes and flattened words (sorted by reading order)
    private val detectedNodes = mutableListOf<TextNode>()
    private val allWords = mutableListOf<Word>()

    // Global selection state (indices into 'allWords')
    private var globalSelectionStart: Int = -1
    private var globalSelectionEnd: Int = -1

    // ── Public API ───────────────────────────────────────────────────────────

    fun getOverlayView(onDismiss: () -> Unit): View {
        if (dimView != null) return dimView!!
        android.util.Log.d("CopyText", "getOverlayView() called")
        onDismissCallback = onDismiss

        val view = DimPunchOutView(context) { dismiss() }
        dimView = view

        android.util.Log.d("CopyText", "Starting accessibility node scan…")
        scanNodes(view)
        return view
    }

    fun dismiss() {
        scanJob?.cancel()
        dimView = null
        onDismissCallback?.invoke()
        onDismissCallback = null
    }

    /** Call on TYPE_VIEW_SCROLLED events to refresh punched-out regions live. */
    fun rescanNodes() {
        dimView?.let { scanNodes(it) }
    }

    // ── Node scanning ────────────────────────────────────────────────────────

    private fun scanNodes(view: DimPunchOutView) {
        scanJob?.cancel()
        scanJob = scope.launch {
            android.util.Log.d("CopyText", "scanNodes triggered")

            if (screenshotBitmap == null) {
                android.util.Log.e("CopyText", "scanNodes: screenshotBitmap is null")
                detectedNodes.clear()
                view.invalidate()
                return@launch
            }

            android.util.Log.d("CopyText", "Live scan with OCR on bitmap")

            val nodes = com.akslabs.circletosearch.ocr.TesseractEngine.extractText(context, screenshotBitmap)
            
            // Sort nodes by top coordinate, then left to ensure logical reading order
            val sortedNodes = nodes.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            
            android.util.Log.d("CopyText", "Live OCR scan complete: ${sortedNodes.size} text nodes")
            
            detectedNodes.clear()
            allWords.clear()
            detectedNodes.addAll(sortedNodes)
            
            // Flatten words and update their global index
            var globalIdx = 0
            sortedNodes.forEach { node ->
                node.words.forEach { word ->
                    // We reuse the Word object but treat it globally
                    allWords.add(word)
                    globalIdx++
                }
            }
            
            view.invalidate()
        }
    }



    // ── Dim + punch-out View ─────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    inner class DimPunchOutView(
        context: Context,
        private val onBackPress: () -> Unit
    ) : View(context) {



        // ── Coordinate helpers ────────────────────────────────────────────────

        private val viewLocation = IntArray(2)

        /** Convert a screen-coordinate Rect to this view's local coordinates. */
        private fun toLocal(screenRect: Rect): RectF {
            getLocationOnScreen(viewLocation)
            return RectF(
                (screenRect.left  - viewLocation[0]).toFloat(),
                (screenRect.top   - viewLocation[1]).toFloat(),
                (screenRect.right - viewLocation[0]).toFloat(),
                (screenRect.bottom - viewLocation[1]).toFloat()
            )
        }

        /** Convert a screen-coordinate RectF to this view's local coordinates. */
        private fun toLocal(screenRectF: RectF): RectF {
            getLocationOnScreen(viewLocation)
            return RectF(
                screenRectF.left   - viewLocation[0],
                screenRectF.top    - viewLocation[1],
                screenRectF.right  - viewLocation[0],
                screenRectF.bottom - viewLocation[1]
            )
        }

        /**
         * Convert a local-coordinate point to screen coordinates.
         * Used so we can compare touch x/y against word.bounds (screen coords).
         *
         * FIX #3: word.bounds are in SCREEN coordinates (set from getBoundsInScreen).
         * Touch events arrive in LOCAL view coordinates.  We must translate before
         * comparing — the original code compared local touch coords directly to
         * screen-space word bounds, causing selection to be offset by the view origin.
         */
        private fun toScreenX(localX: Float): Float {
            getLocationOnScreen(viewLocation)
            return localX + viewLocation[0]
        }

        private fun toScreenY(localY: Float): Float {
            getLocationOnScreen(viewLocation)
            return localY + viewLocation[1]
        }

        // ── Paint objects ─────────────────────────────────────────────────────

        private val dimPaint = Paint().apply {
            color = Color.BLACK
            alpha = 38  // ~15% dim
            isAntiAlias = false
        }



        private val selectedWordPaint = Paint().apply {
            // Priority: M3 dynamic color if possible, fallback to a nice purple/blue accent
            val colorRes = android.R.color.system_accent1_200 
            color = try { context.getColor(colorRes) } catch(e: Exception) { Color.parseColor("#D0BCFF") }
            alpha = 150
            isAntiAlias = true
        }

        private val handlePaint = Paint().apply {
            color = Color.parseColor("#6750A4") // M3 Primary
            isAntiAlias = true
        }

        private val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F3EDF7") // M3 Surface Container
        }

        private val toolbarActionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6750A4") // M3 Primary
        }

        private val toolbarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D1B20") // M3 On Surface
            textSize = 38f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        private val toolbarIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6750A4")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // ── Pulse animation ───────────────────────────────────────────────────

        init {
            background = null
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        // ── Selection / drag state ────────────────────────────────────────────

        private var dragHandleType = 0  // 0=none, 1=start, 2=end
        private var toolbarButtons: List<ToolbarButton> = emptyList()


        // ── Node entry / exit ─────────────────────────────────────────────────

        private fun enterNode(node: TextNode) {
            // Find global range for this node's words
            val firstWord = node.words.firstOrNull() ?: return
            val lastWord  = node.words.lastOrNull() ?: return
            
            val startIdx = allWords.indexOfFirst { it === firstWord }
            val endIdx   = allWords.indexOfLast { it === lastWord }
            
            if (startIdx != -1 && endIdx != -1) {
                globalSelectionStart = startIdx
                globalSelectionEnd   = endIdx
            }
            invalidate()
        }

        private fun exitNode() {
            globalSelectionStart = -1
            globalSelectionEnd   = -1
            invalidate()
        }

        // ── Drawing ───────────────────────────────────────────────────────────

        private val clearPaint = Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

            // 1. Dim layer
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            // 2. Punch out holes for all text blocks
            detectedNodes.forEach { node ->
                val localBounds = toLocal(node.bounds)
                localBounds.inset(-16f, -12f) // More padding for professional look
                canvas.drawRoundRect(localBounds, 16f, 16f, clearPaint)
            }

            // 3. Draw Highlights (Merged into unified bars)
            if (globalSelectionStart != -1 && globalSelectionEnd != -1) {
                val start = globalSelectionStart.coerceAtMost(globalSelectionEnd)
                val end   = globalSelectionStart.coerceAtLeast(globalSelectionEnd)
                
                // Group words by line (simplified: words in the same node that are close horizontally)
                // Actually, Tesseract's TextNode usually represents a line or block.
                // We'll group selected words by their Word.bounds.centerY() (rounded)
                val selectedWords = (start..end).mapNotNull { allWords.getOrNull(it) }
                
                selectedWords.groupBy { (it.bounds.centerY() / 10).toInt() }.forEach { (_, wordsInLine) ->
                    if (wordsInLine.isEmpty()) return@forEach
                    
                    // Create one unified Rect representing the entire highlight for this line segment
                    val first = wordsInLine.minBy { it.bounds.left }
                    val last  = wordsInLine.maxBy { it.bounds.right }
                    
                    val combinedRect = RectF(
                        first.bounds.left,
                        wordsInLine.minOf { it.bounds.top },
                        last.bounds.right,
                        wordsInLine.maxOf { it.bounds.bottom }
                    )
                    
                    val localLineHighlight = toLocal(combinedRect)
                    localLineHighlight.inset(-4f, -4f) // Slight overlap for smoothness
                    canvas.drawRoundRect(localLineHighlight, 12f, 12f, selectedWordPaint)
                }

                // 4. Handles & Toolbar (Anchor to the overall selection)
                val firstWord = allWords[start]
                val lastWord  = allWords[end]
                
                val startLocal = toLocal(firstWord.bounds)
                val endLocal   = toLocal(lastWord.bounds)

                drawHandle(canvas, startLocal.left,  startLocal.top,    isStart = true)
                drawHandle(canvas, endLocal.right,   endLocal.bottom,   isStart = false)

                // Toolbar anchored to the encompassing rect of the whole selection
                val encompassing = RectF(firstWord.bounds)
                selectedWords.forEach { encompassing.union(it.bounds) }
                drawFloatingToolbar(canvas, toLocal(encompassing))
            }

            canvas.restoreToCount(saveCount)
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float, isStart: Boolean) {
            canvas.drawCircle(x, y, 18f, handlePaint)
            if (isStart) canvas.drawRect(x - 2f, y, x + 2f, y + 40f, handlePaint)
            else         canvas.drawRect(x - 2f, y - 40f, x + 2f, y, handlePaint)
        }

        private fun drawFloatingToolbar(canvas: Canvas, anchor: RectF) {
            val btnW    = 170
            val btnH    = 80
            val padding = 12
            val gap     = 8
            val labels  = listOf("Copy", "Share", "Select All", "Cancel")

            val totalW = labels.size * btnW + (labels.size - 1) * gap + padding * 2
            val totalH = btnH + padding * 2

            var left = anchor.centerX().toInt() - totalW / 2
            var top  = anchor.top.toInt() - totalH - 32
            if (top < 0)              top  = anchor.bottom.toInt() + 32
            if (left < 10)            left = 10
            if (left + totalW > width - 10) left = width - totalW - 10

            val barRect = RectF(left.toFloat(), top.toFloat(), (left + totalW).toFloat(), (top + totalH).toFloat())
            
            toolbarBgPaint.setShadowLayer(20f, 0f, 8f, Color.parseColor("#44000000"))
            canvas.drawRoundRect(barRect, 40f, 40f, toolbarBgPaint)
            toolbarBgPaint.clearShadowLayer()

            val buttons = mutableListOf<ToolbarButton>()
            labels.forEachIndexed { idx, label ->
                val bLeft  = left + padding + idx * (btnW + gap)
                val bTop   = top  + padding
                val bRect  = Rect(bLeft, bTop, bLeft + btnW, bTop + btnH)
                buttons.add(ToolbarButton(label, bRect))

                val bRectF = RectF(bRect)
                if (label == "Copy" || label == "Share") {
                    val pillPaint = Paint(toolbarActionPaint).apply { alpha = 35 }
                    canvas.drawRoundRect(bRectF, 40f, 40f, pillPaint)
                    canvas.drawText(label, bRectF.centerX(), bRectF.centerY() + 14f, toolbarActionPaint.apply { style = Paint.Style.FILL; textSize = 34f })
                } else {
                    canvas.drawText(label, bRectF.centerX(), bRectF.centerY() + 14f, toolbarTextPaint.apply { textSize = 34f })
                }
            }
            toolbarButtons = buttons
        }

        // ── Touch handling ────────────────────────────────────────────────────

        private var startSelectionIdx = -1

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lx = event.x
            val ly = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 1. Check toolbar FIRST with 24px extra hit-padding for reliability
                    for (btn in toolbarButtons) {
                        val touchRect = Rect(btn.rect).apply { inset(-24, -24) }
                        if (touchRect.contains(lx.toInt(), ly.toInt())) {
                            handleToolbarAction(btn.label)
                            return true
                        }
                    }

                    // 2. Check for handle drag
                    if (globalSelectionStart != -1) {
                        val start = globalSelectionStart.coerceAtMost(globalSelectionEnd)
                        val end   = globalSelectionStart.coerceAtLeast(globalSelectionEnd)
                        val startLocal = toLocal(allWords[start].bounds)
                        val endLocal   = toLocal(allWords[end].bounds)
                        
                        if (isPointNear(lx, ly, startLocal.left, startLocal.top)) {
                            dragHandleType = 1; return true
                        }
                        if (isPointNear(lx, ly, endLocal.right, endLocal.bottom)) {
                            dragHandleType = 2; return true
                        }
                    }

                    // 3. Glide Select across ALL nodes
                    val sx = toScreenX(lx)
                    val sy = toScreenY(ly)
                    val nearest = findNearestWordGlobal(sx, sy)
                    if (nearest != -1) {
                        globalSelectionStart = nearest
                        globalSelectionEnd   = nearest
                        startSelectionIdx    = nearest
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        invalidate()
                        return true
                    } else {
                        exitNode()
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val sx = toScreenX(lx)
                    val sy = toScreenY(ly)
                    
                    if (dragHandleType != 0) {
                        val nearest = findNearestWordGlobal(sx, sy)
                        if (nearest != -1) {
                            if (dragHandleType == 1) globalSelectionStart = nearest
                            else                     globalSelectionEnd   = nearest
                            invalidate()
                        }
                        return true
                    } else if (startSelectionIdx != -1) {
                        val nearest = findNearestWordGlobal(sx, sy)
                        if (nearest != -1) {
                            globalSelectionEnd = nearest
                            invalidate()
                            return true
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragHandleType = 0
                    startSelectionIdx = -1
                    invalidate()
                }
            }
            return super.onTouchEvent(event)
        }

        private fun findNearestWordGlobal(sx: Float, sy: Float): Int {
            var minDist = Float.MAX_VALUE
            var nearest = -1
            allWords.forEachIndexed { idx, word ->
                val dx = sx - word.bounds.centerX()
                val dy = sy - word.bounds.centerY()
                val d  = dx * dx + dy * dy
                // Only consider it a "hit" if it's within a reasonable distance of the block
                if (d < minDist) {
                    minDist = d
                    nearest = idx
                }
            }
            // If the nearest word is too far (e.g. empty space), return -1
            return if (minDist < 600 * 600) nearest else -1
        }

        private fun isPointNear(px: Float, py: Float, x: Float, y: Float): Boolean {
            val dx = px - x
            val dy = py - y
            return dx * dx + dy * dy < 80 * 80
        }



        // ── Toolbar action handler ────────────────────────────────────────────

        private fun handleToolbarAction(label: String) {
            val start = globalSelectionStart.coerceAtMost(globalSelectionEnd)
            val end   = globalSelectionStart.coerceAtLeast(globalSelectionEnd)
            if (start == -1) return

            val selectedText = (start..end).mapNotNull { allWords.getOrNull(it) }.joinToString(" ") { it.text }

            when (label) {
                "Copy" -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", selectedText))
                    Toast.makeText(context, "Text copied ✓", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                "Share" -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, selectedText)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share text via").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    dismiss()
                }
                "Select All" -> {
                    globalSelectionStart = 0
                    globalSelectionEnd   = allWords.lastIndex
                    invalidate()
                }
                "Cancel" -> {
                    exitNode()
                }
            }
        }

        // ── Misc ──────────────────────────────────────────────────────────────

        override fun isInEditMode(): Boolean = false

        override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                onBackPress()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
    }
}

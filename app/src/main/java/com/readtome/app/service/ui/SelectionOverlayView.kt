package com.readtome.app.service.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Camada fullscreen que gerencia a área de leitura da tela.
 * - Por padrão, seleciona a tela toda ao ser acionada.
 * - Delimita a área com linhas tracejadas ciano neon de alto contraste.
 * - Permite que o usuário redesenhe a área arrastando o dedo a qualquer momento.
 * - Oferece botões rápidos de "Ouvir Agora", "Tela Toda" e "Fechar".
 */
@SuppressLint("ViewConstructor")
class SelectionOverlayView(
    context: Context,
    private val onAreaSelected: (Rect) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    val windowLayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        gravity = Gravity.TOP or Gravity.START
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    private enum class TouchTarget {
        NONE,
        BUTTON_PLAY,
        BUTTON_RESET,
        BUTTON_CLOSE,
        SELECTION_CANVAS
    }

    private var activeTouchTarget = TouchTarget.NONE
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var isCustomSelection = false
    private var hasTriggered = false

    private val selectionRect = RectF()

    // Botões interativos desenhados no Canvas
    private val bannerRect = RectF()
    private val btnPlayRect = RectF()
    private val btnResetRect = RectF()
    private val btnCloseRect = RectF()

    // Tintas de desenho
    private val scrimPaint = Paint().apply {
        color = 0x88000000.toInt() // Escurecimento suave do fundo não selecionado
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Linha tracejada neon ciano
    private val dashedBorderPaint = Paint().apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(28f, 16f), 0f)
    }

    // Linha escura de fundo para garantir contraste do tracejado em qualquer tela
    private val dashedShadowPaint = Paint().apply {
        color = 0xCC000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 9f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(28f, 16f), 0f)
    }

    // Cantos destacados (L-brackets nos vértices)
    private val cornerBracketPaint = Paint().apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    // Textos e HUD
    private val pillBackgroundPaint = Paint().apply {
        color = 0xDD0F172A.toInt() // Dark Slate translúcido
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pillBorderPaint = Paint().apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val buttonPlayPaint = Paint().apply {
        color = 0xFF2563EB.toInt() // Azul Royal / Ação principal
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val buttonResetPaint = Paint().apply {
        color = 0xFF1E293B.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val buttonBorderPaint = Paint().apply {
        color = 0xFF475569.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(8f, 0f, 4f, 0xFF000000.toInt())
    }

    private val buttonTextPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val closeIconPaint = Paint().apply {
        color = 0xFFEF4444.toInt()
        textSize = 46f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (!isCustomSelection) {
                // Seleciona a tela toda por padrão (com margem de 16px para destacar a linha tracejada)
                val margin = 16f
                selectionRect.set(margin, margin, w - margin, h - margin)
            }
            layoutButtons(w.toFloat(), h.toFloat())
        }
    }

    private fun layoutButtons(w: Float, h: Float) {
        if (w <= 0 || h <= 0) return

        val bannerWidth = min(w - 60f, 960f)
        val bannerHeight = 110f
        val bannerLeft = (w - bannerWidth) / 2f
        val bannerTop = 80f
        bannerRect.set(bannerLeft, bannerTop, bannerLeft + bannerWidth, bannerTop + bannerHeight)

        val closeSize = 70f
        btnCloseRect.set(bannerRect.right - closeSize - 15f, bannerTop + 20f, bannerRect.right - 15f, bannerTop + 20f + closeSize)

        val bottomMargin = 160f
        val btnPlayWidth = 420f
        val btnPlayHeight = 120f
        val btnResetWidth = if (isCustomSelection) 320f else 0f
        val spacing = 20f
        val totalActionWidth = btnPlayWidth + (if (isCustomSelection) btnResetWidth + spacing else 0f)

        val actionsLeft = (w - totalActionWidth) / 2f
        val actionsTop = h - bottomMargin - btnPlayHeight

        if (isCustomSelection) {
            btnResetRect.set(actionsLeft, actionsTop, actionsLeft + btnResetWidth, actionsTop + btnPlayHeight)
            val playLeft = btnResetRect.right + spacing
            btnPlayRect.set(playLeft, actionsTop, playLeft + btnPlayWidth, actionsTop + btnPlayHeight)
        } else {
            btnResetRect.setEmpty()
            btnPlayRect.set(actionsLeft, actionsTop, actionsLeft + btnPlayWidth, actionsTop + btnPlayHeight)
        }
    }

    private fun isPointInside(rect: RectF, x: Float, y: Float, margin: Float = 24f): Boolean {
        if (rect.isEmpty) return false
        return x >= rect.left - margin && x <= rect.right + margin &&
               y >= rect.top - margin && y <= rect.bottom + margin
    }

    private fun resetToFullScreen() {
        val margin = 16f
        selectionRect.set(margin, margin, width - margin, height - margin)
        isCustomSelection = false
        layoutButtons(width.toFloat(), height.toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (selectionRect.isEmpty && w > 0 && h > 0) {
            val margin = 16f
            selectionRect.set(margin, margin, w - margin, h - margin)
        }
        layoutButtons(w, h)

        // 1. Desenha o escurecimento do fundo
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // 2. Abre a janela transparente da área selecionada
        if (!selectionRect.isEmpty) {
            canvas.drawRect(selectionRect, clearPaint)

            // Linha tracejada com sombra de alto contraste
            canvas.drawRect(selectionRect, dashedShadowPaint)
            canvas.drawRect(selectionRect, dashedBorderPaint)

            // Cantos em "L" para efeito visual de escaneamento de precisão
            drawCornerBrackets(canvas, selectionRect)
        }

        // 3. Banner superior com instruções
        canvas.drawRoundRect(bannerRect, 32f, 32f, pillBackgroundPaint)
        canvas.drawRoundRect(bannerRect, 32f, 32f, pillBorderPaint)

        val instructionText = if (isCustomSelection) {
            "Área selecionada marcada. Arraste para refazer."
        } else {
            "Tela inteira marcada por padrão. Arraste para recortar."
        }
        canvas.drawText(instructionText, w / 2f, bannerRect.top + 70f, textPaint)

        // Botão Fechar (X)
        canvas.drawCircle(btnCloseRect.centerX(), btnCloseRect.centerY(), btnCloseRect.width() / 2f, buttonResetPaint)
        canvas.drawText("✕", btnCloseRect.centerX(), btnCloseRect.centerY() + 16f, closeIconPaint)

        // 4. Barra de Ações Inferior: [ ⟲ Tela Toda ]  [ ▶ Ouvir Agora ]
        if (isCustomSelection && !btnResetRect.isEmpty) {
            canvas.drawRoundRect(btnResetRect, 30f, 30f, buttonResetPaint)
            canvas.drawRoundRect(btnResetRect, 30f, 30f, buttonBorderPaint)
            canvas.drawText("⟲ Tela Toda", btnResetRect.centerX(), btnResetRect.centerY() + 14f, buttonTextPaint)
        }

        // Desenha o Botão Principal "Ouvir Agora"
        canvas.drawRoundRect(btnPlayRect, 30f, 30f, buttonPlayPaint)
        canvas.drawRoundRect(btnPlayRect, 30f, 30f, pillBorderPaint)
        val playText = if (isCustomSelection) "▶ Ouvir Seleção" else "▶ Ouvir Tela Toda"
        canvas.drawText(playText, btnPlayRect.centerX(), btnPlayRect.centerY() + 14f, buttonTextPaint)
    }

    private fun drawCornerBrackets(canvas: Canvas, rect: RectF) {
        val len = 40f
        // Canto superior esquerdo
        canvas.drawLine(rect.left, rect.top, rect.left + len, rect.top, cornerBracketPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + len, cornerBracketPaint)

        // Canto superior direito
        canvas.drawLine(rect.right - len, rect.top, rect.right, rect.top, cornerBracketPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + len, cornerBracketPaint)

        // Canto inferior esquerdo
        canvas.drawLine(rect.left, rect.bottom, rect.left + len, rect.bottom, cornerBracketPaint)
        canvas.drawLine(rect.left, rect.bottom - len, rect.left, rect.bottom, cornerBracketPaint)

        // Canto inferior direito
        canvas.drawLine(rect.right - len, rect.bottom, rect.right, rect.bottom, cornerBracketPaint)
        canvas.drawLine(rect.right, rect.bottom - len, rect.right, rect.bottom, cornerBracketPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layoutButtons(width.toFloat(), height.toFloat())

                // 1. Identifica exatamente onde começou o toque
                activeTouchTarget = when {
                    isPointInside(btnCloseRect, x, y, margin = 24f) -> TouchTarget.BUTTON_CLOSE
                    isPointInside(btnResetRect, x, y, margin = 24f) -> TouchTarget.BUTTON_RESET
                    isPointInside(btnPlayRect, x, y, margin = 24f) -> TouchTarget.BUTTON_PLAY
                    else -> {
                        startX = x
                        startY = y
                        isDragging = false
                        TouchTarget.SELECTION_CANVAS
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Se o toque iniciou em algum botão, NUNCA altera a área de seleção!
                if (activeTouchTarget != TouchTarget.SELECTION_CANVAS) {
                    return true
                }

                val dx = Math.abs(x - startX)
                val dy = Math.abs(y - startY)

                // Limiar de 25px para confirmar intenção de arrasto
                if (isDragging || dx > 25 || dy > 25) {
                    isDragging = true
                    isCustomSelection = true
                    selectionRect.set(
                        min(startX, x),
                        min(startY, y),
                        max(startX, x),
                        max(startY, y)
                    )
                    layoutButtons(width.toFloat(), height.toFloat())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                when (activeTouchTarget) {
                    TouchTarget.BUTTON_CLOSE -> {
                        if (isPointInside(btnCloseRect, x, y, margin = 32f)) {
                            onDismiss()
                        }
                    }
                    TouchTarget.BUTTON_RESET -> {
                        if (isPointInside(btnResetRect, x, y, margin = 32f)) {
                            resetToFullScreen()
                        }
                    }
                    TouchTarget.BUTTON_PLAY -> {
                        if (isPointInside(btnPlayRect, x, y, margin = 32f)) {
                            triggerSelection()
                        }
                    }
                    TouchTarget.SELECTION_CANVAS -> {
                        if (isDragging) {
                            if (selectionRect.width() < 60 || selectionRect.height() < 60) {
                                resetToFullScreen()
                            }
                        }
                        // Se foi um simples clique sem arrastar, preserva integralmente a seleção existente!
                    }
                    TouchTarget.NONE -> {}
                }

                activeTouchTarget = TouchTarget.NONE
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                activeTouchTarget = TouchTarget.NONE
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun triggerSelection() {
        if (hasTriggered) return
        hasTriggered = true

        val finalRect = Rect(
            selectionRect.left.toInt(),
            selectionRect.top.toInt(),
            selectionRect.right.toInt(),
            selectionRect.bottom.toInt()
        )
        if (finalRect.width() > 30 && finalRect.height() > 30) {
            onAreaSelected(finalRect)
        } else {
            onDismiss()
        }
    }
}

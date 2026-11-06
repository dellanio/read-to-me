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

    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var isCustomSelection = false

    private val selectionRect = RectF()

    // Botões interativos desenhados no Canvas
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
        if (w > 0 && h > 0 && !isCustomSelection) {
            // Seleciona a tela toda por padrão (com margem de 16px para destacar a linha tracejada)
            val margin = 16f
            selectionRect.set(margin, margin, w - margin, h - margin)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (selectionRect.isEmpty && w > 0 && h > 0) {
            val margin = 16f
            selectionRect.set(margin, margin, w - margin, h - margin)
        }

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
        val bannerWidth = min(w - 60f, 960f)
        val bannerHeight = 110f
        val bannerLeft = (w - bannerWidth) / 2f
        val bannerTop = 80f
        val bannerRect = RectF(bannerLeft, bannerTop, bannerLeft + bannerWidth, bannerTop + bannerHeight)

        canvas.drawRoundRect(bannerRect, 32f, 32f, pillBackgroundPaint)
        canvas.drawRoundRect(bannerRect, 32f, 32f, pillBorderPaint)

        val instructionText = if (isCustomSelection) {
            "Área selecionada marcada. Arraste para refazer."
        } else {
            "Tela inteira marcada por padrão. Arraste para recortar."
        }
        canvas.drawText(instructionText, w / 2f, bannerTop + 70f, textPaint)

        // Botão Fechar (X) no canto superior direito do banner
        val closeSize = 70f
        btnCloseRect.set(bannerRect.right - closeSize - 15f, bannerTop + 20f, bannerRect.right - 15f, bannerTop + 20f + closeSize)
        canvas.drawCircle(btnCloseRect.centerX(), btnCloseRect.centerY(), closeSize / 2f, buttonResetPaint)
        canvas.drawText("✕", btnCloseRect.centerX(), btnCloseRect.centerY() + 16f, closeIconPaint)

        // 4. Barra de Ações Inferior: [ ⟲ Tela Toda ]  [ ▶ Ouvir Agora ]
        val bottomMargin = 160f
        val btnPlayWidth = 420f
        val btnPlayHeight = 120f

        val btnResetWidth = if (isCustomSelection) 320f else 0f
        val spacing = 20f
        val totalActionWidth = btnPlayWidth + (if (isCustomSelection) btnResetWidth + spacing else 0f)

        val actionsLeft = (w - totalActionWidth) / 2f
        val actionsTop = h - bottomMargin - btnPlayHeight

        if (isCustomSelection) {
            // Botão Redefinir para Tela Toda
            btnResetRect.set(actionsLeft, actionsTop, actionsLeft + btnResetWidth, actionsTop + btnPlayHeight)
            canvas.drawRoundRect(btnResetRect, 30f, 30f, buttonResetPaint)
            canvas.drawRoundRect(btnResetRect, 30f, 30f, buttonBorderPaint)
            canvas.drawText("⟲ Tela Toda", btnResetRect.centerX(), btnResetRect.centerY() + 14f, buttonTextPaint)

            // Botão Ouvir Seleção
            val playLeft = btnResetRect.right + spacing
            btnPlayRect.set(playLeft, actionsTop, playLeft + btnPlayWidth, actionsTop + btnPlayHeight)
        } else {
            btnResetRect.setEmpty()
            btnPlayRect.set(actionsLeft, actionsTop, actionsLeft + btnPlayWidth, actionsTop + btnPlayHeight)
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

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Checa cliques nos botões de ação
                if (btnCloseRect.contains(x, y)) {
                    onDismiss()
                    return true
                }
                if (!btnResetRect.isEmpty && btnResetRect.contains(x, y)) {
                    val margin = 16f
                    selectionRect.set(margin, margin, width - margin, height - margin)
                    isCustomSelection = false
                    invalidate()
                    return true
                }
                if (btnPlayRect.contains(x, y)) {
                    triggerSelection()
                    return true
                }

                // Inicia potencial desenho de nova área
                startX = x
                startY = y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(x - startX)
                val dy = Math.abs(y - startY)

                if (dx > 20 || dy > 20) {
                    isDragging = true
                    isCustomSelection = true
                    selectionRect.set(
                        min(startX, x),
                        min(startY, y),
                        max(startX, x),
                        max(startY, y)
                    )
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Se foi um clique no botão de ação no UP
                if (btnCloseRect.contains(x, y)) {
                    onDismiss()
                    return true
                }
                if (!btnResetRect.isEmpty && btnResetRect.contains(x, y)) {
                    val margin = 16f
                    selectionRect.set(margin, margin, width - margin, height - margin)
                    isCustomSelection = false
                    invalidate()
                    return true
                }
                if (btnPlayRect.contains(x, y)) {
                    triggerSelection()
                    return true
                }

                if (isDragging) {
                    // Validar se a área desenhada tem tamanho mínimo útil
                    if (selectionRect.width() > 60 && selectionRect.height() > 60) {
                        isCustomSelection = true
                    } else {
                        // Se foi um arrasto acidental muito pequeno, volta para tela inteira
                        val margin = 16f
                        selectionRect.set(margin, margin, width - margin, height - margin)
                        isCustomSelection = false
                    }
                    isDragging = false
                    invalidate()
                    return true
                }

                // Se deu um simples toque fora dos botões sem arrastar:
                // Se ainda for a tela toda por padrão ou seleção válida, pode confirmar a leitura
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun triggerSelection() {
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

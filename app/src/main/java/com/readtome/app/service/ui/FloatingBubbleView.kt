package com.readtome.app.service.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.readtome.app.R
import com.readtome.app.audio.PlaybackState
import com.readtome.app.audio.TtsEngine
import kotlin.math.abs

/**
 * Botão flutuante inteligente com Mini-Player integrado no estilo ReadToMe Studio:
 * - Modo Compacto: Círculo de 58dp com borda neon laranja e ícone metálico 3D.
 * - Modo Mini-Player: Barra de controles (Play/Pause, Stop, Velocidade e Recolher).
 * - Arrastável com snap suave para a borda da tela.
 * - O botão [X] no mini-player apenas pausa/interrompe o áudio e volta ao modo ícone flutuante.
 */
@SuppressLint("ViewConstructor")
class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val ttsEngine: TtsEngine,
    private val onCloseRequested: () -> Unit,
    private val onBubbleClicked: () -> Unit
) : FrameLayout(context) {

    companion object {
        private const val TAG = "FloatingBubbleView"
    }

    private val compactSize = dpToPx(58)
    private val expandedWidth = dpToPx(250)

    val layoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        gravity = Gravity.TOP or Gravity.START
        width = compactSize
        height = compactSize
        x = dpToPx(16)
        y = dpToPx(200)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isMiniPlayerMode = false

    // Componentes de UI
    private val bubbleContainer: FrameLayout
    private val idleIcon: ImageView
    private val playerLayout: LinearLayout
    private val playPauseBtn: ImageView
    private val stopBtn: ImageView
    private val speedBtn: TextView
    private val closeBtn: ImageView

    init {
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(29).toFloat()
            setColor(Color.parseColor("#18181B")) // Dark Obsidian
            setStroke(dpToPx(2), Color.parseColor("#FF5722")) // Orange Primary Accent
        }

        bubbleContainer = FrameLayout(context).apply {
            background = backgroundDrawable
            elevation = dpToPx(12).toFloat()
        }

        // Ícone em modo ocioso (Novo Logo 3D Studio ReadToMe)
        idleIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_readtome_logo)
            val pad = dpToPx(7)
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        bubbleContainer.addView(idleIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Layout horizontal do Mini-Player
        playerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val padH = dpToPx(10)
            setPadding(padH, 0, padH, 0)
        }

        // Botão Play/Pause (Círculo Laranja com ícone branco)
        playPauseBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF5722"))
            }
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            setOnClickListener {
                Log.d(TAG, "Clique no botão Play/Pause do MiniPlayer")
                val state = ttsEngine.playbackState.value
                if (state is PlaybackState.Playing) {
                    ttsEngine.pause()
                } else if (state is PlaybackState.Paused) {
                    ttsEngine.resume()
                }
            }
        }
        playerLayout.addView(playPauseBtn, LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)))

        // Botão Stop
        stopBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.parseColor("#E0E0E0"))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#27272A"))
                setStroke(dpToPx(1), Color.parseColor("#3F3F46"))
            }
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            setOnClickListener {
                Log.d(TAG, "Clique no botão Stop do MiniPlayer")
                ttsEngine.stop()
                setMiniPlayerVisible(false)
            }
        }
        val stopParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
            marginStart = dpToPx(8)
        }
        playerLayout.addView(stopBtn, stopParams)

        // Botão de Velocidade (1.0x / 1.5x / 2.0x)
        speedBtn = TextView(context).apply {
            text = "1.0x"
            setTextColor(Color.parseColor("#FF9800"))
            textSize = 12f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#27272A"))
                setStroke(dpToPx(1), Color.parseColor("#FF5722"))
            }
            setOnClickListener {
                val current = ttsEngine.currentSpeed
                val next = when {
                    current < 1.2f -> 1.5f
                    current < 1.7f -> 2.0f
                    else -> 1.0f
                }
                text = "${next}x"
                ttsEngine.setPlaybackSpeed(next)
            }
        }
        val speedParams = LinearLayout.LayoutParams(dpToPx(46), dpToPx(34)).apply {
            marginStart = dpToPx(8)
        }
        playerLayout.addView(speedBtn, speedParams)

        // Botão Fechar/Recolher (X): Para o áudio e volta ao ícone circular (sem remover da tela)
        closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#EF4444"))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#27272A"))
                setStroke(dpToPx(1), Color.parseColor("#3F3F46"))
            }
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            setOnClickListener {
                Log.d(TAG, "Clique no botão Fechar: Interrompendo reprodução e recolhendo ao botão flutuante")
                ttsEngine.stop()
                setMiniPlayerVisible(false)
            }
        }
        val closeParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
            marginStart = dpToPx(8)
        }
        playerLayout.addView(closeBtn, closeParams)

        bubbleContainer.addView(playerLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(bubbleContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Atualiza o estado visual do player conforme emissões do motor de áudio.
     */
    fun updatePlaybackState(state: PlaybackState) {
        post {
            when (state) {
                is PlaybackState.Playing -> {
                    setMiniPlayerVisible(true)
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
                    speedBtn.text = "${state.speed}x"
                }
                is PlaybackState.Paused -> {
                    setMiniPlayerVisible(true)
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
                }
                is PlaybackState.Buffering -> {
                    setMiniPlayerVisible(true)
                    playPauseBtn.setImageResource(android.R.drawable.ic_popup_sync)
                }
                is PlaybackState.Idle,
                is PlaybackState.Error -> {
                    setMiniPlayerVisible(false)
                }
            }
        }
    }

    private fun setMiniPlayerVisible(showPlayer: Boolean) {
        if (isMiniPlayerMode == showPlayer) return
        isMiniPlayerMode = showPlayer

        val startW = layoutParams.width
        val targetW = if (showPlayer) expandedWidth else compactSize

        ValueAnimator.ofInt(startW, targetW).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (isAttachedToWindow) {
                    layoutParams.width = anim.animatedValue as Int
                    try {
                        windowManager.updateViewLayout(this@FloatingBubbleView, layoutParams)
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao animar MiniPlayer", e)
                    }
                }
            }
            start()
        }

        if (showPlayer) {
            idleIcon.visibility = View.GONE
            playerLayout.visibility = View.VISIBLE
        } else {
            playerLayout.visibility = View.GONE
            idleIcon.visibility = View.VISIBLE
            animateSnapToEdge()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                // Em modo compacto, intercepta sempre para capturar toque ou arrasto
                return !isMiniPlayerMode
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.rawX - initialTouchX)
                val dy = abs(ev.rawY - initialTouchY)
                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }

                if (isDragging && isAttachedToWindow) {
                    val screenW = getRealScreenWidth()
                    val screenH = getRealScreenHeight()
                    val newX = (initialX + dx).coerceIn(0, screenW - layoutParams.width)
                    val newY = (initialY + dy).coerceIn(dpToPx(40), screenH - layoutParams.height - dpToPx(40))

                    layoutParams.x = newX
                    layoutParams.y = newY
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao arrastar FloatingBubble", e)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = abs(event.rawX - initialTouchX)
                val dy = abs(event.rawY - initialTouchY)

                if (!isDragging && dx < touchSlop && dy < touchSlop) {
                    if (!isMiniPlayerMode) {
                        Log.i(TAG, "Toque confirmado no botão flutuante. Acionando overlay de leitura.")
                        onBubbleClicked()
                    }
                } else {
                    animateSnapToEdge()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateSnapToEdge() {
        if (!isAttachedToWindow) return
        val screenWidth = getRealScreenWidth()
        val currentW = layoutParams.width
        val margin = dpToPx(12)

        val targetX = if (layoutParams.x + currentW / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - currentW - margin
        }

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                if (isAttachedToWindow) {
                    layoutParams.x = animation.animatedValue as Int
                    try {
                        windowManager.updateViewLayout(this@FloatingBubbleView, layoutParams)
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao realizar snap na borda", e)
                    }
                }
            }
            start()
        }
    }

    private fun getRealScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.x
        }
    }

    private fun getRealScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.y
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}

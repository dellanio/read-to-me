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
import com.readtome.app.audio.PlaybackState
import com.readtome.app.audio.TtsEngine
import kotlin.math.abs

/**
 * Botão flutuante inteligente com Mini-Player totalmente interativo:
 * - Modo Compacto: Círculo de 56dp com detecção de toque para selecionar área.
 * - Modo Mini-Player: Barra de controles com Play/Pause, Stop, Velocidade e Fechar (X).
 * - Suporte a arrasto suave e Snap-to-Edge nas laterais.
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

    private val compactSize = dpToPx(56)
    private val expandedWidth = dpToPx(240)

    val layoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        gravity = Gravity.TOP or Gravity.START
        width = compactSize
        height = compactSize
        x = 50
        y = 350
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
            cornerRadius = dpToPx(28).toFloat()
            setColor(Color.parseColor("#0F172A"))
            setStroke(dpToPx(2), Color.parseColor("#38BDF8"))
        }

        bubbleContainer = FrameLayout(context).apply {
            background = backgroundDrawable
            elevation = dpToPx(10).toFloat()
        }

        // Ícone em modo ocioso (Tela 3D + Microfone)
        idleIcon = ImageView(context).apply {
            setImageResource(com.readtome.app.R.drawable.ic_readtome_3d)
            val pad = dpToPx(8)
            setPadding(pad, pad, pad, pad)
        }
        bubbleContainer.addView(idleIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Layout horizontal do Mini-Player
        playerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val padH = dpToPx(8)
            setPadding(padH, 0, padH, 0)
        }

        // Botão Play/Pause (40x40dp com área de toque acessível)
        playPauseBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            isClickable = true
            isFocusable = true
            val p = dpToPx(6)
            setPadding(p, p, p, p)
            setOnClickListener {
                Log.d(TAG, "Clique no botão Play/Pause")
                val state = ttsEngine.playbackState.value
                if (state is PlaybackState.Playing) {
                    ttsEngine.pause()
                } else if (state is PlaybackState.Paused) {
                    ttsEngine.resume()
                }
            }
        }
        playerLayout.addView(playPauseBtn, LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)))

        // Botão Stop (Para a fala e volta ao modo normal)
        stopBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            isClickable = true
            isFocusable = true
            val p = dpToPx(7)
            setPadding(p, p, p, p)
            setOnClickListener {
                Log.d(TAG, "Clique no botão Stop")
                ttsEngine.stop()
            }
        }
        val stopParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
            marginStart = dpToPx(4)
        }
        playerLayout.addView(stopBtn, stopParams)

        // Botão de Velocidade (1.0x / 1.5x / 2.0x)
        speedBtn = TextView(context).apply {
            text = "1.0x"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.editbox_background_normal)
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
        val speedParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(32)).apply {
            marginStart = dpToPx(4)
        }
        playerLayout.addView(speedBtn, speedParams)

        // Botão Fechar (X)
        closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            isClickable = true
            isFocusable = true
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            setOnClickListener {
                Log.d(TAG, "Clique no botão Fechar")
                ttsEngine.stop()
                onCloseRequested()
            }
        }
        val closeParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
            marginStart = dpToPx(4)
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
                layoutParams.width = anim.animatedValue as Int
                windowManager.updateViewLayout(this@FloatingBubbleView, layoutParams)
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
                // Em modo compacto, intercepta sempre para capturar o toque
                return !isMiniPlayerMode
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.rawX - initialTouchX)
                val dy = abs(ev.rawY - initialTouchY)
                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true
                    return true // Intercepta para arrastar mesmo com os botões ativos
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

                if (isDragging) {
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    windowManager.updateViewLayout(this, layoutParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    if (!isMiniPlayerMode) {
                        onBubbleClicked()
                    }
                } else {
                    animateSnapToEdge()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateSnapToEdge() {
        val screenWidth = getRealScreenWidth()
        val currentW = layoutParams.width
        val margin = dpToPx(12)

        val targetX = if (layoutParams.x + currentW / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - currentW - margin
        }

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                layoutParams.x = animation.animatedValue as Int
                windowManager.updateViewLayout(this@FloatingBubbleView, layoutParams)
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

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}

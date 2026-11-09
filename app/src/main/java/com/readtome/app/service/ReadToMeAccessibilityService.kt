package com.readtome.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.readtome.app.ReadToMeApp
import com.readtome.app.data.ai.GeminiTextProcessor
import com.readtome.app.service.ui.FloatingBubbleView
import com.readtome.app.service.ui.SelectionOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço central do ReadToMe:
 * - Exibe e controla o botão flutuante e overlay de seleção.
 * - Prioridade 1: Extrai nós estruturados de texto via AccessibilityNodeInfo (janela ativa e todas as janelas do sistema).
 * - Prioridade 2: Fallback transparente via takeScreenshot() + Google ML Kit OCR.
 * - Sanitização opcional com Gemini e reprodução via TtsEngine com mini-player flutuante.
 */
class ReadToMeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReadToMeA11y"
        var instance: ReadToMeAccessibilityService? = null
            private set
    }

    private lateinit var windowManager: WindowManager
    private var floatingBubbleView: FloatingBubbleView? = null
    private var selectionOverlayView: SelectionOverlayView? = null
    private var isProcessingSelection = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "ReadToMeAccessibilityService conectado com sucesso!")
        setupFloatingBubble()
        observePlaybackState()
        setupAccessibilityButtonCallback()
    }

    private fun setupAccessibilityButtonCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController.registerAccessibilityButtonCallback(object :
                android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController?) {
                    super.onClicked(controller)
                    Log.i(TAG, "Botão de atalho de acessibilidade nativo acionado pelo usuário.")
                    openSelectionOverlay()
                }
            })
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitoramento de eventos de janela
    }

    override fun onInterrupt() {
        Log.w(TAG, "Serviço de acessibilidade interrompido.")
    }

    fun updateBubbleVisibility() {
        val enabled = ReadToMeApp.instance.preferences.isFloatingBubbleEnabled
        Log.i(TAG, "Atualizando visibilidade do botão flutuante: $enabled")
        if (enabled) {
            setupFloatingBubble()
        } else {
            removeFloatingBubble()
        }
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        removeFloatingBubble()
        closeSelectionOverlay()
        textRecognizer.close()
        super.onDestroy()
    }

    // =========================================================================
    // CONTROLE DOS OVERLAYS & MINI-PLAYER
    // =========================================================================

    private fun setupFloatingBubble() {
        try {
            val isEnabled = ReadToMeApp.instance.preferences.isFloatingBubbleEnabled
            if (!isEnabled) {
                Log.d(TAG, "Botão flutuante desativado nas preferências do usuário.")
                removeFloatingBubble()
                return
            }

            if (floatingBubbleView == null) {
                val app = ReadToMeApp.instance
                floatingBubbleView = FloatingBubbleView(
                    context = this,
                    windowManager = windowManager,
                    ttsEngine = app.ttsEngine,
                    onCloseRequested = {
                        // Não remove do WindowManager ao fechar reprodução; apenas recolhe
                        Log.d(TAG, "Solicitação de recolhimento do mini player")
                    }
                ) {
                    openSelectionOverlay()
                }
                windowManager.addView(floatingBubbleView, floatingBubbleView?.layoutParams)
                Log.i(TAG, "FloatingBubbleView adicionada com sucesso ao WindowManager.")
            } else {
                floatingBubbleView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar FloatingBubbleView", e)
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            ReadToMeApp.instance.ttsEngine.playbackState.collect { state ->
                floatingBubbleView?.updatePlaybackState(state)
            }
        }
    }

    private fun removeFloatingBubble() {
        try {
            floatingBubbleView?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
                floatingBubbleView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover FloatingBubbleView", e)
        }
    }

    private fun openSelectionOverlay() {
        try {
            if (selectionOverlayView != null) return

            floatingBubbleView?.visibility = View.INVISIBLE

            selectionOverlayView = SelectionOverlayView(
                context = this,
                onAreaSelected = { rect ->
                    handleSelection(rect)
                },
                onDismiss = {
                    closeSelectionOverlay()
                }
            )
            windowManager.addView(selectionOverlayView, selectionOverlayView?.windowLayoutParams)
            Log.i(TAG, "SelectionOverlayView aberto com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir SelectionOverlayView", e)
            floatingBubbleView?.visibility = View.VISIBLE
            isProcessingSelection = false
        }
    }

    private fun closeSelectionOverlay() {
        try {
            selectionOverlayView?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
                selectionOverlayView = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar SelectionOverlayView", e)
        } finally {
            selectionOverlayView = null
            floatingBubbleView?.visibility = View.VISIBLE
            isProcessingSelection = false
        }
    }

    // =========================================================================
    // PIPELINE HÍBRIDO DE CAPTURA
    // =========================================================================

    private fun handleSelection(selectedRect: Rect) {
        if (isProcessingSelection) {
            Log.w(TAG, "handleSelection já em andamento, ignorando evento duplicado.")
            return
        }
        isProcessingSelection = true

        try {
            // Interrompe qualquer áudio anterior
            ReadToMeApp.instance.ttsEngine.stop()

            // Prioridade 1: Buscar nós nativos de texto dentro da área selecionada
            val extractedNodeText = extractTextFromAccessibilityNodes(selectedRect)

            if (extractedNodeText.isNotBlank()) {
                Log.d(TAG, "Extração bem-sucedida via nós nativos: ${extractedNodeText.take(60)}...")
                closeSelectionOverlay()
                dispatchTextToPipeline(extractedNodeText)
                return
            }

            // Prioridade 2: Fallback para Screenshot + OCR (API 30+)
            Log.d(TAG, "Nenhum nó de texto nativo encontrado. Acionando Fallback OCR...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                captureScreenshotAndOcr(selectedRect)
            } else {
                Log.e(TAG, "Fallback de screenshot requer Android 11+ (API 30).")
                Toast.makeText(this, "Nenhum texto acessível encontrado.", Toast.LENGTH_SHORT).show()
                closeSelectionOverlay()
                isProcessingSelection = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante handleSelection", e)
            closeSelectionOverlay()
            isProcessingSelection = false
        }
    }

    private fun extractTextFromAccessibilityNodes(selectedRect: Rect): String {
        val collectedNodes = mutableListOf<Pair<Rect, CharSequence>>()
        val visitedRoots = mutableSetOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return

            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)

            if (Rect.intersects(nodeRect, selectedRect)) {
                val text = node.text ?: node.contentDescription
                if (!text.isNullOrBlank()) {
                    collectedNodes.add(Pair(nodeRect, text))
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        // 1. Tenta a janela ativa principal
        rootInActiveWindow?.let { root ->
            visitedRoots.add(root)
            traverse(root)
        }

        // 2. Explora todas as demais janelas interativas do sistema
        try {
            for (window in windows) {
                val root = window.root
                if (root != null && visitedRoots.add(root)) {
                    traverse(root)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao iterar windows de acessibilidade", e)
        }

        // Ordena espacialmente (top-to-bottom, left-to-right) para fluxo natural de leitura
        collectedNodes.sortWith(compareBy({ it.first.top }, { it.first.left }))

        return collectedNodes.joinToString(separator = "\n") { it.second.toString().trim() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("WrongConstant")
    private fun captureScreenshotAndOcr(selectedRect: Rect) {
        selectionOverlayView?.visibility = View.INVISIBLE
        floatingBubbleView?.visibility = View.INVISIBLE

        Choreographer.getInstance().postFrameCallback {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace

                            val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()

                            if (softwareBitmap != null) {
                                processCroppedBitmapWithOcr(softwareBitmap, selectedRect)
                            } else {
                                Log.e(TAG, "Falha ao converter HardwareBuffer em Software Bitmap.")
                                isProcessingSelection = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro no processamento do screenshot", e)
                            isProcessingSelection = false
                        } finally {
                            closeSelectionOverlay()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "takeScreenshot falhou com código: $errorCode")
                        Toast.makeText(this@ReadToMeAccessibilityService, "Não foi possível capturar a tela.", Toast.LENGTH_SHORT).show()
                        closeSelectionOverlay()
                        isProcessingSelection = false
                    }
                }
            )
        }
    }

    private fun processCroppedBitmapWithOcr(bitmap: Bitmap, rect: Rect) {
        val safeLeft = rect.left.coerceIn(0, bitmap.width - 1)
        val safeTop = rect.top.coerceIn(0, bitmap.height - 1)
        val safeWidth = rect.width().coerceAtMost(bitmap.width - safeLeft)
        val safeHeight = rect.height().coerceAtMost(bitmap.height - safeTop)

        if (safeWidth <= 0 || safeHeight <= 0) {
            Log.w(TAG, "Dimensões de corte inválidas após clamping.")
            isProcessingSelection = false
            return
        }

        val croppedBitmap = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val detectedText = visionText.text
                if (detectedText.isNotBlank()) {
                    Log.d(TAG, "Texto extraído com sucesso pelo OCR: ${detectedText.take(60)}...")
                    dispatchTextToPipeline(detectedText)
                } else {
                    Log.w(TAG, "OCR não detectou nenhum texto na área recortada.")
                    Toast.makeText(this, "Nenhum texto identificado nesta área.", Toast.LENGTH_SHORT).show()
                    isProcessingSelection = false
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha no reconhecimento de texto do ML Kit", e)
                Toast.makeText(this, "Erro no reconhecimento de texto.", Toast.LENGTH_SHORT).show()
                isProcessingSelection = false
            }
    }

    /**
     * Sanitiza o texto (com Gemini, se habilitado) e inicia a reprodução de voz via TtsEngine.
     */
    private fun dispatchTextToPipeline(rawText: String) {
        val app = ReadToMeApp.instance
        val prefs = app.preferences

        serviceScope.launch {
            try {
                Toast.makeText(this@ReadToMeAccessibilityService, "Lendo texto da tela...", Toast.LENGTH_SHORT).show()
                val textToRead = if (prefs.isAiCleaningEnabled && prefs.geminiApiKey.isNotBlank()) {
                    val cleanResult = GeminiTextProcessor.cleanOcrText(rawText, prefs.geminiApiKey)
                    cleanResult.getOrDefault(rawText)
                } else {
                    rawText
                }

                Log.i(TAG, "Iniciando síntese de voz para: ${textToRead.take(80)}...")
                setupFloatingBubble()
                app.ttsEngine.stop()
                app.ttsEngine.speak(textToRead, prefs.playbackSpeed)
            } finally {
                isProcessingSelection = false
            }
        }
    }
}

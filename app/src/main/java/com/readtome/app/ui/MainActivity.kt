package com.readtome.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readtome.app.ReadToMeApp
import com.readtome.app.audio.PlaybackState
import com.readtome.app.data.VoiceEngineType
import com.readtome.app.data.ai.GeminiTextProcessor
import com.readtome.app.data.doc.DocumentReaderHelper
import com.readtome.app.data.web.WebArticleExtractor
import com.readtome.app.service.ReadToMeAccessibilityService
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSpeakIntent(intent)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090D16) // Deep Cyber Space Dark
                ) {
                    ReadToMeStudioScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpeakIntent(intent)
    }

    private fun handleSpeakIntent(intent: Intent?) {
        val speakText = intent?.getStringExtra("speak_text")
        if (!speakText.isNullOrBlank()) {
            val app = application as? ReadToMeApp ?: return
            app.ttsEngine.speak(speakText, app.preferences.playbackSpeed)
        }
    }
}

@Composable
fun ReadToMeStudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val app = ReadToMeApp.instance
    val prefs = app.preferences
    val playbackState by app.ttsEngine.playbackState.collectAsState()

    val isA11yEnabled = isAccessibilityServiceEnabled(context, ReadToMeAccessibilityService::class.java)

    var directInputText by remember { mutableStateOf("") }
    var processingStatus by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Preferências de Motor de Voz
    var selectedVoiceEngine by remember { mutableStateOf(prefs.voiceEngine) }
    var elevenLabsApiKey by remember { mutableStateOf(prefs.elevenLabsApiKey) }
    var elevenLabsVoiceId by remember { mutableStateOf(prefs.elevenLabsVoiceId) }
    var showElevenApiKey by remember { mutableStateOf(false) }

    // Preferências ReadToMe Neural (Kokoro 82M Offline)
    var kokoroSpeakerId by remember { mutableStateOf(prefs.kokoroSpeakerId) }

    // Preferências da MiniMax
    var minimaxApiKey by remember { mutableStateOf(prefs.minimaxApiKey) }
    var minimaxVoiceId by remember { mutableStateOf(prefs.minimaxVoiceId.ifBlank { "Portuguese_CaptivatingStoryteller" }) }
    var minimaxModel by remember { mutableStateOf(prefs.minimaxModel) }
    var showMinimaxApiKey by remember { mutableStateOf(false) }

    // Preferências do Gemini AI
    var geminiApiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var showGeminiApiKey by remember { mutableStateOf(false) }
    var isAiCleaningEnabled by remember { mutableStateOf(prefs.isAiCleaningEnabled) }

    // Preferências de Interface
    var isFloatingBubbleEnabled by remember { mutableStateOf(prefs.isFloatingBubbleEnabled) }

    // Velocidade
    var currentSpeed by remember { mutableFloatStateOf(prefs.playbackSpeed) }

    val isPlaying = playbackState is PlaybackState.Playing

    // File Picker para Documentos (PDF / TXT)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                processingStatus = "Extraindo texto do documento..."
                try {
                    val result = DocumentReaderHelper.extractText(context, uri) { current, total ->
                        processingStatus = "Lendo PDF com OCR: Página $current de $total..."
                    }
                    result.onSuccess { doc ->
                        directInputText = doc.text
                        processingStatus = "Documento '${doc.fileName}' carregado! (${doc.pageCount} pág.)"
                        Toast.makeText(context, "Documento carregado com sucesso!", Toast.LENGTH_SHORT).show()
                        // Inicia leitura imediatamente
                        app.ttsEngine.speak(doc.text, currentSpeed)
                    }.onFailure { err ->
                        processingStatus = null
                        Toast.makeText(context, "Erro ao carregar documento: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Função para analisar e ler link de notícia/site
    fun processAndReadWebLink(targetText: String) {
        val detectedUrl = WebArticleExtractor.findUrlInText(targetText)
        if (detectedUrl == null) {
            Toast.makeText(context, "Nenhum link HTTP/HTTPS encontrado no texto.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isProcessing = true
            processingStatus = "Baixando e limpando conteúdo do site..."
            try {
                val extractResult = WebArticleExtractor.extractArticle(detectedUrl)
                extractResult.onSuccess { article ->
                    var finalSpeechText = article.cleanContent

                    // Se o Gemini estiver configurado, cria roteiro de locução falada
                    if (geminiApiKey.isNotBlank() && isAiCleaningEnabled) {
                        processingStatus = "Gemini AI gerando entendimento editorial..."
                        val geminiResult = GeminiTextProcessor.summarizeForSpokenAudio(
                            articleText = "${article.title}\n\n${article.cleanContent}",
                            apiKey = geminiApiKey
                        )
                        geminiResult.onSuccess { spokenScript ->
                            finalSpeechText = spokenScript
                        }
                    }

                    directInputText = "${article.title}\n\n$finalSpeechText"
                    processingStatus = "Notícia pronta! Iniciando narração..."
                    app.ttsEngine.speak(directInputText, currentSpeed)
                }.onFailure { err ->
                    Toast.makeText(context, "Falha ao extrair página: ${err.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isProcessing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // =========================================================================
        // 1. TOP BAR: LOGO & STATUS BADGE
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Read",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "ToMe",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF38BDF8)
                    )
                }
                Text(
                    text = "Cyber-Audio Studio & AI Synthesizer",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
            }

            // Status Pill Holográfico
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isA11yEnabled) Color(0x2210B981) else Color(0x22EF4444))
                    .border(
                        1.dp,
                        if (isA11yEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isA11yEnabled) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isA11yEnabled) "SISTEMA ATIVO" else "ATIVAR SERVIÇO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isA11yEnabled) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // =========================================================================
        // 2. HERO HOLOGRÁFICO 3D: TELA TRANSLÚCIDA + MICROFONE FLUTUANTE
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Holographic3dScreenWithMic(isPlaying = isPlaying)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Visualizador de Espectro de Áudio
        AudioWaveVisualizer(isPlaying = isPlaying)

        Spacer(modifier = Modifier.height(20.dp))



        // =========================================================================
        // 3. CONSOLE DE LEITURA (TEXTO, NOTÍCIAS, LINKS E DOCUMENTOS)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Central de Leitura",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    // Ações Rápidas: Colar e Limpar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botão Colar
                        IconButton(
                            onClick = {
                                val clip = clipboardManager.getText()
                                if (clip != null) {
                                    directInputText = clip.text
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Colar",
                                tint = Color(0xFF38BDF8)
                            )
                        }

                        // Botão Limpar Texto (Destaque conforme solicitado)
                        if (directInputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    directInputText = ""
                                    processingStatus = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar Campo",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Campo de Texto com Botão de Limpar Integrado
                OutlinedTextField(
                    value = directInputText,
                    onValueChange = { directInputText = it },
                    placeholder = {
                        Text(
                            "Cole texto, link de notícia ou carregue um arquivo (PDF / TXT)...",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    },
                    trailingIcon = {
                        if (directInputText.isNotBlank()) {
                            IconButton(onClick = { directInputText = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0B1120),
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFE2E8F0)
                    )
                )

                // Feedback de Processamento (Download de link ou OCR de PDF)
                AnimatedVisibility(visible = isProcessing || processingStatus != null) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = processingStatus ?: "Processando...",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Barra de Ações: [Analisar Link] e [Carregar PDF / TXT]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botão Analisar Link do Site
                    OutlinedButton(
                        onClick = { processAndReadWebLink(directInputText) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ler Link Web", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Botão Carregar Documento (PDF / TXT)
                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA855F7)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9333EA))
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF / TXT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botão Principal: Ouvir Agora / Parar Áudio
                Button(
                    onClick = {
                        if (isPlaying) {
                            app.ttsEngine.stop()
                        } else {
                            val detectedUrl = WebArticleExtractor.findUrlInText(directInputText)
                            if (detectedUrl != null && directInputText.trim() == detectedUrl) {
                                // Se for puramente um link, faz a extração inteligente automaticamente
                                processAndReadWebLink(directInputText)
                            } else {
                                val text = directInputText.ifBlank {
                                    "ReadToMe pronto para narrar qualquer conteúdo da sua tela com naturalidade imediata."
                                }
                                app.ttsEngine.speak(text, currentSpeed)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFEF4444) else Color(0xFF2563EB)
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Parar Áudio" else "Ouvir Agora",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))



        // =========================================================================
        // 4. SELETOR DE MOTOR DE VOZ & CASCATA INTELIGENTE (ELEVENLABS + MINIMAX)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Motores de Voz & IA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    // Botão Salvar Todas as Chaves
                    Button(
                        onClick = {
                            prefs.elevenLabsApiKey = elevenLabsApiKey
                            prefs.elevenLabsVoiceId = elevenLabsVoiceId
                            prefs.minimaxApiKey = minimaxApiKey
                            prefs.minimaxVoiceId = minimaxVoiceId
                            prefs.minimaxModel = minimaxModel
                            Toast.makeText(context, "Todas as chaves salvas com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA))
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salvar Tudo", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Badges de Status das Chaves e Motores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val elevenConfigured = elevenLabsApiKey.isNotBlank()
                    val speechMgr = app.ttsEngine as? com.readtome.app.audio.SpeechManager
                    val kokoroInstalled = speechMgr?.kokoroEngine?.isModelInstalled() ?: true

                    // Badge ElevenLabs
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (elevenConfigured) Color(0x2210B981) else Color(0x2264748B))
                            .border(1.dp, if (elevenConfigured) Color(0xFF10B981) else Color(0xFF475569), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (elevenConfigured) "● ElevenLabs Ativa" else "○ ElevenLabs Pendente",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (elevenConfigured) Color(0xFF34D399) else Color(0xFF94A3B8)
                        )
                    }

                    // Badge ReadToMe Kokoro Offline
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (kokoroInstalled) Color(0x2238BDF8) else Color(0x2264748B))
                            .border(1.dp, if (kokoroInstalled) Color(0xFF38BDF8) else Color(0xFF475569), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (kokoroInstalled) "● ReadToMe Neural Pronto" else "○ Modelo Pendente",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (kokoroInstalled) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Abas de Seleção de Motor
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Linha 1: Cascata Inteligente (Recomendado)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedVoiceEngine == VoiceEngineType.AUTO_CASCADE) Color(0xFF4338CA) else Color(0xFF0F172A)
                            )
                            .border(
                                1.dp,
                                if (selectedVoiceEngine == VoiceEngineType.AUTO_CASCADE) Color(0xFF818CF8) else Color(0xFF1E293B),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedVoiceEngine = VoiceEngineType.AUTO_CASCADE
                                prefs.voiceEngine = VoiceEngineType.AUTO_CASCADE
                                Toast.makeText(context, "Cascata: ElevenLabs ➔ ReadToMe Neural ➔ Nativo", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✨ Cascata IA: ElevenLabs ➔ ReadToMe Neural (Offline)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedVoiceEngine == VoiceEngineType.AUTO_CASCADE) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }

                    // Linha 2: Motores Individuais
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // ReadToMe Neural (Kokoro Offline)
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selectedVoiceEngine == VoiceEngineType.KOKORO) Color(0xFF0284C7) else Color(0xFF0F172A)
                                )
                                .border(
                                    1.dp,
                                    if (selectedVoiceEngine == VoiceEngineType.KOKORO) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedVoiceEngine = VoiceEngineType.KOKORO
                                    prefs.voiceEngine = VoiceEngineType.KOKORO
                                    Toast.makeText(context, "Modo: ReadToMe Neural (Kokoro Offline)", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡ ReadToMe (Kokoro)",
                                fontSize = 10.sp,
                                fontWeight = if (selectedVoiceEngine == VoiceEngineType.KOKORO) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedVoiceEngine == VoiceEngineType.KOKORO) Color.White else Color(0xFF94A3B8)
                            )
                        }

                        // ElevenLabs
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selectedVoiceEngine == VoiceEngineType.ELEVEN_LABS) Color(0xFF6B21A8) else Color(0xFF0F172A)
                                )
                                .border(
                                    1.dp,
                                    if (selectedVoiceEngine == VoiceEngineType.ELEVEN_LABS) Color(0xFFA855F7) else Color(0xFF1E293B),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedVoiceEngine = VoiceEngineType.ELEVEN_LABS
                                    prefs.voiceEngine = VoiceEngineType.ELEVEN_LABS
                                    Toast.makeText(context, "Modo: Apenas ElevenLabs", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ElevenLabs",
                                fontSize = 10.sp,
                                fontWeight = if (selectedVoiceEngine == VoiceEngineType.ELEVEN_LABS) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedVoiceEngine == VoiceEngineType.ELEVEN_LABS) Color.White else Color(0xFF94A3B8)
                            )
                        }

                        // Nativo Android
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selectedVoiceEngine == VoiceEngineType.NATIVE) Color(0xFF2563EB) else Color(0xFF0F172A)
                                )
                                .border(
                                    1.dp,
                                    if (selectedVoiceEngine == VoiceEngineType.NATIVE) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedVoiceEngine = VoiceEngineType.NATIVE
                                    prefs.voiceEngine = VoiceEngineType.NATIVE
                                    Toast.makeText(context, "Modo: Android Nativo (Offline)", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nativo",
                                fontSize = 10.sp,
                                fontWeight = if (selectedVoiceEngine == VoiceEngineType.NATIVE) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedVoiceEngine == VoiceEngineType.NATIVE) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // =====================================================================
                // SUB-PAINEL 1: CONFIGURAÇÃO ELEVENLABS (PRINCIPAL)
                // =====================================================================
                Text(
                    text = "1. ElevenLabs (Voz de Estúdio - Principal)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC084FC)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = elevenLabsApiKey,
                    onValueChange = { elevenLabsApiKey = it },
                    label = { Text("Chave API ElevenLabs") },
                    placeholder = { Text("xi-api-key...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showElevenApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showElevenApiKey = !showElevenApiKey }) {
                            Icon(
                                imageVector = if (showElevenApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0B1120),
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFE2E8F0)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chips de vozes ElevenLabs (Premade: Funcionam em contas gratuitas e pagas)
                val elevenVoices = listOf(
                    "George (Narrador / Grátis)" to "JBFqnCBsd6RMkjVDRZzb",
                    "Sarah (Feminina / Grátis)" to "EXAVITQu4vr4xnSDxMaL",
                    "Roger (Expressivo / Grátis)" to "CwhRBWXzGAHq8TQ4Fs17",
                    "Rachel (Plano Pago)" to "21m00Tcm4TlvDq8ikWAM"
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    elevenVoices.take(2).forEach { (label, id) ->
                        val isSelected = elevenLabsVoiceId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF6B21A8) else Color(0xFF1E293B))
                                .clickable {
                                    elevenLabsVoiceId = id
                                    prefs.elevenLabsVoiceId = id
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://elevenlabs.io"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA855F7))
                    ) {
                        Text("Criar Chave ElevenLabs", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            prefs.elevenLabsApiKey = elevenLabsApiKey
                            prefs.elevenLabsVoiceId = elevenLabsVoiceId
                            Toast.makeText(context, "ElevenLabs salva!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7))
                    ) {
                        Text("Salvar Eleven", fontSize = 11.sp)
                    }
                }

        Spacer(modifier = Modifier.height(20.dp))



                // =====================================================================
                // SUB-PAINEL 2: READTOME NEURAL (KOKORO-82M OFFLINE VIA SHERPA-ONNX)
                // =====================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. ReadToMe Neural (Kokoro Offline - Sem API/Sem Custo)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                val speechMgr = app.ttsEngine as? com.readtome.app.audio.SpeechManager
                val kokoroInstalled = speechMgr?.kokoroEngine?.isModelInstalled() ?: true

                // Card de Status do Modelo Local
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (kokoroInstalled) Color(0x1A0284C7) else Color(0x1AF43F5E))
                        .border(1.dp, if (kokoroInstalled) Color(0xFF0284C7) else Color(0xFFF43F5E), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (kokoroInstalled) "✓ Modelo On-Device Pronto" else "⚠ Modelo Ausente",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (kokoroInstalled) Color(0xFF38BDF8) else Color(0xFFFB7185)
                            )
                            Text(
                                text = if (kokoroInstalled) "Kokoro-82M INT8 (Multilíngue) • 100% Offline" else "Copie o modelo para a pasta do app",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Button(
                            onClick = {
                                prefs.voiceEngine = VoiceEngineType.KOKORO
                                selectedVoiceEngine = VoiceEngineType.KOKORO
                                app.ttsEngine.speak(
                                    "Olá Dellanio! Esta é a voz neural ReadToMe, rodando cem por cento no seu aparelho sem precisar de internet.",
                                    currentSpeed
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Testar Voz", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Vozes Neurais (Português do Brasil & Inglês):",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Chips de vozes neurais offline (Supertonic-3 & Sherpa-ONNX)
                val neuralVoicesRow1 = listOf(
                    "Voz 1 (Padrão)" to 0,
                    "Voz 2 (Expressiva)" to 1,
                    "Voz 3 (Suave)" to 2,
                    "Voz 4 (Grave)" to 3
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    neuralVoicesRow1.forEach { (label, id) ->
                        val isSelected = kokoroSpeakerId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                                .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(8.dp))
                                .clickable {
                                    kokoroSpeakerId = id
                                    prefs.kokoroSpeakerId = id
                                    Toast.makeText(context, "Voz Neural: $label", Toast.LENGTH_SHORT).show()
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val neuralVoicesRow2 = listOf(
                    "Voz 5 (Narrativa)" to 4,
                    "Voz 6 (Dinâmica)" to 5,
                    "Voz 7 (Conversacional)" to 6,
                    "Voz 8 (Clara)" to 7
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    neuralVoicesRow2.forEach { (label, id) ->
                        val isSelected = kokoroSpeakerId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                                .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(8.dp))
                                .clickable {
                                    kokoroSpeakerId = id
                                    prefs.kokoroSpeakerId = id
                                    Toast.makeText(context, "Voz Neural: $label", Toast.LENGTH_SHORT).show()
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = Color.White, textAlign = TextAlign.Center, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // =====================================================================
                // SUB-PAINEL 3: MINI MAX (OPCIONAL VIA API)
                // =====================================================================
                var showMinimaxSection by remember { mutableStateOf(minimaxApiKey.isNotBlank()) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMinimaxSection = !showMinimaxSection },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. MiniMax T2A (Opcional via API)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = if (showMinimaxSection) "▲ Ocultar" else "▼ Expandir",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (showMinimaxSection) {
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = minimaxApiKey,
                        onValueChange = { minimaxApiKey = it },
                        label = { Text("Chave API MiniMax (platform.minimax.io)") },
                        placeholder = { Text("Bearer token...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showMinimaxApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showMinimaxApiKey = !showMinimaxApiKey }) {
                                Icon(
                                    imageVector = if (showMinimaxApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0B1120),
                            focusedBorderColor = Color(0xFF14B8A6),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.minimax.io"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF14B8A6))
                        ) {
                            Text("Criar Chave MiniMax", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                prefs.minimaxApiKey = minimaxApiKey
                                prefs.minimaxVoiceId = minimaxVoiceId
                                prefs.minimaxModel = minimaxModel
                                Toast.makeText(context, "MiniMax salva com sucesso!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Text("Salvar MiniMax", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))



        // =========================================================================
        // 5. STUDIO DE VELOCIDADE DE VOZ
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Velocidade da Narração",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0C4A6E))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("%.2fx", currentSpeed),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = currentSpeed,
                    onValueChange = {
                        val rounded = (it * 20).roundToInt() / 20f
                        currentSpeed = rounded
                        prefs.playbackSpeed = rounded
                        app.ttsEngine.setPlaybackSpeed(rounded)
                    },
                    valueRange = 0.5f..2.5f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x (Lento)", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text("1.0x (Normal)", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text("2.5x (Rápido)", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))



        // =========================================================================
        // 6. INTELIGÊNCIA EDITORIAL & RESUMO (GOOGLE GEMINI 1.5 FLASH)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Inteligência Editorial (Gemini)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Switch(
                        checked = isAiCleaningEnabled,
                        onCheckedChange = {
                            isAiCleaningEnabled = it
                            prefs.isAiCleaningEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2563EB),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Gera entendimento de notícias web e transforma artigos em roteiros de rádio/podcast falados, além de remover ruídos de OCR da tela.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { geminiApiKey = it },
                    label = { Text("Chave API Gemini (Google AI Studio - Grátis)") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showGeminiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGeminiApiKey = !showGeminiApiKey }) {
                            Icon(
                                imageVector = if (showGeminiApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0B1120),
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFE2E8F0)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Criar Chave Google", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            prefs.geminiApiKey = geminiApiKey
                            Toast.makeText(context, "Chave do Gemini salva com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Chave", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // =========================================================================
        // 7. MODOS DE CAPTURA RÁPIDA
        // =========================================================================
        Text(
            text = "Modos de Uso Integrados",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        StudioFeatureCard(
            icon = Icons.Default.Description,
            title = "Importação de PDF & TXT",
            description = "Toque em 'PDF / TXT' para ler livros, apostilas ou faturas direto no celular com OCR integrado."
        )

        Spacer(modifier = Modifier.height(10.dp))

        StudioFeatureCard(
            icon = Icons.Default.Link,
            title = "Leitura Inteligente de Notícias",
            description = "Cole qualquer link e toque em 'Ler Link Web'. O ReadToMe extrai a notícia e descarta anúncios e menus."
        )

        Spacer(modifier = Modifier.height(10.dp))

        StudioFeatureCard(
            icon = Icons.Default.CropFree,
            title = "Seleção Visual na Tela",
            description = "Toque no botão flutuante e arraste o retângulo para ler qualquer trecho da tela (WhatsApp, Telegram, fotos)."
        )

        Spacer(modifier = Modifier.height(10.dp))

        StudioFeatureCard(
            icon = Icons.Default.Security,
            title = "Atalho Nativo da Samsung",
            description = "Conectado ao botão de acessibilidade da barra do seu Galaxy: você pode esconder o botão flutuante e usar apenas o atalho do sistema."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // =========================================================================
        // 8. CONFIGURAÇÕES DE INTERFACE
        // =========================================================================
        Text(
            text = "Preferências de Interface",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newValue = !isFloatingBubbleEnabled
                        isFloatingBubbleEnabled = newValue
                        prefs.isFloatingBubbleEnabled = newValue
                        ReadToMeAccessibilityService.instance?.updateBubbleVisibility()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = isFloatingBubbleEnabled,
                        onCheckedChange = { isChecked ->
                            isFloatingBubbleEnabled = isChecked
                            prefs.isFloatingBubbleEnabled = isChecked
                            ReadToMeAccessibilityService.instance?.updateBubbleVisibility()
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF38BDF8),
                            uncheckedColor = Color(0xFF64748B),
                            checkmarkColor = Color(0xFF0F172A)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Exibir botão flutuante na tela",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ícone sobre outros apps para ler seleção imediata. Se desativado, utilize o atalho de acessibilidade da barra.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Rodapé de Versão e Atualização do Aplicativo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ReadToMe v${com.readtome.app.BuildConfig.VERSION_NAME} (Build ${com.readtome.app.BuildConfig.VERSION_CODE})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Atualizado em ${com.readtome.app.BuildConfig.BUILD_TIME}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ID: ${com.readtome.app.BuildConfig.APPLICATION_ID}",
                fontSize = 10.sp,
                color = Color(0xFF334155)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

/**
 * Gráfico 3D Holográfico: Ícone Estilizado em 3D em Perspectiva Métrica + Halo Neon Volumétrico
 */
@Composable
fun Holographic3dScreenWithMic(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isPlaying) 1.07f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 650 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = if (isPlaying) 0.85f else 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 500 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    val tiltY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_y"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // Halo de brilho volumétrico neon ciano e magenta
        Canvas(modifier = Modifier.size(260.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x8838BDF8),
                        Color(0x44A855F7),
                        Color.Transparent
                    )
                ),
                radius = size.width / 2f,
                alpha = auraAlpha
            )
        }

        // Card do Ícone 3D Estilizado em Perspectiva Métrica
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    rotationX = 12f
                    rotationY = tiltY
                    scaleX = pulseScale
                    scaleY = pulseScale
                    cameraDistance = 16f * density
                    shadowElevation = 24f
                }
                .clip(RoundedCornerShape(36.dp))
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8),
                            Color(0xFFA855F7),
                            Color(0x4438BDF8)
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.readtome.app.R.drawable.ic_readtome_3d),
                contentDescription = "ReadToMe 3D Studio",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Visualizador Dinâmico de Espectro de Frequência de Áudio (Equalizador Neon)
 */
@Composable
fun AudioWaveVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_eq")

    val barHeights = List(9) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = if (isPlaying) (0.35f + (index % 4) * 0.2f) else 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isPlaying) (300 + index * 60) else 1500,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            barHeights.forEachIndexed { index, animHeight ->
                val heightPercent = animHeight.value
                val barColor = when (index % 3) {
                    0 -> Color(0xFF38BDF8)
                    1 -> Color(0xFF818CF8)
                    else -> Color(0xFFA855F7)
                }

                Canvas(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                ) {
                    val actualHeight = size.height * heightPercent
                    val top = (size.height - actualHeight) / 2f
                    drawLine(
                        color = if (isPlaying) barColor else Color(0xFF334155),
                        start = Offset(size.width / 2f, top),
                        end = Offset(size.width / 2f, top + actualHeight),
                        strokeWidth = size.width,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun StudioFeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1B4B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(
    context: Context,
    serviceClass: Class<*>
): Boolean {
    val expectedId = "${context.packageName}/${serviceClass.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)

    while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        if (componentName.equals(expectedId, ignoreCase = true)) {
            return true
        }
    }
    return false
}

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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readtome.app.R
import com.readtome.app.ReadToMeApp
import com.readtome.app.audio.PlaybackState
import com.readtome.app.data.VoiceEngineType
import com.readtome.app.data.ai.GeminiTextProcessor
import com.readtome.app.data.doc.DocumentReaderHelper
import com.readtome.app.data.web.WebArticleExtractor
import com.readtome.app.service.ReadToMeAccessibilityService
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =========================================================================
// PALETA DE CORES V2.0: PRETO + CARVÃO + PRATA + LARANJA QUEIMADO
// =========================================================================
private val BgDark = Color(0xFF090A0E)
private val SurfaceCard = Color(0xFF13141B)
private val SurfaceCardElevated = Color(0xFF181A24)
private val SurfaceInput = Color(0xFF0E0F16)
private val BorderSubtle = Color(0xFF232532)
private val BorderActive = Color(0xFFEA580C)
private val OrangePrimary = Color(0xFFF97316)
private val OrangeDeep = Color(0xFFEA580C)
private val SilverBright = Color(0xFFF8FAFC)
private val SilverMuted = Color(0xFF94A3B8)
private val SilverDark = Color(0xFF64748B)

private val OrangeGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFEA580C), Color(0xFFF97316))
)

private val PlayButtonGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFFB923C), Color(0xFFEA580C), Color(0xFFC2410C))
)

enum class StudioTab(val labelPt: String, val labelEn: String, val icon: ImageVector) {
    LEITURA("Leitura", "Reading", Icons.Default.MenuBook),
    BIBLIOTECA("Biblioteca", "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    VOZ_AUDIO("Voz e Áudio", "Voice & Audio", Icons.Default.Tune),
    AJUSTES("Ajustes", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSpeakIntent(intent)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
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
    val app = context.applicationContext as ReadToMeApp
    val prefs = remember { app.preferences }

    val playbackState by app.ttsEngine.playbackState.collectAsState()

    // Aba de Navegação Ativa
    var currentTab by remember { mutableStateOf(StudioTab.LEITURA) }

    // Estado da Leitura
    var directInputText by remember { mutableStateOf("") }
    var activeDocumentTitle by remember { mutableStateOf("Texto Direto") }
    var isProcessing by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf<String?>(null) }
    var highlightInReading by remember { mutableStateOf(true) }

    // Preferências de Motor de Voz
    var selectedVoiceEngine by remember { mutableStateOf(prefs.voiceEngine) }
    var elevenLabsApiKey by remember { mutableStateOf(prefs.elevenLabsApiKey) }
    var elevenLabsVoiceId by remember { mutableStateOf(prefs.elevenLabsVoiceId) }
    var showElevenApiKey by remember { mutableStateOf(false) }

    // Preferências ReadToMe Neural (Grátis - On-Device)
    var kokoroSpeakerId by remember { mutableStateOf(prefs.kokoroSpeakerId) }

    // Preferências de Idioma e Voz Nativa Masculina
    var speechLanguage by remember { mutableStateOf(prefs.speechLanguage) }
    var nativeVoiceMale by remember { mutableStateOf(prefs.nativeVoiceMale) }
    val isEn = speechLanguage == "en-US"

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
                processingStatus = if (isEn) "Extracting text from document..." else "Extraindo texto do documento..."
                try {
                    val result = DocumentReaderHelper.extractText(context, uri) { current, total ->
                        processingStatus = if (isEn) "Reading PDF with OCR: Page $current of $total..." else "Lendo PDF com OCR: Página $current de $total..."
                    }
                    result.onSuccess { doc ->
                        directInputText = doc.text
                        activeDocumentTitle = doc.fileName
                        processingStatus = if (isEn) "Document '${doc.fileName}' loaded! (${doc.pageCount} p.)" else "Documento '${doc.fileName}' carregado! (${doc.pageCount} pág.)"
                        Toast.makeText(context, if (isEn) "Document loaded successfully!" else "Documento carregado com sucesso!", Toast.LENGTH_SHORT).show()
                        app.ttsEngine.speak(doc.text, currentSpeed)
                    }.onFailure { err ->
                        processingStatus = null
                        Toast.makeText(context, (if (isEn) "Error loading document: " else "Erro ao carregar documento: ") + err.message, Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, if (isEn) "No HTTP/HTTPS link found in text." else "Nenhum link HTTP/HTTPS encontrado no texto.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isProcessing = true
            processingStatus = if (isEn) "Downloading and cleaning webpage content..." else "Baixando e limpando conteúdo do site..."
            try {
                val extractResult = WebArticleExtractor.extractArticle(detectedUrl)
                extractResult.onSuccess { article ->
                    var finalSpeechText = article.cleanContent

                    if (geminiApiKey.isNotBlank() && isAiCleaningEnabled) {
                        processingStatus = if (isEn) "Gemini AI generating editorial script..." else "Gemini AI gerando entendimento editorial..."
                        val geminiResult = GeminiTextProcessor.summarizeForSpokenAudio(
                            articleText = "${article.title}\n\n${article.cleanContent}",
                            apiKey = geminiApiKey
                        )
                        geminiResult.onSuccess { spokenScript ->
                            finalSpeechText = spokenScript
                        }
                    }

                    directInputText = "${article.title}\n\n$finalSpeechText"
                    activeDocumentTitle = article.title.take(30).ifBlank { "Notícia Web" }
                    processingStatus = if (isEn) "Article ready! Starting narration..." else "Notícia pronta! Iniciando narração..."
                    app.ttsEngine.speak(directInputText, currentSpeed)
                }.onFailure { err ->
                    Toast.makeText(context, (if (isEn) "Failed to extract webpage: " else "Falha ao extrair página: ") + err.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                isProcessing = false
            }
        }
    }

    val isA11yEnabled = isAccessibilityServiceEnabled(context, ReadToMeAccessibilityService::class.java)

    // Layout Principal com Barra Superior, Tabs e Área de Conteúdo
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
    ) {
        // =========================================================================
        // 1. CABEÇALHO COMPACTO PREMIUM (LOGO + AUDIO WAVE DISCRETA + AJUSTES)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.ic_readtome_logo),
                        contentDescription = "ReadToMe",
                        modifier = Modifier.height(26.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEn) "Read, listen and follow your texts" else "Leia, ouça e acompanhe seus textos",
                        fontSize = 11.sp,
                        color = SilverDark
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pill de Status do Serviço de Acessibilidade
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isA11yEnabled) Color(0x1F10B981) else Color(0x1FEF4444))
                        .border(1.dp, if (isA11yEnabled) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(20.dp))
                        .clickable {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isA11yEnabled) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isA11yEnabled) (if (isEn) "ACTIVE" else "ATIVO") else (if (isEn) "ENABLE" else "ATIVAR"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isA11yEnabled) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }
                }

                // Botão de Atalho para Ajustes
                IconButton(
                    onClick = { currentTab = StudioTab.AJUSTES },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = if (currentTab == StudioTab.AJUSTES) OrangePrimary else SilverMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // =========================================================================
        // 2. BARRA DE NAVEGAÇÃO HORIZONTAL EM ABAS (DESIGN 02 APROVADO)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) OrangeGradient else Brush.linearGradient(listOf(SurfaceCard, SurfaceCard)))
                        .border(
                            1.dp,
                            if (isSelected) BorderActive else BorderSubtle,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { currentTab = tab },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else SilverMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isEn) tab.labelEn else tab.labelPt,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else SilverMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =========================================================================
        // 3. CORPO DINÂMICO CONFORME A ABA SELECIONADA
        // =========================================================================
        Box(modifier = Modifier.weight(1f)) {
            Crossfade(targetState = currentTab, label = "tab_fade") { tab ->
                when (tab) {
                    StudioTab.LEITURA -> LeituraTabContent(
                        isEn = isEn,
                        directInputText = directInputText,
                        onTextChanged = { directInputText = it },
                        activeDocumentTitle = activeDocumentTitle,
                        isProcessing = isProcessing,
                        processingStatus = processingStatus,
                        isPlaying = isPlaying,
                        currentSpeed = currentSpeed,
                        onSpeedChanged = {
                            currentSpeed = it
                            prefs.playbackSpeed = it
                            app.ttsEngine.setPlaybackSpeed(it)
                        },
                        highlightInReading = highlightInReading,
                        onHighlightChanged = { highlightInReading = it },
                        isAiCleaningEnabled = isAiCleaningEnabled,
                        onAiCleaningChanged = {
                            isAiCleaningEnabled = it
                            prefs.isAiCleaningEnabled = it
                        },
                        selectedVoiceEngine = selectedVoiceEngine,
                        kokoroSpeakerId = kokoroSpeakerId,
                        elevenLabsVoiceId = elevenLabsVoiceId,
                        nativeVoiceMale = nativeVoiceMale,
                        onChooseFileClick = {
                            documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "*/*"))
                        },
                        onPasteClick = {
                            val clip = clipboardManager.getText()
                            if (clip != null) {
                                directInputText = clip.text
                            }
                        },
                        onClearClick = {
                            directInputText = ""
                            processingStatus = null
                            activeDocumentTitle = "Texto Direto"
                        },
                        onReadWebLinkClick = {
                            processAndReadWebLink(directInputText)
                        },
                        onPlayPauseClick = {
                            if (isPlaying) {
                                app.ttsEngine.stop()
                            } else {
                                val detectedUrl = WebArticleExtractor.findUrlInText(directInputText)
                                if (detectedUrl != null && directInputText.trim() == detectedUrl) {
                                    processAndReadWebLink(directInputText)
                                } else {
                                    val text = directInputText.ifBlank {
                                        if (isEn) {
                                            "ReadToMe is ready to narrate any text or screen content with high natural audio."
                                        } else {
                                            "ReadToMe pronto para narrar qualquer conteúdo da sua tela com naturalidade imediata."
                                        }
                                    }
                                    app.ttsEngine.speak(text, currentSpeed)
                                }
                            }
                        },
                        onNavigateToVozTab = { currentTab = StudioTab.VOZ_AUDIO }
                    )

                    StudioTab.BIBLIOTECA -> BibliotecaTabContent(
                        isEn = isEn,
                        activeDocumentTitle = activeDocumentTitle,
                        directInputText = directInputText,
                        isPlaying = isPlaying,
                        onReadDocument = {
                            if (directInputText.isNotBlank()) {
                                app.ttsEngine.speak(directInputText, currentSpeed)
                                currentTab = StudioTab.LEITURA
                            }
                        }
                    )

                    StudioTab.VOZ_AUDIO -> VozAudioTabContent(
                        isEn = isEn,
                        selectedVoiceEngine = selectedVoiceEngine,
                        onSelectVoiceEngine = {
                            selectedVoiceEngine = it
                            prefs.voiceEngine = it
                        },
                        kokoroSpeakerId = kokoroSpeakerId,
                        onSelectKokoroSpeaker = {
                            kokoroSpeakerId = it
                            prefs.kokoroSpeakerId = it
                        },
                        elevenLabsApiKey = elevenLabsApiKey,
                        onElevenApiKeyChanged = { elevenLabsApiKey = it },
                        showElevenApiKey = showElevenApiKey,
                        onToggleShowElevenApiKey = { showElevenApiKey = !showElevenApiKey },
                        elevenLabsVoiceId = elevenLabsVoiceId,
                        onSelectElevenVoice = {
                            elevenLabsVoiceId = it
                            prefs.elevenLabsVoiceId = it
                        },
                        nativeVoiceMale = nativeVoiceMale,
                        onSelectNativeMale = { isMale ->
                            nativeVoiceMale = isMale
                            prefs.nativeVoiceMale = isMale
                            (app.ttsEngine as? com.readtome.app.audio.SpeechManager)?.nativeEngine?.applyLanguageAndVoice(speechLanguage, isMale)
                        },
                        speechLanguage = speechLanguage,
                        onSelectSpeechLanguage = { lang ->
                            speechLanguage = lang
                            prefs.speechLanguage = lang
                            (app.ttsEngine as? com.readtome.app.audio.SpeechManager)?.nativeEngine?.applyLanguageAndVoice(lang, nativeVoiceMale)
                        },
                        currentSpeed = currentSpeed,
                        onSpeedChanged = {
                            currentSpeed = it
                            prefs.playbackSpeed = it
                            app.ttsEngine.setPlaybackSpeed(it)
                        },
                        directInputText = directInputText,
                        app = app,
                        onSaveAll = {
                            prefs.elevenLabsApiKey = elevenLabsApiKey
                            prefs.elevenLabsVoiceId = elevenLabsVoiceId
                            prefs.kokoroSpeakerId = kokoroSpeakerId
                            prefs.speechLanguage = speechLanguage
                            prefs.nativeVoiceMale = nativeVoiceMale
                            Toast.makeText(context, if (isEn) "Settings saved successfully!" else "Configurações de áudio salvas!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    StudioTab.AJUSTES -> AjustesTabContent(
                        isEn = isEn,
                        geminiApiKey = geminiApiKey,
                        onGeminiApiKeyChanged = { geminiApiKey = it },
                        showGeminiApiKey = showGeminiApiKey,
                        onToggleShowGeminiApiKey = { showGeminiApiKey = !showGeminiApiKey },
                        isAiCleaningEnabled = isAiCleaningEnabled,
                        onAiCleaningEnabledChanged = {
                            isAiCleaningEnabled = it
                            prefs.isAiCleaningEnabled = it
                        },
                        onSaveGeminiKey = {
                            prefs.geminiApiKey = geminiApiKey
                            Toast.makeText(context, if (isEn) "Gemini key saved!" else "Chave do Gemini salva com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        isFloatingBubbleEnabled = isFloatingBubbleEnabled,
                        onFloatingBubbleChanged = {
                            isFloatingBubbleEnabled = it
                            prefs.isFloatingBubbleEnabled = it
                            ReadToMeAccessibilityService.instance?.updateBubbleVisibility()
                        }
                    )
                }
            }
        }
    }
}

// =========================================================================
// ABA 1: LEITURA (PLAYER, EDITOR E IMPORTAÇÃO)
// =========================================================================
@Composable
private fun LeituraTabContent(
    isEn: Boolean,
    directInputText: String,
    onTextChanged: (String) -> Unit,
    activeDocumentTitle: String,
    isProcessing: Boolean,
    processingStatus: String?,
    isPlaying: Boolean,
    currentSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    highlightInReading: Boolean,
    onHighlightChanged: (Boolean) -> Unit,
    isAiCleaningEnabled: Boolean,
    onAiCleaningChanged: (Boolean) -> Unit,
    selectedVoiceEngine: VoiceEngineType,
    kokoroSpeakerId: Int,
    elevenLabsVoiceId: String,
    nativeVoiceMale: Boolean,
    onChooseFileClick: () -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit,
    onReadWebLinkClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNavigateToVozTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // CARD 1: ADICIONAR TEXTO / IMPORTAÇÃO RÁPIDA (COM ESTILO TRACEJADO DO MOCKUP)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ícone com badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PostAdd,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isEn) "Add Text or Document" else "Adicionar texto",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SilverBright
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isEn) "Paste text, web news link or choose a document." else "Cole um texto, link de notícia ou escolha um arquivo.",
                    fontSize = 11.sp,
                    color = SilverMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Chips de Formatos Reais Suportados (TXT, PDF, LINK WEB)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FormatChip(label = "TXT", color = Color(0xFF38BDF8))
                    FormatChip(label = "PDF", color = Color(0xFFEF4444))
                    FormatChip(label = "WEB", color = Color(0xFF34D399))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botão "Escolher arquivo" com acabamento metálico
                Button(
                    onClick = onChooseFileClick,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardElevated),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = SilverBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Choose File (PDF / TXT)" else "Escolher arquivo (PDF / TXT)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SilverBright
                    )
                }
            }
        }

        // CARD 2: EDITOR "TEXTO OU LINK"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) "Text or Web Link" else "Texto ou link",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SilverBright
                        )
                    }

                    // Ações Rápidas: Colar e Limpar
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onPasteClick, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Colar",
                                tint = OrangePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (directInputText.isNotBlank()) {
                            IconButton(onClick = onClearClick, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar",
                                    tint = SilverDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Campo de Entrada com Fundo Escuro Metálico
                OutlinedTextField(
                    value = directInputText,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            text = if (isEn) "Paste text or article link here to narrate..." else "Cole aqui o texto ou link que deseja ouvir...",
                            color = SilverDark,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceInput,
                        unfocusedContainerColor = SurfaceInput,
                        focusedBorderColor = OrangePrimary,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = SilverBright,
                        unfocusedTextColor = SilverMuted
                    )
                )

                // Feedback de Processamento (Link ou OCR)
                AnimatedVisibility(visible = isProcessing || processingStatus != null) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCardElevated)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = OrangePrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = processingStatus ?: "",
                                fontSize = 11.sp,
                                color = SilverMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botão de Extração de Notícia se for link
                OutlinedButton(
                    onClick = onReadWebLinkClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary),
                    border = BorderStroke(1.dp, OrangeDeep)
                ) {
                    Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "Extract and Read Web Link" else "Extrair e Ler Notícia Web",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // CARD 3: CONFIGURAÇÕES DE LEITURA (ATALHO DE RESUMO CONFORME MOCKUP)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = OrangePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "Reading Settings" else "Configurações de leitura",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SilverBright
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Linha de Voz Ativa (Clicável para ir à aba de Voz)
                val activeVoiceName = when (selectedVoiceEngine) {
                    VoiceEngineType.AUTO_CASCADE -> if (isEn) "AI Cascade (Smart)" else "Cascata IA (Inteligente)"
                    VoiceEngineType.KOKORO -> {
                        when (kokoroSpeakerId) {
                            6 -> if (isEn) "Voice 1 (Default) · ReadToMe" else "Voz 1 (Padrão) · ReadToMe"
                            0 -> if (isEn) "Voice 2 (Classic) · ReadToMe" else "Voz 2 (Clássica) · ReadToMe"
                            4 -> if (isEn) "Voice 3 (Narrative) · ReadToMe" else "Voz 3 (Narrativa) · ReadToMe"
                            5 -> if (isEn) "Voice 4 (Dynamic) · ReadToMe" else "Voz 4 (Dinâmica) · ReadToMe"
                            else -> if (isEn) "Voice 5 (Clear) · ReadToMe" else "Voz 5 (Clara) · ReadToMe"
                        }
                    }
                    VoiceEngineType.ELEVEN_LABS -> "ElevenLabs (Studio Cloud)"
                    VoiceEngineType.NATIVE -> if (nativeVoiceMale) (if (isEn) "Native Male (Android)" else "Nativo Masculino (Android)") else (if (isEn) "Native Female (Android)" else "Nativo Feminino (Android)")
                    else -> "ReadToMe Neural"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigateToVozTab() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = SilverDark, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEn) "Voice" else "Voz", fontSize = 12.sp, color = SilverMuted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeVoiceName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrangePrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = SilverDark, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Linha de Velocidade
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = SilverDark, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEn) "Speed" else "Velocidade", fontSize = 12.sp, color = SilverMuted)
                    }
                    Text(
                        text = String.format("%.1f×", currentSpeed),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SilverBright
                    )
                }

                Slider(
                    value = currentSpeed,
                    onValueChange = {
                        val rounded = (it * 10).roundToInt() / 10f
                        onSpeedChanged(rounded)
                    },
                    valueRange = 0.5f..2.5f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = OrangePrimary,
                        activeTrackColor = OrangePrimary,
                        inactiveTrackColor = SurfaceCardElevated
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Linha de Modos: Destaque em leitura e Limpeza IA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Destaque de Texto
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCardElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEn) "Highlight text" else "Destacar texto",
                                fontSize = 10.sp,
                                color = SilverBright,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = highlightInReading,
                                onCheckedChange = onHighlightChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OrangePrimary,
                                    uncheckedThumbColor = SilverDark,
                                    uncheckedTrackColor = SurfaceInput
                                ),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Limpeza / Resumo IA
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCardElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEn) "Gemini Editorial" else "Limpeza IA",
                                fontSize = 10.sp,
                                color = SilverBright,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = isAiCleaningEnabled,
                                onCheckedChange = onAiCleaningChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OrangePrimary,
                                    uncheckedThumbColor = SilverDark,
                                    uncheckedTrackColor = SurfaceInput
                                ),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 4. PLAYER CENTRAL PROEMINENTE (BOTÃO CIRCULAR LARANJA DOMINANTE)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de Tempo Real / Estimado
                val wordCount = directInputText.split("\\s+".toRegex()).count { it.isNotBlank() }
                val estimatedMinutes = (wordCount / 140).coerceAtLeast(0)
                val estimatedSeconds = ((wordCount % 140) * 60 / 140).coerceAtLeast(0)
                val formattedTime = String.format("%02d:%02d", estimatedMinutes, estimatedSeconds)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPlaying) "00:15" else "00:00", fontSize = 11.sp, color = SilverDark)
                    // Barra de progresso visual com thumb laranja
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SurfaceCardElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isPlaying) 0.35f else 0.0f)
                                .height(4.dp)
                                .background(OrangePrimary)
                        )
                    }
                    Text(formattedTime, fontSize = 11.sp, color = SilverDark)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Linha de Controles de Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Loop / Recomeçar
                    IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = "Reiniciar", tint = SilverMuted, modifier = Modifier.size(20.dp))
                    }

                    // Anterior
                    IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = SilverBright, modifier = Modifier.size(24.dp))
                    }

                    // BOTÃO CIRCULAR DOMINANTE DE PLAY / STOP (LARANJA QUEIMADO)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(elevation = 12.dp, shape = CircleShape, spotColor = OrangePrimary)
                            .clip(CircleShape)
                            .background(PlayButtonGradient)
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Parar" else "Reproduzir",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Próximo
                    IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Próximo", tint = SilverBright, modifier = Modifier.size(24.dp))
                    }

                    // Velocidade rápida
                    IconButton(
                        onClick = {
                            val next = when {
                                currentSpeed < 1.0f -> 1.0f
                                currentSpeed < 1.5f -> 1.5f
                                currentSpeed < 2.0f -> 2.0f
                                else -> 1.0f
                            }
                            onSpeedChanged(next)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = "Velocidade", tint = SilverMuted, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // CARD DO DOCUMENTO ATUAL NO RODAPÉ DO PLAYER
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeDocumentTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SilverBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$wordCount palavras • ~${estimatedMinutes}m • ${if (isPlaying) "Reproduzindo" else "Pronto"}",
                                    fontSize = 10.sp,
                                    color = SilverDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// =========================================================================
// ABA 2: BIBLIOTECA (FASE 2 CONDICIONAL - ESTADO LIMPO SEM DADOS FALSOS)
// =========================================================================
@Composable
private fun BibliotecaTabContent(
    isEn: Boolean,
    activeDocumentTitle: String,
    directInputText: String,
    isPlaying: Boolean,
    onReadDocument: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (isEn) "Library & History" else "Biblioteca de Leituras",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = SilverBright
        )

        // Documento Ativo da Sessão
        if (directInputText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, OrangeDeep)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeDocumentTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SilverBright
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22F97316))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (isPlaying) "Lendo" else "Em Memória", fontSize = 10.sp, color = OrangePrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = directInputText.take(160) + if (directInputText.length > 160) "..." else "",
                        fontSize = 11.sp,
                        color = SilverMuted,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onReadDocument,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeDeep)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEn) "Resume Reading" else "Retomar Leitura", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Card Transparente e Confiável da Fase 2
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    contentDescription = null,
                    tint = SilverMuted,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isEn) "Persistent Storage & Cloud Sync (Phase 2)" else "Armazenamento & Histórico Offline (Fase 2)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SilverBright,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isEn) {
                        "Local Room database persistence, bookmarks and document tagging are planned for Phase 2. All files loaded in your current session remain ready in the reading hub."
                    } else {
                        "A persistência definitiva em banco de dados local Room, marcações de página e categorização estão planejadas para a Fase 2. Todo texto carregado nesta sessão permanece pronto para leitura imediata."
                    },
                    fontSize = 11.sp,
                    color = SilverDark,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// =========================================================================
// ABA 3: VOZ E ÁUDIO (MOTORES, CATÁLOGO, TESTES E IDIOMA)
// =========================================================================
@Composable
private fun VozAudioTabContent(
    isEn: Boolean,
    selectedVoiceEngine: VoiceEngineType,
    onSelectVoiceEngine: (VoiceEngineType) -> Unit,
    kokoroSpeakerId: Int,
    onSelectKokoroSpeaker: (Int) -> Unit,
    elevenLabsApiKey: String,
    onElevenApiKeyChanged: (String) -> Unit,
    showElevenApiKey: Boolean,
    onToggleShowElevenApiKey: () -> Unit,
    elevenLabsVoiceId: String,
    onSelectElevenVoice: (String) -> Unit,
    nativeVoiceMale: Boolean,
    onSelectNativeMale: (Boolean) -> Unit,
    speechLanguage: String,
    onSelectSpeechLanguage: (String) -> Unit,
    currentSpeed: Float,
    onSpeedChanged: (Float) -> Unit,
    directInputText: String,
    app: ReadToMeApp,
    onSaveAll: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEn) "Voice & Speech Engines" else "Motores de Voz & IA",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SilverBright
            )

            Button(
                onClick = onSaveAll,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeDeep)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isEn) "Save All" else "Salvar Tudo", fontSize = 11.sp)
            }
        }

        // SELETOR DE MODO / MOTOR (CASCATA OU ESPECÍFICO)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cascata Inteligente
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedVoiceEngine == VoiceEngineType.AUTO_CASCADE) Color(0x33F97316) else SurfaceCardElevated)
                        .border(1.dp, if (selectedVoiceEngine == VoiceEngineType.AUTO_CASCADE) OrangePrimary else BorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { onSelectVoiceEngine(VoiceEngineType.AUTO_CASCADE) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEn) "✨ AI Cascade: ReadToMe (Free) ➔ ElevenLabs" else "✨ Cascata IA: ReadToMe (Grátis) ➔ ElevenLabs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedVoiceEngine == VoiceEngineType.AUTO_CASCADE) OrangePrimary else SilverBright
                    )
                }

                // Linha de Motores Específicos
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnginePill(
                        label = if (isEn) "⚡ ReadToMe (Free)" else "⚡ ReadToMe (Grátis)",
                        isSelected = selectedVoiceEngine == VoiceEngineType.KOKORO,
                        modifier = Modifier.weight(1.2f),
                        onClick = { onSelectVoiceEngine(VoiceEngineType.KOKORO) }
                    )
                    EnginePill(
                        label = "ElevenLabs",
                        isSelected = selectedVoiceEngine == VoiceEngineType.ELEVEN_LABS,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectVoiceEngine(VoiceEngineType.ELEVEN_LABS) }
                    )
                    EnginePill(
                        label = if (isEn) "Android TTS" else "Nativo",
                        isSelected = selectedVoiceEngine == VoiceEngineType.NATIVE,
                        modifier = Modifier.weight(0.9f),
                        onClick = { onSelectVoiceEngine(VoiceEngineType.NATIVE) }
                    )
                }
            }
        }

        // SUB-PAINEL 1: READTOME INTEGRADO (ON-DEVICE)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "1. ReadToMe · Integrated (On-Device)" else "1. ReadToMe · Integrado (On-Device)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )
                        Text(
                            text = if (isEn) "100% Offline, Neural, Free & Unlimited" else "100% Offline, Neural, Grátis e Ilimitado",
                            fontSize = 10.sp,
                            color = SilverMuted
                        )
                    }

                    Button(
                        onClick = {
                            onSelectVoiceEngine(VoiceEngineType.KOKORO)
                            val testText = directInputText.trim().ifBlank {
                                if (isEn) "Hello! This is ReadToMe integrated neural voice, running 100% locally."
                                else "Olá Dellanio! Esta é a voz neural ReadToMe, rodando cem por cento no seu aparelho de forma gratuita."
                            }
                            app.ttsEngine.speak(testText, currentSpeed)
                        },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeDeep)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Listen Sample" else "Ouvir Amostra", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(if (isEn) "Neural Voices:" else "Vozes Neurais:", fontSize = 11.sp, color = SilverBright, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                // Nova Sequência das 5 Vozes Neurais
                val voicesRow1 = listOf(
                    (if (isEn) "Voice 1 (Default)" else "Voz 1 (Padrão)") to 6,
                    (if (isEn) "Voice 2 (Classic)" else "Voz 2 (Clássica)") to 0,
                    (if (isEn) "Voice 3 (Narrative)" else "Voz 3 (Narrativa)") to 4
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voicesRow1.forEach { (label, id) ->
                        val isSelected = kokoroSpeakerId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x33F97316) else SurfaceCardElevated)
                                .border(1.dp, if (isSelected) OrangePrimary else BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onSelectKokoroSpeaker(id) }
                            .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = if (isSelected) OrangePrimary else SilverBright, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val voicesRow2 = listOf(
                    (if (isEn) "Voice 4 (Dynamic)" else "Voz 4 (Dinâmica)") to 5,
                    (if (isEn) "Voice 5 (Clear)" else "Voz 5 (Clara)") to 7
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voicesRow2.forEach { (label, id) ->
                        val isSelected = kokoroSpeakerId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x33F97316) else SurfaceCardElevated)
                                .border(1.dp, if (isSelected) OrangePrimary else BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onSelectKokoroSpeaker(id) }
                            .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = if (isSelected) OrangePrimary else SilverBright, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // SUB-PAINEL 2: ELEVENLABS (NUVEM)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEn) "2. ElevenLabs (Studio Cloud)" else "2. ElevenLabs (Voz de Estúdio - Nuvem)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SilverBright
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = elevenLabsApiKey,
                    onValueChange = onElevenApiKeyChanged,
                    label = { Text(if (isEn) "ElevenLabs API Key" else "Chave API ElevenLabs") },
                    placeholder = { Text("xi-api-key...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showElevenApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleShowElevenApiKey) {
                            Icon(
                                imageVector = if (showElevenApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = SilverDark
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceInput,
                        unfocusedContainerColor = SurfaceInput,
                        focusedBorderColor = OrangePrimary,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = SilverBright,
                        unfocusedTextColor = SilverMuted
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                val elevenVoices = listOf(
                    (if (isEn) "George (Narrator / Free)" else "George (Narrador / Grátis)") to "JBFqnCBsd6RMkjVDRZzb",
                    (if (isEn) "Sarah (Female / Free)" else "Sarah (Feminina / Grátis)") to "EXAVITQu4vr4xnSDxMaL",
                    (if (isEn) "Roger (Expressive / Free)" else "Roger (Expressivo / Grátis)") to "CwhRBWXzGAHq8TQ4Fs17",
                    (if (isEn) "Rachel (Paid Plan)" else "Rachel (Plano Pago)") to "21m00Tcm4TlvDq8ikWAM"
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    elevenVoices.take(2).forEach { (label, id) ->
                        val isSelected = elevenLabsVoiceId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x33F97316) else SurfaceCardElevated)
                                .border(1.dp, if (isSelected) OrangePrimary else BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { onSelectElevenVoice(id) }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 10.sp, color = if (isSelected) OrangePrimary else SilverBright, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // SUB-PAINEL 3: VOZ NATIVA ANDROID (ESTRITAMENTE BRASIL - SEM SOTAQUE LUSO)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEn) "3. Android Native Voice (Offline)" else "3. Voz Nativa do Android (Offline)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SilverBright
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEn) "System offline synthesizer with pure regional dialect filter." else "Sintetizador integrado do aparelho com filtro regional estrito.",
                    fontSize = 11.sp,
                    color = SilverMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (nativeVoiceMale) Color(0x33F97316) else SurfaceCardElevated)
                            .border(1.dp, if (nativeVoiceMale) OrangePrimary else BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onSelectNativeMale(true) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isEn) "👨 Male Voice" else "👨 Voz Masculina", fontSize = 11.sp, color = if (nativeVoiceMale) OrangePrimary else SilverBright, fontWeight = if (nativeVoiceMale) FontWeight.Bold else FontWeight.Normal)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!nativeVoiceMale) Color(0x33F97316) else SurfaceCardElevated)
                            .border(1.dp, if (!nativeVoiceMale) OrangePrimary else BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onSelectNativeMale(false) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isEn) "👩 Female Voice" else "👩 Voz Feminina", fontSize = 11.sp, color = if (!nativeVoiceMale) OrangePrimary else SilverBright, fontWeight = if (!nativeVoiceMale) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSelectVoiceEngine(VoiceEngineType.NATIVE)
                        val speechMgr = app.ttsEngine as? com.readtome.app.audio.SpeechManager
                        speechMgr?.nativeEngine?.applyLanguageAndVoice(speechLanguage, nativeVoiceMale)
                        val testText = directInputText.trim().ifBlank {
                            if (isEn) "Hello! This is the Android native voice configured on your phone."
                            else "Olá Dellanio! Esta é a voz nativa do Android configurada no seu aparelho."
                        }
                        speechMgr?.nativeEngine?.speak(testText, currentSpeed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardElevated),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = OrangePrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isEn) "Test Native Voice" else "Testar Voz Nativa", fontSize = 11.sp, color = SilverBright)
                }
            }
        }

        // SELETOR DE IDIOMA (PT-BR / EN-US)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEn) "Speech & Interface Language" else "Idioma de Fala & Interface",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SilverBright
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEn) "Switches speech synthesis dialect and translates application UI:" else "Alterne a síntese de pronúncia e traduza a interface do app:",
                    fontSize = 11.sp,
                    color = SilverMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isPt = speechLanguage == "pt-BR"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPt) Color(0x33F97316) else SurfaceCardElevated)
                            .border(1.dp, if (isPt) OrangePrimary else BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { onSelectSpeechLanguage("pt-BR") }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🇧🇷  Português (BR)", fontSize = 11.sp, color = if (isPt) OrangePrimary else SilverBright, fontWeight = if (isPt) FontWeight.Bold else FontWeight.Normal)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isEn) Color(0x33F97316) else SurfaceCardElevated)
                            .border(1.dp, if (isEn) OrangePrimary else BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { onSelectSpeechLanguage("en-US") }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🇺🇸  English (US)", fontSize = 11.sp, color = if (isEn) OrangePrimary else SilverBright, fontWeight = if (isEn) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// =========================================================================
// ABA 4: AJUSTES (GEMINI, ACESSIBILIDADE, RECURSOS E SOBRE)
// =========================================================================
@Composable
private fun AjustesTabContent(
    isEn: Boolean,
    geminiApiKey: String,
    onGeminiApiKeyChanged: (String) -> Unit,
    showGeminiApiKey: Boolean,
    onToggleShowGeminiApiKey: () -> Unit,
    isAiCleaningEnabled: Boolean,
    onAiCleaningEnabledChanged: (Boolean) -> Unit,
    onSaveGeminiKey: () -> Unit,
    isFloatingBubbleEnabled: Boolean,
    onFloatingBubbleChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (isEn) "System Settings & AI" else "Ajustes do Sistema & IA",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = SilverBright
        )

        // CARD GEMINI IA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "Editorial Intelligence (Gemini)" else "Inteligência Editorial (Gemini)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SilverBright
                        )
                    }
                    Switch(
                        checked = isAiCleaningEnabled,
                        onCheckedChange = onAiCleaningEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = OrangePrimary,
                            uncheckedThumbColor = SilverDark,
                            uncheckedTrackColor = SurfaceInput
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isEn) "Converts web articles into spoken scripts, removes OCR noise and generates clear summaries." else "Gera entendimento de notícias web e transforma artigos em roteiros falados, além de remover ruídos de OCR da tela.",
                    fontSize = 11.sp,
                    color = SilverMuted,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = onGeminiApiKeyChanged,
                    label = { Text(if (isEn) "Gemini API Key" else "Chave API Gemini") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showGeminiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleShowGeminiApiKey) {
                            Icon(
                                imageVector = if (showGeminiApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = SilverDark
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceInput,
                        unfocusedContainerColor = SurfaceInput,
                        focusedBorderColor = OrangePrimary,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = SilverBright,
                        unfocusedTextColor = SilverMuted
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
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Get Free Google Key" else "Criar Chave Google", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onSaveGeminiKey,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeDeep)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Save Key" else "Salvar Chave", fontSize = 11.sp)
                    }
                }
            }
        }

        // PREFERÊNCIAS DE INTERFACE (BOTÃO FLUTUANTE)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFloatingBubbleChanged(!isFloatingBubbleEnabled) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                        checked = isFloatingBubbleEnabled,
                        onCheckedChange = onFloatingBubbleChanged,
                        colors = CheckboxDefaults.colors(
                            checkedColor = OrangePrimary,
                            uncheckedColor = SilverDark,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isEn) "Display floating screen button" else "Exibir botão flutuante na tela",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SilverBright
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEn) "Bubble overlay to select and read screen contents." else "Ícone sobre outros apps para ler seleção imediata da tela.",
                            fontSize = 11.sp,
                            color = SilverMuted
                        )
                    }
                }
            }
        }

        // RECURSOS INTEGRADOS (MODOS DE USO)
        Text(
            text = if (isEn) "Integrated Usage Modes" else "Recursos & Modos de Uso",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = SilverBright
        )

        AjustesFeatureCard(
            icon = Icons.Default.Description,
            title = if (isEn) "PDF & TXT Extraction" else "Importação de PDF & TXT",
            description = if (isEn) "Read books, invoices and contracts using built-in OCR." else "Toque em 'Escolher arquivo' para ler apostilas ou faturas com OCR integrado."
        )

        AjustesFeatureCard(
            icon = Icons.Default.Link,
            title = if (isEn) "Smart Web News Extraction" else "Leitura Inteligente de Notícias",
            description = if (isEn) "Paste web articles to discard menus and ads automatically." else "Cole qualquer link e toque em 'Extrair Notícia'. Remove menus e anúncios."
        )

        AjustesFeatureCard(
            icon = Icons.Default.CropFree,
            title = if (isEn) "Visual Screen Selection" else "Seleção Visual na Tela",
            description = if (isEn) "Drag selection rectangle over WhatsApp, Telegram or apps." else "Toque no botão flutuante e arraste o retângulo para ler qualquer trecho."
        )

        AjustesFeatureCard(
            icon = Icons.Default.Security,
            title = if (isEn) "Samsung Accessibility Shortcut" else "Atalho Nativo da Samsung",
            description = if (isEn) "Connected to navigation bar accessibility button." else "Integrado à barra do seu Galaxy: você pode usar o atalho do sistema."
        )

        // RODAPÉ / SOBRE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ReadToMe v${com.readtome.app.BuildConfig.VERSION_NAME} (Build ${com.readtome.app.BuildConfig.VERSION_CODE})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = SilverDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEn) "Updated at ${com.readtome.app.BuildConfig.BUILD_TIME}" else "Atualizado em ${com.readtome.app.BuildConfig.BUILD_TIME}",
                fontSize = 10.sp,
                color = SilverDark
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// =========================================================================
// COMPONENTES AUXILIARES
// =========================================================================
@Composable
private fun FormatChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCardElevated)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun EnginePill(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x33F97316) else SurfaceCardElevated)
            .border(1.dp, if (isSelected) OrangePrimary else BorderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) OrangePrimary else SilverMuted
        )
    }
}

@Composable
private fun AjustesFeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCardElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SilverBright)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, fontSize = 11.sp, color = SilverMuted, lineHeight = 15.sp)
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

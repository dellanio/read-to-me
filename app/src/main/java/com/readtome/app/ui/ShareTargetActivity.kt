package com.readtome.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readtome.app.ReadToMeApp
import com.readtome.app.data.ai.GeminiTextProcessor
import com.readtome.app.data.web.ExtractedArticle
import com.readtome.app.data.web.WebArticleExtractor
import kotlinx.coroutines.launch

/**
 * Activity para processar textos e links compartilhados pelo Android Share Sheet.
 * Configurada com launchMode="singleTop", lidando com onCreate e onNewIntent.
 */
class ShareTargetActivity : ComponentActivity() {

    private var sharedTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    val text = sharedTextState.value
                    if (text != null) {
                        ShareReaderDialog(
                            rawSharedText = text,
                            onDismiss = { finish() }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF2563EB))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (Intent.ACTION_SEND == intent.action) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)

            if (!text.isNullOrBlank()) {
                sharedTextState.value = text
                return
            }

            // Verifica se foi enviado um arquivo (PDF ou TXT) via EXTRA_STREAM
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            }

            if (streamUri != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val result = com.readtome.app.data.doc.DocumentReaderHelper.extractText(this@ShareTargetActivity, streamUri)
                    result.onSuccess { doc ->
                        sharedTextState.value = doc.text
                    }.onFailure { err ->
                        Toast.makeText(this@ShareTargetActivity, "Falha ao ler arquivo: ${err.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun ShareReaderDialog(
    rawSharedText: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var article by remember { mutableStateOf<ExtractedArticle?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }

    LaunchedEffect(rawSharedText) {
        val detectedUrl = WebArticleExtractor.findUrlInText(rawSharedText)

        if (detectedUrl != null) {
            val result = WebArticleExtractor.extractArticle(detectedUrl)
            result.onSuccess { extracted ->
                article = extracted
                isLoading = false
            }.onFailure { err ->
                errorMessage = "Falha ao extrair artigo da página: ${err.localizedMessage}"
                isLoading = false
            }
        } else {
            // É um texto direto compartilhado (não um link)
            article = ExtractedArticle(
                url = "",
                title = "Texto Compartilhado",
                cleanContent = rawSharedText,
                excerpt = null
            )
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ReadToMe Web",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Extraindo artigo com Readability...",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFDC2626),
                        fontSize = 14.sp
                    )
                } else if (article != null) {
                    val art = article!!
                    Text(
                        text = art.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = art.cleanContent,
                        fontSize = 14.sp,
                        color = Color(0xFF475569),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botão 1: Ler na íntegra
                    Button(
                        onClick = {
                            val app = ReadToMeApp.instance
                            app.ttsEngine.speak(art.cleanContent, app.preferences.playbackSpeed)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvir Artigo Completo", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botão 2: Resumo em Locução com Gemini
                    OutlinedButton(
                        onClick = {
                            val app = ReadToMeApp.instance
                            val apiKey = app.preferences.geminiApiKey

                            if (apiKey.isBlank()) {
                                Toast.makeText(
                                    app,
                                    "Configure sua chave Gemini nas configurações do app para usar o resumo.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@OutlinedButton
                            }

                            scope.launch {
                                isSummarizing = true
                                val summaryResult = GeminiTextProcessor.summarizeForSpokenAudio(
                                    art.cleanContent,
                                    apiKey
                                )
                                isSummarizing = false
                                summaryResult.onSuccess { spokenScript ->
                                    app.ttsEngine.speak(spokenScript, app.preferences.playbackSpeed)
                                    onDismiss()
                                }.onFailure { e ->
                                    Toast.makeText(
                                        app,
                                        "Erro no resumo Gemini: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isSummarizing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gerando resumo com Gemini...")
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resumo em Locução (Gemini)", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

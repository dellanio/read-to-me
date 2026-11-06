package com.readtome.app.data.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Processador de texto com Gemini:
 * - Limpa ruídos de OCR (horários, botões de UI, cabeçalhos de mensagem).
 * - Adapta notícias e artigos longos em roteiro de locução falada e fluida.
 */
object GeminiTextProcessor {

    private const val TAG = "GeminiTextProcessor"
    private const val MODEL_NAME = "gemini-1.5-flash"

    /**
     * Sanitiza textos de OCR retirando artefatos visuais e botões de interface.
     */
    suspend fun cleanOcrText(rawText: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chave da API Gemini não configurada."))
        }

        try {
            val generativeModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey
            )

            val prompt = """
                Você é um assistente de leitura acessível.
                Sua tarefa é sanitizar o seguinte texto capturado por OCR da tela de um smartphone para que ele seja falado em voz alta:
                
                Texto Bruto:
                \"\"\"$rawText\"\"\"
                
                Instruções:
                1. Remova ruídos visuais de UI (ex: '12:34', 'visto por último', 'digite uma mensagem', botões 'enviar', 'curtir', 'compartilhar').
                2. Reestruture frases e parágrafos quebrados para formar uma leitura fluida em Português.
                3. Preserve fielmente o sentido das mensagens e conversas.
                4. Retorne EXCLUSIVAMENTE o texto final para leitura, sem saudações, aspas extras ou explicações.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val cleaned = response.text?.trim() ?: rawText
            Result.success(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "Falha na sanitização com Gemini", e)
            Result.failure(e)
        }
    }

    /**
     * Resume e formata um artigo longo para locução falada de rádio/podcast.
     */
    suspend fun summarizeForSpokenAudio(articleText: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chave da API Gemini não configurada."))
        }

        try {
            val generativeModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey
            )

            val prompt = """
                Você é um locutor profissional de rádio e notícias.
                Adapte e resuma o seguinte artigo web em um roteiro de locução falada conciso, fluido e natural em Português do Brasil:
                
                Artigo:
                \"\"\"$articleText\"\"\"
                
                Instruções:
                1. Comece direto com o fato principal ou manchete de forma envolvente.
                2. Use linguagem direta e fácil de compreender ouvindo.
                3. Duração ideal de fala: 1 a 2 minutos.
                4. Retorne EXCLUSIVAMENTE o texto da locução, sem títulos como 'Roteiro:', sem notas ou instruções entre parênteses.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val script = response.text?.trim() ?: articleText
            Result.success(script)
        } catch (e: Exception) {
            Log.e(TAG, "Falha no resumo de áudio com Gemini", e)
            Result.failure(e)
        }
    }
}

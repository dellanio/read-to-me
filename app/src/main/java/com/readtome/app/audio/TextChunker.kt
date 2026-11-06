package com.readtome.app.audio

import java.util.regex.Pattern

/**
 * Utilitário de chunking inteligente de texto em sentenças.
 * Permite Time-To-First-Audio (TTFA) imediato, iniciando a reprodução
 * da primeira frase enquanto as seguintes continuam na fila ou em processamento.
 */
object TextChunker {

    // Regex de tamanho fixo seguro para Android ICU (look-behind de 1 caractere)
    private val SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+")
    private val PARAGRAPH_SPLIT = Pattern.compile("\\n+")
    private val CLEANUP_WHITESPACE = Pattern.compile("\\s+")

    /**
     * Divide um texto bruto em uma lista de sentenças prontas para síntese de voz.
     * @param text Texto de entrada (OCR ou Artigo Web)
     * @param maxChunkLength Tamanho máximo aproximado para evitar timeouts em engines de áudio
     */
    fun splitIntoSentences(text: String, maxChunkLength: Int = 300): List<String> {
        val sanitized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (sanitized.isEmpty()) return emptyList()

        val paragraphs = PARAGRAPH_SPLIT.split(sanitized)
        val result = mutableListOf<String>()

        for (para in paragraphs) {
            val trimmedPara = CLEANUP_WHITESPACE.matcher(para.trim()).replaceAll(" ")
            if (trimmedPara.isBlank()) continue

            val sentences = SENTENCE_BOUNDARY.split(trimmedPara)
            for (sentence in sentences) {
                val cleanSentence = sentence.trim()
                if (cleanSentence.isBlank()) continue

                if (cleanSentence.length <= maxChunkLength) {
                    result.add(cleanSentence)
                } else {
                    val subChunks = splitLongSentence(cleanSentence, maxChunkLength)
                    result.addAll(subChunks)
                }
            }
        }

        return if (result.isEmpty()) listOf(sanitized) else result
    }

    private fun splitLongSentence(longSentence: String, maxChunkLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        val commaSplit = longSentence.split(Pattern.compile("(?<=[,;:])\\s+"))
        var currentChunk = StringBuilder()

        for (part in commaSplit) {
            if (currentChunk.length + part.length < maxChunkLength) {
                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                currentChunk.append(part)
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                if (part.length > maxChunkLength) {
                    val words = part.split(" ")
                    var wordChunk = StringBuilder()
                    for (w in words) {
                        if (wordChunk.length + w.length < maxChunkLength) {
                            if (wordChunk.isNotEmpty()) wordChunk.append(" ")
                            wordChunk.append(w)
                        } else {
                            if (wordChunk.isNotEmpty()) chunks.add(wordChunk.toString().trim())
                            wordChunk = StringBuilder(w)
                        }
                    }
                    if (wordChunk.isNotEmpty()) chunks.add(wordChunk.toString().trim())
                } else {
                    currentChunk.append(part)
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks
    }
}

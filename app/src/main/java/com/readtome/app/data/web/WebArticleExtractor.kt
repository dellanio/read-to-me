package com.readtome.app.data.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import java.util.regex.Pattern

data class ExtractedArticle(
    val url: String,
    val title: String,
    val cleanContent: String,
    val excerpt: String?
)

/**
 * Extrator inteligente de artigos web.
 * Extrai URLs de textos compartilhados sujos e usa Readability4J
 * para remover menus, rodapés, anúncios e capturar apenas o texto principal.
 */
object WebArticleExtractor {

    private const val TAG = "WebArticleExtractor"

    // Regex para identificar URLs http/https mesmo dentro de textos misturados
    private val URL_PATTERN = Pattern.compile(
        "https?://[\\w\\-\\.]+(?::[0-9]+)?(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]*)?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Encontra a primeira URL válida dentro de uma string compartilhada.
     */
    fun findUrlInText(rawText: String): String? {
        val matcher = URL_PATTERN.matcher(rawText)
        return if (matcher.find()) {
            matcher.group(0)
        } else {
            null
        }
    }

    /**
     * Faz o download do HTML e extrai o corpo do artigo limpo com Readability4J.
     */
    suspend fun extractArticle(url: String): Result<ExtractedArticle> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Baixando conteúdo da URL: $url")
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .timeout(15_000)
                .followRedirects(true)
                .get()

            val rawHtml = document.html()
            val readability = Readability4J(url, rawHtml)
            val article = readability.parse()

            val title = article.title ?: document.title() ?: "Artigo Web"
            val textContent = article.textContent ?: ""

            if (textContent.isBlank()) {
                // Fallback para texto do elemento <article> ou <body> do Jsoup se Readability falhar
                val fallbackText = document.select("article").text().ifBlank { document.body().text() }
                return@withContext Result.success(
                    ExtractedArticle(
                        url = url,
                        title = title,
                        cleanContent = fallbackText,
                        excerpt = article.excerpt
                    )
                )
            }

            // Sanitiza espaçamentos excessivos
            val cleanContent = textContent
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")

            Result.success(
                ExtractedArticle(
                    url = url,
                    title = title,
                    cleanContent = cleanContent,
                    excerpt = article.excerpt
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair artigo da web: $url", e)
            Result.failure(e)
        }
    }
}

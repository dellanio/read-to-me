package com.readtome.app.data.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractedDocument(
    val fileName: String,
    val text: String,
    val pageCount: Int = 1
)

object DocumentReaderHelper {

    private const val TAG = "DocumentReaderHelper"
    private const val MAX_PDF_PAGES = 30 // Limite de segurança para processamento responsivo

    /**
     * Extrai o nome do arquivo a partir de sua Uri.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "Documento"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao obter nome do arquivo", e)
        }
        return name
    }

    /**
     * Extrai texto de um documento TXT ou PDF via Uri do Android Storage Access Framework.
     */
    suspend fun extractText(
        context: Context,
        uri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ExtractedDocument> = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: ""

        val isPdf = fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"
        val isTxt = fileName.endsWith(".txt", ignoreCase = true) || mimeType.startsWith("text/")

        if (isTxt) {
            extractTxt(context, uri, fileName)
        } else if (isPdf) {
            extractPdf(context, uri, fileName, onProgress)
        } else {
            // Tenta como texto primeiro, se falhar tenta como PDF
            try {
                extractTxt(context, uri, fileName)
            } catch (e: Exception) {
                extractPdf(context, uri, fileName, onProgress)
            }
        }
    }

    private fun extractTxt(context: Context, uri: Uri, fileName: String): Result<ExtractedDocument> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""

            if (content.isBlank()) {
                Result.failure(IllegalStateException("O arquivo de texto selecionado está vazio."))
            } else {
                Result.success(ExtractedDocument(fileName = fileName, text = content.trim(), pageCount = 1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler arquivo TXT", e)
            Result.failure(e)
        }
    }

    private suspend fun extractPdf(
        context: Context,
        uri: Uri,
        fileName: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<ExtractedDocument> {
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: return Result.failure(IllegalStateException("Não foi possível abrir o arquivo PDF."))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val stringBuilder = StringBuilder()
        var totalPages = 0

        return try {
            PdfRenderer(pfd).use { renderer ->
                totalPages = renderer.pageCount
                val pagesToProcess = minOf(totalPages, MAX_PDF_PAGES)

                for (pageIndex in 0 until pagesToProcess) {
                    onProgress(pageIndex + 1, pagesToProcess)

                    val page = renderer.openPage(pageIndex)
                    // Renderiza a página em uma resolução balanceada para OCR rápido e preciso
                    val scale = 2f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    try {
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val visionText = suspendCancellableCoroutine { continuation ->
                            recognizer.process(inputImage)
                                .addOnSuccessListener { textResult ->
                                    continuation.resume(textResult)
                                }
                                .addOnFailureListener { exception ->
                                    continuation.resumeWithException(exception)
                                }
                        }

                        if (visionText.text.isNotBlank()) {
                            if (stringBuilder.isNotEmpty()) {
                                stringBuilder.append("\n\n")
                            }
                            stringBuilder.append(visionText.text.trim())
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            val extracted = stringBuilder.toString().trim()
            if (extracted.isBlank()) {
                Result.failure(IllegalStateException("Nenhum texto legível foi encontrado nas páginas do PDF."))
            } else {
                Result.success(
                    ExtractedDocument(
                        fileName = fileName,
                        text = extracted,
                        pageCount = totalPages
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar PDF com OCR", e)
            Result.failure(e)
        } finally {
            try {
                pfd.close()
            } catch (_: Exception) {}
            recognizer.close()
        }
    }
}

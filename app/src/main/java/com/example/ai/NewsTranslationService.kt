package com.example.ai

import android.util.Log
import com.example.data.CachedArabicTranslation
import com.example.data.SettingsDataStore
import com.example.model.NewsArticle
import com.example.model.NewsTranslationStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

interface NewsTranslator {
    suspend fun translateArticlesToArabic(articles: List<NewsArticle>): List<NewsArticle>
}

class FirebaseAiNewsTranslator(
    private val settingsDataStore: SettingsDataStore? = null
) : NewsTranslator {

    companion object {
        private const val TAG = "NewsTranslationService"
        private const val MODEL_NAME = "gemini-3.5-flash-lite"
        private const val BATCH_SIZE = 8
        private const val TIMEOUT_MS = 45_000L
    }

    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<String, CachedArabicTranslation>()
    private var cacheHydrated = false

    private val model by lazy {
        val responseSchema = Schema.obj(
            mapOf(
                "translations" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "id" to Schema.string("Exact input article id"),
                            "titleAr" to Schema.string("Natural, clear Arabic translation of headline keeping game/character/studio/platform names intact"),
                            "summaryAr" to Schema.string("Natural, concise Arabic translation of summary without altering meaning or inventing facts")
                        )
                    )
                )
            )
        )

        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = responseSchema
            },
            systemInstruction = content {
                text(
                    "You are a professional Arabic gaming journalist translator for ZYVORA. " +
                        "Translate English gaming news headlines and short summaries into natural, clear Modern Standard Arabic. " +
                        "Preserve official proper nouns of games, characters, studios, publishers, consoles, and technical terms " +
                        "(e.g., PlayStation 5, Xbox Series X, Steam, Nintendo Switch, RTX, Battle Royale, Patch) without transliterating them awkwardly. " +
                        "Never invent facts, never exaggerate rumors, and never omit leak/unconfirmed qualifiers."
                )
            }
        )
    }

    override suspend fun translateArticlesToArabic(articles: List<NewsArticle>): List<NewsArticle> =
        withContext(Dispatchers.IO) {
            if (articles.isEmpty()) return@withContext emptyList()
            hydrateCacheIfNeeded()

            // First apply already cached or already Arabic translations
            val pending = mutableListOf<NewsArticle>()
            val resolvedMap = LinkedHashMap<String, NewsArticle>(articles.size)

            cacheMutex.withLock {
                for (article in articles) {
                    if (containsMostlyArabic(article.title)) {
                        resolvedMap[article.id] = article.copy(
                            titleAr = article.title,
                            summaryAr = article.summary,
                            translationStatus = NewsTranslationStatus.TRANSLATED
                        )
                        continue
                    }
                    val cached = memoryCache[article.id]
                    if (cached != null) {
                        resolvedMap[article.id] = article.copy(
                            titleAr = cached.titleAr,
                            summaryAr = cached.summaryAr,
                            translationStatus = NewsTranslationStatus.TRANSLATED
                        )
                    } else {
                        resolvedMap[article.id] = article
                        pending.add(article)
                    }
                }
            }

            if (pending.isEmpty()) {
                return@withContext articles.map { resolvedMap[it.id] ?: it }
            }

            // Translate in chunks with timeout and honest fallback on failure
            for (batch in pending.chunked(BATCH_SIZE)) {
                try {
                    val promptPayload = buildString {
                        append("Translate each item accurately into natural Arabic JSON:\n")
                        batch.forEach { art ->
                            append("ID: ${art.id}\n")
                            append("TITLE: ${art.title.take(220)}\n")
                            append("SUMMARY: ${art.summary.take(320)}\n---\n")
                        }
                    }

                    val response = withTimeout(TIMEOUT_MS) {
                        model.generateContent(promptPayload)
                    }
                    val rawJson = response.text?.trim().orEmpty()
                    if (rawJson.isBlank()) {
                        markBatchUnavailable(batch, resolvedMap)
                        continue
                    }

                    val parsed = parseStructuredTranslations(rawJson)
                    cacheMutex.withLock {
                        for (art in batch) {
                            val tr = parsed[art.id]
                            if (tr != null && tr.titleAr.isNotBlank()) {
                                memoryCache[art.id] = tr
                                resolvedMap[art.id] = art.copy(
                                    titleAr = tr.titleAr,
                                    summaryAr = tr.summaryAr.ifBlank { art.summary },
                                    translationStatus = NewsTranslationStatus.TRANSLATED
                                )
                            } else {
                                resolvedMap[art.id] = art.copy(
                                    translationStatus = NewsTranslationStatus.UNAVAILABLE
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) {
                        Log.e(TAG, "Firebase AI translation batch timed out after ${TIMEOUT_MS}ms", e)
                        markBatchUnavailable(batch, resolvedMap)
                    } else {
                        throw e
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase AI translation failed; preserving original English text", e)
                    markBatchUnavailable(batch, resolvedMap)
                }
            }

            cacheMutex.withLock {
                runCatching { settingsDataStore?.saveNewsTranslationCache(memoryCache) }
            }

            articles.map { resolvedMap[it.id] ?: it }
        }

    private fun markBatchUnavailable(
        batch: List<NewsArticle>,
        resolvedMap: MutableMap<String, NewsArticle>
    ) {
        for (art in batch) {
            if (resolvedMap[art.id]?.translationStatus != NewsTranslationStatus.TRANSLATED) {
                resolvedMap[art.id] = art.copy(translationStatus = NewsTranslationStatus.UNAVAILABLE)
            }
        }
    }

    private fun parseStructuredTranslations(rawJson: String): Map<String, CachedArabicTranslation> {
        val result = mutableMapOf<String, CachedArabicTranslation>()
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("translations") ?: return emptyMap()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            val titleAr = obj.optString("titleAr").trim()
            val summaryAr = obj.optString("summaryAr").trim()
            if (id.isNotBlank() && titleAr.isNotBlank()) {
                result[id] = CachedArabicTranslation(
                    titleAr = titleAr,
                    summaryAr = summaryAr.ifBlank { titleAr }
                )
            }
        }
        return result
    }

    private suspend fun hydrateCacheIfNeeded() {
        cacheMutex.withLock {
            if (cacheHydrated) return
            cacheHydrated = true
            val stored = runCatching { settingsDataStore?.loadNewsTranslationCache().orEmpty() }
                .getOrDefault(emptyMap())
            memoryCache.putAll(stored)
        }
    }

    private fun containsMostlyArabic(text: String): Boolean {
        val arabicCount = text.count { it in '\u0600'..'\u06FF' }
        return arabicCount >= (text.length / 4).coerceAtLeast(3)
    }
}

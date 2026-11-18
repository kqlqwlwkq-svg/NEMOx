package com.example.network

import android.util.Xml
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.NewsVerificationLevel
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class RssFeedSourceSpec(
    val id: String,
    val sourceName: String,
    val feedUrl: String,
    val isOfficial: Boolean,
    val defaultPlatformTags: List<String> = emptyList(),
    val defaultCategoryHint: NewsCategory? = null
)

object NewsRssParser {

    private val leakKeywords = listOf(
        "rumor", "rumour", "rumors", "rumours", "leak", "leaked", "leaks",
        "unconfirmed", "datamine", "datamined", "insider claims", "report claims",
        "reportedly", "allegedly", "speculation", "purported"
    )

    private val esportsKeywords = listOf(
        "esports", "e-sports", "tournament", "championship", "world cup", "major",
        "fncs", "vct", "worlds", "prize pool", "masters", "playoffs", "qualifier", "roster"
    )

    private val dealsKeywords = listOf(
        "free game", "free games", "giveaway", "free to keep", "free on epic",
        "discount", "sale", "bundle", "deal", "ps plus", "game pass", "prime gaming", "% off"
    )

    private val updateKeywords = listOf(
        "patch notes", "patch ", "update ", "hotfix", "title update", "season ",
        "balance changes", "maintenance", "changelog", "version ", "v1.", "v2.", "v3."
    )

    private val studioKeywords = listOf(
        "studio", "acquisition", "acquire", "layoff", "layoffs", "ceo", "financial",
        "earnings", "publisher", "closes", "shutdown", "subsidiary", "merger", "investment"
    )

    private val mobileKeywords = listOf(
        "android", "ios", "iphone", "ipad", "mobile", "google play", "app store",
        "pubg mobile", "cod mobile", "call of duty: mobile", "brawl stars", "free fire",
        "clash royale", "mobile legends", "honkai", "genshin impact"
    )

    private val consoleKeywords = listOf(
        "ps5", "ps4", "playstation", "xbox", "nintendo", "switch", "dualsense", "psvr"
    )

    private val pcKeywords = listOf(
        "steam", "pc ", "windows", "gpu", "rtx", "geforce", "nvidia", "radeon",
        "steam deck", "epic games store", "gog", "directx", "linux"
    )

    private val announceKeywords = listOf(
        "announce", "announced", "announcement", "reveal", "revealed", "trailer",
        "release date", "launch", "coming soon", "showcase", "state of play",
        "nintendo direct", "teaser", "new game", "sequel", "remake"
    )

    private val knownGames = listOf(
        "Genshin Impact", "Wuthering Waves", "Honkai: Star Rail", "PUBG Mobile",
        "PUBG", "Fortnite", "Minecraft", "Call of Duty", "Valorant", "League of Legends",
        "Counter-Strike 2", "Apex Legends", "Rocket League", "Marvel Rivals",
        "Elden Ring", "Cyberpunk 2077", "GTA", "Grand Theft Auto", "Brawl Stars",
        "Free Fire", "Blood Strike", "Monster Hunter", "Resident Evil", "Assassin's Creed",
        "EA SPORTS FC", "Overwatch", "Dota 2", "Halo", "Forza", "Mario", "Zelda", "Pokemon"
    )

    fun parseFeed(
        xmlContent: String,
        source: RssFeedSourceSpec,
        maxItems: Int = 14
    ): List<NewsArticle> {
        if (xmlContent.isBlank() || source.sourceName.isBlank()) return emptyList()
        val results = mutableListOf<NewsArticle>()

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent.trim()))

            var inEntry = false
            var isAtom = false
            var title = ""
            var link = ""
            var description = ""
            var pubDate = ""
            var updatedDate = ""
            var imageUrl: String? = null
            val categories = mutableListOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT && results.size < maxItems) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase(Locale.ROOT).orEmpty()
                        when (tag) {
                            "item", "entry" -> {
                                inEntry = true
                                isAtom = tag == "entry"
                                title = ""
                                link = ""
                                description = ""
                                pubDate = ""
                                updatedDate = ""
                                imageUrl = null
                                categories.clear()
                            }
                            "title" -> if (inEntry) title = safeNextText(parser)
                            "link" -> if (inEntry) {
                                val href = parser.getAttributeValue(null, "href")
                                val rel = parser.getAttributeValue(null, "rel")
                                if (!href.isNullOrBlank()) {
                                    if (rel == null || rel == "alternate") link = href.trim()
                                } else {
                                    val txt = safeNextText(parser)
                                    if (txt.isNotBlank()) link = txt.trim()
                                }
                            }
                            "description", "summary" -> if (inEntry && description.isBlank()) {
                                val raw = safeNextText(parser)
                                if (imageUrl == null) imageUrl = extractImgFromHtml(raw)
                                description = stripHtml(raw)
                            }
                            "content:encoded", "content" -> if (inEntry) {
                                val raw = safeNextText(parser)
                                if (imageUrl == null) imageUrl = extractImgFromHtml(raw)
                                if (description.isBlank()) description = stripHtml(raw)
                            }
                            "pubdate", "published", "dc:date" -> if (inEntry && pubDate.isBlank()) {
                                pubDate = safeNextText(parser)
                            }
                            "updated" -> if (inEntry) {
                                val txt = safeNextText(parser)
                                if (isAtom && pubDate.isBlank()) pubDate = txt
                                else updatedDate = txt
                            }
                            "category" -> if (inEntry) {
                                val term = parser.getAttributeValue(null, "term")
                                val label = term ?: safeNextText(parser)
                                if (label.isNotBlank()) categories.add(label.trim())
                            }
                            "media:content", "media:thumbnail", "enclosure" -> if (inEntry && imageUrl == null) {
                                val mediaUrl = parser.getAttributeValue(null, "url")?.trim()
                                val type = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT).orEmpty()
                                val medium = parser.getAttributeValue(null, "medium")?.lowercase(Locale.ROOT).orEmpty()
                                val looksImage = type.startsWith("image") ||
                                    medium == "image" ||
                                    tag == "media:thumbnail" ||
                                    (!mediaUrl.isNullOrBlank() && Regex("\\.(jpg|jpeg|png|webp)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(mediaUrl))
                                if (looksImage && isValidHttpUrl(mediaUrl)) {
                                    imageUrl = mediaUrl
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase(Locale.ROOT).orEmpty()
                        if ((tag == "item" || tag == "entry") && inEntry) {
                            inEntry = false
                            val cleanTitle = stripHtml(title).take(220)
                            val cleanLink = link.trim()
                            if (cleanTitle.isNotBlank() && isValidHttpUrl(cleanLink)) {
                                val publishedMillis = parseFlexibleDate(pubDate) ?: System.currentTimeMillis()
                                val updatedMillis = parseFlexibleDate(updatedDate)
                                val summaryText = description.ifBlank { cleanTitle }.take(280)
                                val classified = classifyArticle(
                                    title = cleanTitle,
                                    summary = summaryText,
                                    feedCategories = categories,
                                    source = source,
                                    publishedAtMillis = publishedMillis
                                )
                                val matchedGame = detectGameTitle(cleanTitle, summaryText)
                                val canonicalHash = canonicalizeUrl(cleanLink).hashCode().toUInt().toString(16)
                                results.add(
                                    NewsArticle(
                                        id = "${source.id}_$canonicalHash",
                                        title = cleanTitle,
                                        summary = summaryText,
                                        sourceName = source.sourceName,
                                        isOfficial = source.isOfficial,
                                        imageUrl = imageUrl,
                                        articleUrl = cleanLink,
                                        publishedAtMillis = publishedMillis,
                                        updatedAtMillis = updatedMillis,
                                        gameTitle = matchedGame ?: source.sourceName,
                                        gameKey = (matchedGame ?: source.id).lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_"),
                                        category = classified.first,
                                        verificationLevel = classified.second,
                                        platformTags = classified.third,
                                        leakConfidenceLabel = if (classified.second == NewsVerificationLevel.UNCONFIRMED_LEAK) {
                                            "Unconfirmed Report / Leak"
                                        } else null
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return results
    }

    fun classifyArticle(
        title: String,
        summary: String,
        feedCategories: List<String>,
        source: RssFeedSourceSpec,
        publishedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Triple<NewsCategory, NewsVerificationLevel, List<String>> {
        val haystack = (listOf(title, summary) + feedCategories).joinToString(" ").lowercase(Locale.ROOT)
        val hasLeakMarker = !source.isOfficial && leakKeywords.any { haystack.contains(it) }

        val verification = when {
            source.isOfficial -> NewsVerificationLevel.OFFICIAL
            hasLeakMarker -> NewsVerificationLevel.UNCONFIRMED_LEAK
            else -> NewsVerificationLevel.VERIFIED_MEDIA
        }

        val platforms = buildSet {
            addAll(source.defaultPlatformTags)
            if (mobileKeywords.any { haystack.contains(it) }) add("Mobile")
            if (haystack.contains("android")) add("Android")
            if (haystack.contains("ios") || haystack.contains("iphone")) add("iOS")
            if (pcKeywords.any { haystack.contains(it) }) add("PC")
            if (haystack.contains("playstation") || haystack.contains("ps5") || haystack.contains("ps4")) add("PlayStation")
            if (haystack.contains("xbox")) add("Xbox")
            if (haystack.contains("nintendo") || haystack.contains("switch")) add("Nintendo")
        }.toList()

        val category = when {
            hasLeakMarker -> NewsCategory.LEAKS_RUMORS
            source.defaultCategoryHint == NewsCategory.DEALS_FREE || dealsKeywords.any { haystack.contains(it) } ->
                NewsCategory.DEALS_FREE
            esportsKeywords.any { haystack.contains(it) } -> NewsCategory.ESPORTS_TOURNAMENTS
            updateKeywords.any { haystack.contains(it) } -> NewsCategory.GAME_UPDATES
            studioKeywords.any { haystack.contains(it) } -> NewsCategory.STUDIOS_INDUSTRY
            source.defaultCategoryHint == NewsCategory.MOBILE_GAMES ||
                (platforms.contains("Mobile") && !platforms.contains("PC") && !platforms.contains("PlayStation") && !platforms.contains("Xbox")) ->
                NewsCategory.MOBILE_GAMES
            source.defaultCategoryHint == NewsCategory.CONSOLE_GAMES ||
                consoleKeywords.any { haystack.contains(it) } -> NewsCategory.CONSOLE_GAMES
            source.defaultCategoryHint == NewsCategory.PC_GAMES ||
                pcKeywords.any { haystack.contains(it) } -> NewsCategory.PC_GAMES
            announceKeywords.any { haystack.contains(it) } -> NewsCategory.ANNOUNCEMENTS_RELEASES
            nowMillis - publishedAtMillis in 0L..(36 * 3_600_000L) -> NewsCategory.BREAKING
            else -> NewsCategory.ANNOUNCEMENTS_RELEASES
        }

        return Triple(category, verification, platforms)
    }

    fun deduplicateNews(articles: List<NewsArticle>): List<NewsArticle> {
        val valid = articles.filter {
            it.title.isNotBlank() &&
                it.sourceName.isNotBlank() &&
                isValidHttpUrl(it.articleUrl)
        }
        // Higher authority & newer first
        val prioritized = valid.sortedWith(
            compareByDescending<NewsArticle> {
                when (it.verificationLevel) {
                    NewsVerificationLevel.OFFICIAL -> 3
                    NewsVerificationLevel.VERIFIED_MEDIA -> 2
                    NewsVerificationLevel.UNCONFIRMED_LEAK -> 1
                }
            }.thenByDescending { it.publishedAtMillis }
        )

        val accepted = mutableListOf<NewsArticle>()
        val seenUrls = HashSet<String>()
        val seenIds = HashSet<String>()

        for (candidate in prioritized) {
            if (!seenIds.add(candidate.id)) continue
            val canonUrl = canonicalizeUrl(candidate.articleUrl)
            if (canonUrl.isNotBlank() && !seenUrls.add(canonUrl)) continue

            val candidateTokens = tokenizeTitle(candidate.title)
            val duplicateIdx = accepted.indexOfFirst { existing ->
                val existingTokens = tokenizeTitle(existing.title)
                jaccardSimilarity(candidateTokens, existingTokens) >= 0.72
            }
            if (duplicateIdx >= 0) {
                val existing = accepted[duplicateIdx]
                if (existing.imageUrl.isNullOrBlank() && !candidate.imageUrl.isNullOrBlank()) {
                    accepted[duplicateIdx] = existing.copy(imageUrl = candidate.imageUrl)
                }
            } else {
                accepted.add(candidate)
            }
        }

        return accepted.sortedWith(
            compareByDescending<NewsArticle> { it.publishedAtMillis / 86_400_000L }
                .thenByDescending { it.isOfficial }
                .thenByDescending { it.publishedAtMillis }
        )
    }

    fun isValidHttpUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        return trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
    }

    fun canonicalizeUrl(rawUrl: String): String {
        return runCatching {
            val uri = URI(rawUrl.trim())
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
            val path = uri.path?.trimEnd('/')?.lowercase(Locale.ROOT).orEmpty()
            if (host.isBlank()) rawUrl.lowercase(Locale.ROOT) else "$host$path"
        }.getOrDefault(rawUrl.trim().lowercase(Locale.ROOT))
    }

    private fun tokenizeTitle(title: String): Set<String> {
        val stopWords = setOf(
            "the", "and", "for", "with", "that", "this", "from", "into", "new",
            "game", "games", "now", "out", "will", "gets", "reveal", "announces"
        )
        return title.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        val union = a.size + b.size - intersection
        return if (union <= 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    private fun detectGameTitle(title: String, summary: String): String? {
        val combined = "$title $summary"
        return knownGames.firstOrNull { combined.contains(it, ignoreCase = true) }
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return runCatching {
            if (parser.next() == XmlPullParser.TEXT) {
                val res = parser.text ?: ""
                parser.nextTag()
                res
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun extractImgFromHtml(raw: String): String? {
        val regex = Regex("<img[^>]+src=[\"'](https?://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
        return regex.find(raw)?.groupValues?.getOrNull(1)?.takeIf { isValidHttpUrl(it) }
    }

    fun stripHtml(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("<(br|p|div|li)[^>]*>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .replace("&ndash;", "-")
            .replace("&mdash;", "—")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("&#8217;"), "'")
            .replace(Regex("&#8220;|&#8221;"), "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseFlexibleDate(raw: String): Long? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(clean)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}

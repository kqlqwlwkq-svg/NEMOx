package com.example.network

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class SteamPublicProfile(
    val steamId64: String,
    val personaName: String,
    val avatarUrl: String?,
    val onlineState: String?,
    val privacyState: String?,
    val hoursLastTwoWeeks: String?,
    val recentGamesSummary: String?
)

object SteamXmlParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun fetchPublicProfile(identifier: String): SteamPublicProfile? {
        val clean = identifier.trim().removePrefix("https://steamcommunity.com/id/")
            .removePrefix("https://steamcommunity.com/profiles/")
            .trimEnd('/')
        if (clean.isBlank()) return null

        val isNumeric64 = clean.length >= 16 && clean.all { it.isDigit() }
        val url = if (isNumeric64) {
            "https://steamcommunity.com/profiles/$clean/?xml=1"
        } else {
            "https://steamcommunity.com/id/$clean/?xml=1"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ZYVORAGamingHub/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val xml = response.body?.string() ?: return null
            if (xml.contains("<error>")) return null
            return parseXml(xml)
        }
    }

    private fun parseXml(xml: String): SteamPublicProfile? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var steamId64: String? = null
        var personaName: String? = null
        var avatarUrl: String? = null
        var onlineState: String? = null
        var privacyState: String? = null
        var hours2Wk: String? = null
        val recentGames = mutableListOf<String>()

        var currentTag = ""
        var inMostPlayedGame = false
        var currentGameName: String? = null
        var currentGameHours: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    if (currentTag == "mostPlayedGame") {
                        inMostPlayedGame = true
                        currentGameName = null
                        currentGameHours = null
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (!inMostPlayedGame) {
                            when (currentTag) {
                                "steamID64" -> if (steamId64 == null) steamId64 = text
                                "steamID" -> if (personaName == null) personaName = text
                                "avatarFull" -> if (avatarUrl == null) avatarUrl = text
                                "onlineState" -> if (onlineState == null) onlineState = text
                                "privacyState" -> if (privacyState == null) privacyState = text
                                "hoursPlayed2Wk" -> if (hours2Wk == null) hours2Wk = text
                            }
                        } else {
                            when (currentTag) {
                                "gameName" -> currentGameName = text
                                "hoursPlayed" -> currentGameHours = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "mostPlayedGame" && inMostPlayedGame) {
                        val gName = currentGameName
                        if (!gName.isNullOrBlank()) {
                            val hrs = currentGameHours?.let { " ($it h)" } ?: ""
                            recentGames.add("$gName$hrs")
                        }
                        inMostPlayedGame = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        val id64 = steamId64 ?: return null
        return SteamPublicProfile(
            steamId64 = id64,
            personaName = personaName ?: id64,
            avatarUrl = avatarUrl,
            onlineState = onlineState,
            privacyState = privacyState,
            hoursLastTwoWeeks = hours2Wk,
            recentGamesSummary = recentGames.take(3).joinToString(" • ").takeIf { it.isNotBlank() }
        )
    }
}

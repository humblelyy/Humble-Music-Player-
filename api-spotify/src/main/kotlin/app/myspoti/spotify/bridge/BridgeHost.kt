package app.myspoti.spotify.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Where the Web Player page runs: an Android WebView or desktop Chrome over CDP.
 *
 * A host loads open.spotify.com with the user's session, injects [BridgeScript] after each
 * page load, blocks ad audio ([AdBlock.blockedHosts]) and relays the page's messages.
 */
interface BridgeHost {
    /** Runs [script] in the page. Fire and forget: answers come back through [messages]. */
    suspend fun evaluate(script: String)

    /**
     * Runs [script] like [evaluate] and returns the JSON of its value once the page ran it, or
     * null when this host cannot tell. Throws when the script could not be handed to the page,
     * so a call fails at once instead of waiting for an answer that cannot come.
     */
    suspend fun evaluateValue(script: String): String? { evaluate(script); return null }

    /** JSON strings posted by the bridge (`MyspotiHost.postMessage` / `__mspHostPost`). */
    val messages: Flow<String>
}

/** The bridge's source, bundled as a resource of this module. */
object BridgeScript {
    val source: String by lazy {
        BridgeScript::class.java.getResourceAsStream("/bridge/player-bridge.js")!!
            .bufferedReader().use { it.readText() }
    }
}

/**
 * Audio ads. Spotify's server puts them in the player's queue: the track-playback state machine
 * it sends to the page about three minutes ahead, where the song before the ad `advance`s to an
 * ad state at its end. [withoutAds] sends that end to the song after the ad instead, before the
 * page sees the machine, so the page never reaches an ad. The ad audio host is refused as well,
 * and [app.myspoti.spotify.SpotifyPlayer] reloads the page if an ad still becomes current.
 */

object AdBlock {
    // Spotify playback and advertising are passed through normally.
    val blockedHosts = emptyList<String>()
    val blockedPaths = emptyList<String>()

    fun isBlocked(url: String): Boolean = false

    fun withoutAds(body: String): Pair<String, List<String>>? = null

    data class AdAhead(
        val songUid: String?,
        val songUri: String?,
        val nextUri: String
    )

    val ahead = MutableStateFlow<AdAhead?>(null)

    fun adAhead(body: String): AdAhead? = null

    fun isAdTrack(track: JsonObject?): Boolean = false

    fun whyKept(body: String): String? = null

    fun isDeviceRegistration(method: String, url: String): Boolean =
        method.equals("POST", ignoreCase = true) &&
                url.substringBefore('?').endsWith("/track-playback/v1/devices")

    fun withDeviceName(body: String, name: String): String = runCatching {
        val device = Json.parseToJsonElement(body).jsonObject["device"]?.jsonObject
            ?: return body
        val raw = device["name"] as? JsonPrimitive ?: return body
        val old = raw.content

        if (old.isBlank() || old == name || name.isBlank()) return body

        val quoted = name.replace("\\", "\\\\").replace("\"", "\\\"")
        body.replace("\"$old\"", "\"$quoted\"")
    }.getOrDefault(body)
}

object MediaManifests {
    private const val MAX = 200
    private val byUri = object : LinkedHashMap<String, JsonObject>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?) = size > MAX
    }

    /** Keeps the manifests of a track-playback reply's songs. */
    fun record(body: String) {
        if ("\"manifest\"" !in body) return
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return
        for (key in listOf("state_machine", "previous_state_machine")) {
            val tracks = (root[key] as? JsonObject)?.get("tracks") as? JsonArray ?: continue
            tracks.forEach { t ->
                val track = t as? JsonObject ?: return@forEach
                val uri = ((track["metadata"] as? JsonObject)?.get("uri") as? JsonPrimitive)?.contentOrNull ?: return@forEach
                val manifest = track["manifest"] as? JsonObject ?: return@forEach
                if (uri.startsWith("spotify:track:") && manifest.isNotEmpty()) synchronized(byUri) { byUri[uri] = manifest }
            }
        }
    }

    fun of(trackUri: String): JsonObject? = synchronized(byUri) { byUri[trackUri] }
}

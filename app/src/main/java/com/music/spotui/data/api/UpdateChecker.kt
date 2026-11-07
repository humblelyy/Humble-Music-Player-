
package com.music.spotui.data.api

import android.content.Context
import com.music.spotui.data.DataSaver
import app.myspoti.engine.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val UPDATE_URL =
        "https://humblemusic.vercel.app/update.json"

    private const val DOWNLOAD_PAGE =
        "https://humblemusic.vercel.app/download"

    data class Release(
        val version: String,
        val pageUrl: String,
        val notes: String = "",
        val apkUrl: String = ""
    )

    const val INTERVAL_MS = 3_600_000L
    const val PERIODIC_MS = 6 * 3_600_000L

    private const val RETRY_MS = 10 * 60_000L

    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(DataSaver.interceptor)
            .build()
    }

    sealed interface Outcome {
        data class Found(val release: Release) : Outcome
        data object UpToDate : Outcome
        data class Failed(val rateLimited: Boolean) : Outcome
    }

    private sealed interface Fetch {
        data class Ok(val release: Release?) : Fetch
        data class Err(val rateLimited: Boolean) : Fetch
    }

    fun lastChecked(context: Context): Long {
        return context.getSharedPreferences(
            "updates",
            Context.MODE_PRIVATE
        ).getLong("checked_at", 0L)
    }

    suspend fun check(
        context: Context,
        manual: Boolean = false
    ): Release? = withContext(Dispatchers.IO) {

        val prefs = context.getSharedPreferences(
            "updates",
            Context.MODE_PRIVATE
        )

        val now = System.currentTimeMillis()

        if (
            now - prefs.getLong("checked_at", 0L) < INTERVAL_MS ||
            now - prefs.getLong("tried_at", 0L) < RETRY_MS
        ) {
            return@withContext pending(prefs, manual)
        }

        store(prefs, fetch(force = false), now)

        pending(prefs, manual)
    }

    suspend fun refresh(context: Context): Outcome =
        withContext(Dispatchers.IO) {

            val prefs = context.getSharedPreferences(
                "updates",
                Context.MODE_PRIVATE
            )

            val result = fetch(force = true)

            store(
                prefs,
                result,
                System.currentTimeMillis()
            )

            when (result) {
                is Fetch.Err -> Outcome.Failed(result.rateLimited)

                is Fetch.Ok -> {
                    pending(prefs, manual = true)
                        ?.let { Outcome.Found(it) }
                        ?: Outcome.UpToDate
                }
            }
        }

    private fun store(
        prefs: android.content.SharedPreferences,
        result: Fetch,
        now: Long
    ) {
        val editor = prefs.edit()

        when (result) {
            is Fetch.Err -> {
                editor.putLong("tried_at", now)
            }

            is Fetch.Ok -> {
                editor.putLong("checked_at", now)
                editor.putLong("tried_at", 0L)

                editor.putString(
                    "version",
                    result.release?.version
                )

                editor.putString(
                    "url",
                    result.release?.pageUrl
                )

                editor.putString(
                    "notes",
                    result.release?.notes
                )

                editor.putString(
                    "apk",
                    result.release?.apkUrl
                )
            }
        }

        editor.apply()
    }

    private fun fetch(force: Boolean): Fetch = try {

        val url = if (force) {
            "$UPDATE_URL?_=${System.currentTimeMillis()}"
        } else {
            UPDATE_URL
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (force) {
                    header(
                        "Cache-Control",
                        "no-cache, no-store, max-age=0"
                    )
                    header("Pragma", "no-cache")
                }
            }
            .build()

        http.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                return@use Fetch.Err(
                    response.code == 429
                )
            }

            val body = response.body?.string()
                ?: return@use Fetch.Err(false)

            val json = JSONObject(body)

            val latestVersion =
                json.optString("latestVersion").trim()

            if (latestVersion.isBlank()) {
                return@use Fetch.Err(false)
            }

            val downloadPage =
                json.optString("downloadPage")
                    .takeIf { it.isNotBlank() }
                    ?: DOWNLOAD_PAGE

            val apkUrl =
                json.optString("apkUrl").trim()

            val releaseDate =
                json.optString("releaseDate").trim()

            val changelog = json.optJSONArray("changelog")

            val notes = buildString {
                if (releaseDate.isNotBlank()) {
                    append("Release date: ")
                    append(releaseDate)
                    append("\n\n")
                }

                if (changelog != null) {
                    for (i in 0 until changelog.length()) {
                        val item = changelog.optString(i)
                        if (item.isNotBlank()) {
                            append("• ")
                            append(item)
                            append("\n")
                        }
                    }
                }
            }.trim()

            val release = Release(
                version = latestVersion,
                pageUrl = downloadPage,
                notes = notes,
                apkUrl = apkUrl
            )

            Fetch.Ok(release)
        }

    } catch (e: Exception) {
        Fetch.Err(false)
    }

    fun known(context: Context): Release? {
        return pending(
            context.getSharedPreferences(
                "updates",
                Context.MODE_PRIVATE
            ),
            manual = true
        )
    }

    private fun pending(
        prefs: android.content.SharedPreferences,
        manual: Boolean = false
    ): Release? {

        val version = prefs.getString("version", null)
            ?: return null

        val url = prefs.getString("url", null)
            ?: return null

        if (
            !manual &&
            prefs.getString("dismissed", null) == version
        ) {
            return null
        }

        return Release(
            version = version,
            pageUrl = url,
            notes = prefs.getString("notes", null).orEmpty(),
            apkUrl = prefs.getString("apk", null).orEmpty()
        ).takeIf {
            isNewer(version, BuildConfig.VERSION_NAME)
        }
    }

    fun dismiss(
        context: Context,
        release: Release
    ) {
        context.getSharedPreferences(
            "updates",
            Context.MODE_PRIVATE
        ).edit()
            .putString("dismissed", release.version)
            .apply()
    }

    val isBeta: Boolean
        get() = BuildConfig.VERSION_NAME.contains("-beta")

    fun isNewer(
        candidate: String,
        current: String
    ): Boolean {

        fun parts(version: String): List<Int> {
            return version
                .substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
        }

        val a = parts(candidate)
        val b = parts(current)

        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }

            if (x != y) {
                return x > y
            }
        }

        fun betaNumber(version: String): Int {
            return if (!version.contains("-")) {
                Int.MAX_VALUE
            } else {
                Regex("(\\d+)$")
                    .find(version)
                    ?.value
                    ?.toIntOrNull()
                    ?: 0
            }
        }

        return betaNumber(candidate) > betaNumber(current)
    }
}

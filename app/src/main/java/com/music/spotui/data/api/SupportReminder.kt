
package com.music.spotui.data.api

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref

/**
 * Donations and their thank-yous.
 *
 * Reminders: a day after first use, then every 8 to 12 days.
 * Someone who says they supported once gets a thank-you reminder
 * every 3 months. Monthly supporters do not receive reminders.
 */
object SupportReminder {

    const val URL =
        "upi://pay?pa=maheshmadhav620@okhdfcbank&pn=Mahesh%20Madhav&tn=Support%20HUMBLE%20MUSIC&cu=INR"

    const val SPONSORS_URL = "https://github.com/humblelyy"

    const val FIRST_AFTER_MS = 24L * 3_600_000

    const val PERKS_ENABLED = false

    const val EVERY_MS = 8L * 24 * 3_600_000

    const val SPREAD_MS = 4L * 24 * 3_600_000

    const val SUPPORTER_EVERY_MS = 90L * 24 * 3_600_000

    private const val PREFS = "support_reminder"

    private const val KEY_ACCENT = "accent"
    private const val KEY_ACCENT_CLASSIC = "accent_classic"
    private const val KEY_ACCENT_IMMERSIVE = "accent_immersive"

    enum class Level {
        NONE,
        ONCE,
        MONTHLY
    }

    /**
     * Supporter accent colours.
     */
    enum class Accent(val key: String, val color: Color) {
        GREEN("green", Color(0xFF1ED760)),
        PINK("pink", Color(0xFFF0529B)),
        OLIVE("olive", Color(0xFF969447))
    }

    private var storedLevel by mutableStateOf(Level.NONE)

    val level: Level
        get() = if (PERKS_ENABLED) storedLevel else Level.NONE

    var classicAccent by mutableStateOf(Accent.GREEN)
        private set

    var immersiveAccent by mutableStateOf(Accent.GREEN)
        private set

    var immersiveAccentChosen by mutableStateOf(false)
        private set

    var icon by mutableStateOf(Accent.GREEN)
        private set

    /**
     * Accent for the selected look.
     */
    fun accentOf(look: Look): Accent {
        return if (look == Look.IMMERSIVE) {
            immersiveAccent
        } else {
            classicAccent
        }
    }

    val accentColor: Color
        get() = accentOf(LookPref.look).color

    /**
     * Calculates when the next reminder should appear.
     */
    fun nextAt(
        firstSeen: Long,
        lastShown: Long,
        level: Level,
        supportedAt: Long
    ): Long {
        return when (level) {
            Level.MONTHLY -> Long.MAX_VALUE

            Level.ONCE ->
                maxOf(lastShown, supportedAt) + SUPPORTER_EVERY_MS

            Level.NONE ->
                if (lastShown == 0L) {
                    firstSeen + FIRST_AFTER_MS
                } else {
                    lastShown + EVERY_MS + spread(firstSeen, lastShown)
                }
        }
    }

    /**
     * Calculates a consistent random delay for each reminder.
     */
    internal fun spread(firstSeen: Long, lastShown: Long): Long {
        return java.util.Random(firstSeen * 31 + lastShown)
            .nextLong()
            .mod(SPREAD_MS)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Loads saved support preferences.
     */
    fun init(context: Context) {
        val p = prefs(context)

        storedLevel = runCatching {
            Level.valueOf(p.getString("level", "NONE")!!)
        }.getOrDefault(Level.NONE)

        // Migrate the previous single accent preference.
        if (
            p.contains(KEY_ACCENT) &&
            !p.contains(KEY_ACCENT_CLASSIC) &&
            !p.contains(KEY_ACCENT_IMMERSIVE)
        ) {
            val old = p.getString(KEY_ACCENT, "green")

            p.edit()
                .putString(KEY_ACCENT_CLASSIC, old)
                .putString(KEY_ACCENT_IMMERSIVE, old)
                .apply()
        }

        classicAccent =
            Accent.entries.firstOrNull {
                it.key == p.getString(KEY_ACCENT_CLASSIC, "green")
            } ?: Accent.GREEN

        immersiveAccent =
            Accent.entries.firstOrNull {
                it.key == p.getString(KEY_ACCENT_IMMERSIVE, "green")
            } ?: Accent.GREEN

        immersiveAccentChosen = p.contains(KEY_ACCENT_IMMERSIVE)

        icon =
            Accent.entries.firstOrNull {
                it.key == p.getString("icon", "green")
            } ?: Accent.GREEN
    }

    /**
     * Checks whether a support reminder is due.
     */
    fun isDue(
        context: Context,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val p = prefs(context)

        val firstSeen = p.getLong("first_seen", 0L)
            .takeIf { it > 0 }
            ?: now.also {
                p.edit().putLong("first_seen", it).apply()
            }

        return now >= nextAt(
            firstSeen,
            p.getLong("last_shown", 0L),
            level,
            p.getLong("supported_at", 0L)
        )
    }

    /**
     * Records that a reminder was shown.
     */
    fun shown(context: Context) {
        prefs(context)
            .edit()
            .putLong("last_shown", System.currentTimeMillis())
            .apply()
    }

    /**
     * Records the support level selected by the user.
     */
    fun setLevel(context: Context, value: Level) {
        storedLevel = value

        prefs(context)
            .edit()
            .putString("level", value.name)
            .putLong("supported_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * Changes the accent colour for a look.
     */
    fun setAccent(
        context: Context,
        look: Look,
        value: Accent
    ) {
        if (look == Look.IMMERSIVE) {
            immersiveAccent = value
            immersiveAccentChosen = true

            prefs(context)
                .edit()
                .putString(KEY_ACCENT_IMMERSIVE, value.key)
                .apply()
        } else {
            classicAccent = value

            prefs(context)
                .edit()
                .putString(KEY_ACCENT_CLASSIC, value.key)
                .apply()
        }
    }

    /**
     * Changes the launcher icon.
     */
    fun setIcon(context: Context, value: Accent) {
        // Launcher aliases are disabled while perks are off.
        if (!PERKS_ENABLED || value == icon) return

        val pm = context.packageManager

        for (a in Accent.entries) {
            val state =
                if (a == value) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

            pm.setComponentEnabledSetting(
                ComponentName(context, aliasOf(a)),
                state,
                PackageManager.DONT_KILL_APP
            )
        }

        icon = value

        prefs(context)
            .edit()
            .putString("icon", value.key)
            .apply()
    }

    private fun aliasOf(a: Accent): String {
        return "app.myspoti.engine.ui.Launcher" +
                a.key.replaceFirstChar { it.uppercase() }
    }

    /**
     * Opens the UPI payment chooser safely.
     * If no compatible payment app is installed,
     * HUMBLE MUSIC stays open and displays a message.
     */
    fun open(context: Context) {
        val paymentIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(URL)
        )

        try {
            val chooser = Intent.createChooser(
                paymentIntent,
                "Support HUMBLE MUSIC"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooser)

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No UPI app found. Install Google Pay, PhonePe or another UPI app.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open UPI payment. Please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Opens the HUMBLE GitHub page.
     */
    fun openSponsors(context: Context) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(SPONSORS_URL)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)

        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Unable to open the link.",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Something went wrong.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

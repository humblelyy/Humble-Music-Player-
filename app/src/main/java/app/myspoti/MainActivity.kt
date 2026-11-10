
package app.myspoti

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.myspoti.engine.R
import app.myspoti.host.android.LoginLoad
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.host.android.SessionWait
import com.music.spotui.App
import com.music.spotui.di.AutoBrowser
import com.music.spotui.ui.screens.WelcomeScreen
import com.music.spotui.ui.theme.SpotuiTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * How long the player page is given to prove the session (a non-anonymous token) once it comes back.
 * The sign-in page stays in front while this runs — the address alone is never taken for a session —
 * and only a real token hands the screen to the player (see [MainActivity.watchLoginReturn]).
 */
private const val SESSION_WAIT_MS = 6_000L

/** The most a returning page may take to get its bridge ready before the wait gives up anyway. */
private const val SESSION_LOAD_MAX_MS = 60_000L

/**
 * The app's only screen host: the Compose UI, with the Web Player page attached behind it.
 * The page comes to the front only to sign in, on Spotify's own login pages.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private var signedIn by mutableStateOf(false)
    private var showingLogin by mutableStateOf(false)
    /** Back from Spotify's login, waiting for the session: a native loading screen, not the web page. */
    private var connecting by mutableStateOf(false)
    /** A pending wait for the session to prove itself after the login page came back. */
    private var loginWait: kotlinx.coroutines.Job? = null
    /** The wait ended with a player that never became ready: an error with Try again, not another spinner. */
    private var loginProblem by mutableStateOf(false)
    /** The one-time setup screen (permissions, battery) after signing in. */
    private var setupNeeded by mutableStateOf(false)
    /** In the picture-in-picture window: only the music video is drawn. */
    private var inPip by mutableStateOf(false)
    /** Show the branded in-app splash only on a fresh activity launch. */
    private var launchSplashVisible by mutableStateOf(false)
    /** Debug builds: `am start ... --ez showLook true` opens the look step alone, signed in or not. */
    private var showLook by mutableStateOf(false)
    /** The "new look" popup was already shown (or the onboarding look step covered it). */
    private var lookOffered by mutableStateOf(true)

    private val prefs get() = getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchSplashVisible = savedInstanceState == null
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = orientationFor(resources.configuration)
        signedIn = prefs.getBoolean("LoggedIn", false)
        setupNeeded = com.music.spotui.ui.screens.Setup.needed(this)
        lookOffered = com.music.spotui.data.preferences.LookPref.isOffered(this)
        root = FrameLayout(this)

        val compose = ComposeView(this).apply {
            setContent {
                SpotuiTheme {
                    com.music.spotui.ui.look.ProvideLook {
                        com.music.spotui.ui.components.ProvideWindowSize {
                            val orientation = com.music.spotui.data.preferences.OrientationPref.mode
                            val config = androidx.compose.ui.platform.LocalConfiguration.current

                            androidx.compose.runtime.LaunchedEffect(
                                orientation,
                                config.smallestScreenWidthDp,
                                config.screenLayout
                            ) {
                                requestedOrientation = orientationFor(config)
                            }

                            androidx.compose.foundation.layout.Box(
                                androidx.compose.ui.Modifier.fillMaxSize()
                            ) {
                                when {
                                    showLook -> com.music.spotui.ui.look.LookStep(onNext = {
                                        com.music.spotui.data.preferences.LookPref.setChosen(this@MainActivity)
                                        showLook = false
                                        setupNeeded = com.music.spotui.ui.screens.Setup.needed(this@MainActivity)
                                    })

                                    signedIn && setupNeeded -> com.music.spotui.ui.screens.SetupScreen(
                                        onDone = { setupNeeded = false }
                                    )

                                    signedIn -> App()
                                    connecting -> ConnectingScreen()
                                    else -> WelcomeScreen(onLogin = { showLogin() })
                                }

                                if (showingLogin) {
                                    val load by MyspotiRuntime.host.loginLoad.collectAsState()
                                    when {
                                        load == LoginLoad.FAILED || loginProblem ->
                                            LoginProblemScreen(onRetry = {
                                                if (load == LoginLoad.FAILED) {
                                                    MyspotiRuntime.host.retryLogin()
                                                } else {
                                                    loginProblem = false
                                                    MyspotiRuntime.host.openLogin()
                                                }
                                            })

                                        load == LoginLoad.LOADING -> ConnectingScreen()
                                    }
                                }

                                if (
                                    signedIn && !setupNeeded && !showLook &&
                                    !showingLogin && !inPip && !lookOffered
                                ) {
                                    if (
                                        com.music.spotui.data.preferences.LookPref.look ==
                                        com.music.spotui.data.preferences.Look.IMMERSIVE
                                    ) {
                                        androidx.compose.runtime.LaunchedEffect(Unit) {
                                            com.music.spotui.data.preferences.LookPref.setOffered(this@MainActivity)
                                            lookOffered = true
                                        }
                                    } else {
                                        val close = { look: com.music.spotui.data.preferences.Look ->
                                            com.music.spotui.data.preferences.LookPref.set(this@MainActivity, look)
                                            com.music.spotui.data.preferences.LookPref.setOffered(this@MainActivity)
                                            lookOffered = true
                                        }

                                        val keep = { close(com.music.spotui.data.preferences.Look.CLASSIC) }

                                        androidx.compose.ui.window.Dialog(
                                            onDismissRequest = keep,
                                            properties = androidx.compose.ui.window.DialogProperties(
                                                usePlatformDefaultWidth = false,
                                                decorFitsSystemWindows = false
                                            )
                                        ) {
                                            com.music.spotui.ui.look.LookStep(
                                                onNext = {
                                                    close(com.music.spotui.data.preferences.Look.IMMERSIVE)
                                                },
                                                offer = com.music.spotui.ui.look.LookOffer(onKeep = keep)
                                            )
                                        }
                                    }
                                }

                                com.music.spotui.ui.components.DownloadAskHost()
                                com.music.spotui.extensions.ExtensionConfirmHost()
                                com.music.spotui.extensions.ExtensionAuthHost()

                                if (inPip) {
                                    com.music.spotui.ui.components.PipVideo()
                                }

                                // Full-width branded splash. Android's system splash icon
                                // is intentionally transparent because it crops wide wordmarks.
                                if (launchSplashVisible) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = launchSplashVisible,
                                        enter = androidx.compose.animation.fadeIn(
                                            animationSpec = androidx.compose.animation.core.tween(250)
                                        ),
                                        exit = androidx.compose.animation.fadeOut(
                                            animationSpec = androidx.compose.animation.core.tween(500)
                                        ),
                                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = androidx.compose.ui.Modifier
                                                .fillMaxSize()
                                                .background(androidx.compose.ui.graphics.Color.Black),
                                            contentAlignment = androidx.compose.ui.Alignment.Center
                                        ) {
                                            androidx.compose.foundation.layout.Column(
                                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(26.dp)
                                            ) {
                                                androidx.compose.foundation.Image(
                                                    painter = androidx.compose.ui.res.painterResource(
                                                        R.drawable.humble_s1_splash
                                                    ),
                                                    contentDescription = "HUMBLE",
                                                    modifier = androidx.compose.ui.Modifier
                                                        .fillMaxWidth(0.86f)
                                                        .height(112.dp),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                )

                                                androidx.compose.material3.CircularProgressIndicator(
                                                    modifier = androidx.compose.ui.Modifier.size(28.dp),
                                                    color = androidx.compose.ui.graphics.Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }

                                    androidx.compose.runtime.LaunchedEffect(Unit) {
                                        kotlinx.coroutines.delay(2_000)
                                        launchSplashVisible = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        root.addView(
            compose,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        com.music.spotui.di.Haptics.attach(compose)

        val debuggable = applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

        MyspotiRuntime.host.attach(
            root,
            visible = debuggable && intent.getBooleanExtra("showPlayer", false)
        )

        if (!signedIn && MyspotiRuntime.loginRequired.value) {
            showingLogin = true
            MyspotiRuntime.host.attach(root, visible = true)
        }

        showLook = debuggable && intent.getBooleanExtra("showLook", false)

        if (debuggable && intent.getBooleanExtra("resetLookOffer", false)) {
            com.music.spotui.data.preferences.LookPref.clearOffered(this)
            lookOffered = false
        }

        watchSession()
        watchLoginReturn()
        watchLogout()

        if (savedInstanceState == null) handle(intent)
    }

    private fun orientationFor(config: android.content.res.Configuration): Int {
        val large = config.smallestScreenWidthDp >= 600 ||
                isInMultiWindowMode ||
                isDesktopMode(config)

        return if (large) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            com.music.spotui.data.preferences.OrientationPref.requested()
        }
    }

    private fun isDesktopMode(config: android.content.res.Configuration): Boolean {
        val dex = runCatching {
            val c = config.javaClass
            c.getField("SEM_DESKTOP_MODE_ENABLED").getInt(c) ==
                    c.getField("semDesktopModeEnabled").getInt(config)
        }.getOrDefault(false)

        return dex ||
                (config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_DESK
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (signedIn && !inPip && handleShortcut(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (
            event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.isCtrlPressed &&
            signedIn &&
            !inPip &&
            (
                    event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                            event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    ) &&
            handleShortcut(event.keyCode, event)
        ) return true

        return super.dispatchKeyEvent(event)
    }

    private fun handleShortcut(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val player = com.music.spotui.di.SongPlayer

        if (com.music.spotui.di.Graph.currentSongState.title.value.isBlank()) return false

        when (keyCode) {
            android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.repeatCount == 0) {
                    if (player.isPlaying()) player.pause(by = "keyboard")
                    else player.play(by = "keyboard")
                }
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY ->
                if (event.repeatCount == 0) player.play(by = "keyboard")

            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE ->
                if (event.repeatCount == 0) player.pause(by = "keyboard")

            android.view.KeyEvent.KEYCODE_MEDIA_NEXT ->
                if (event.repeatCount == 0) player.next(by = "keyboard")

            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                if (event.repeatCount == 0) player.previous(by = "keyboard")

            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> player.skipBy(-10_000L)
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> player.skipBy(10_000L)
            else -> return false
        }

        return true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        val mv = com.music.spotui.video.MusicVideoController

        if (
            !com.music.spotui.data.preferences.MusicVideoPref.on ||
            !mv.on.value ||
            mv.player == null
        ) return

        if (!com.music.spotui.di.SongPlayer.isPlaying() || isInPictureInPictureMode) return

        val ratio = mv.aspect.floatValue.coerceIn(0.5f, 2.3f)

        runCatching {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(
                        android.util.Rational((ratio * 1000).toInt(), 1000)
                    )
                    .build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        com.music.spotui.video.MusicVideoController.inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        if (
            com.music.spotui.data.api.UpdateChecker.isBeta ||
            app.myspoti.engine.BuildConfig.DEBUG
        ) {
            app.myspoti.host.android.MainThreadWatch.start()
        }

        super.onStart()
        com.music.spotui.video.MusicVideoController.onForeground()
        com.music.spotui.di.SongPlayer.refreshLikedIfStale()

        if (prefs.getBoolean("keep_screen_on", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onStop() {
        super.onStop()
        MyspotiRuntime.host.flushCookies()
        app.myspoti.host.android.MainThreadWatch.stop()

        if (!isChangingConfigurations) {
            com.music.spotui.video.MusicVideoController.onBackground()
        }
    }

    override fun onDestroy() {
        MyspotiRuntime.host.detach()
        super.onDestroy()
    }

    private fun showLogin() {
        showingLogin = true
        MyspotiRuntime.host.attach(root, visible = true)
        MyspotiRuntime.host.openLogin()
    }

    private fun hideLogin() {
        showingLogin = false
        loginProblem = false
        MyspotiRuntime.host.attach(root, visible = false)
    }

    private fun sessionConfirmed() {
        if (isFinishing) return
        loginProblem = false

        if (com.music.spotui.di.SessionActions.signedOut.value) {
            com.music.spotui.di.SessionActions.signedIn()
            prefs.edit().putBoolean("LoggedIn", true).apply()
            MyspotiRuntime.host.flushCookies()
            MyspotiRuntime.host.attach(root, visible = false)

            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
            )

            finish()
            return
        }

        com.music.spotui.data.api.ProfileCache.ensure(applicationContext)

        if (!signedIn || showingLogin || connecting) {
            Logbook.log("session established: player shown")
            MyspotiRuntime.host.flushCookies()
        }

        if (!signedIn) {
            prefs.edit().putBoolean("LoggedIn", true).apply()
            signedIn = true
        }

        connecting = false

        if (showingLogin) hideLogin()
    }

    private fun watchLogout() {
        lifecycleScope.launch {
            com.music.spotui.di.SessionActions.signedOut.collect { out ->
                if (out && signedIn) {
                    signedIn = false
                    showingLogin = true
                    MyspotiRuntime.host.attach(root, visible = true)
                }
            }
        }
    }

    private fun watchLoginReturn() {
        lifecycleScope.launch {
            MyspotiRuntime.host.url.collect { url ->
                val backOnPlayer =
                    url.startsWith("https://open.spotify.com") &&
                            !url.contains("/login")

                if (!(showingLogin && backOnPlayer)) {
                    if (
                        showingLogin &&
                        connecting &&
                        MyspotiRuntime.host.loginRequired.value
                    ) {
                        loginWait?.cancel()
                        connecting = false
                        MyspotiRuntime.host.veil(false)
                    }
                    return@collect
                }

                connecting = true
                loginProblem = false
                MyspotiRuntime.host.veil(true)

                loginWait?.cancel()

                loginWait = launch {
                    val wait = SessionWait(SESSION_WAIT_MS, SESSION_LOAD_MAX_MS)
                    var last = android.os.SystemClock.elapsedRealtime()

                    while (!wait.expired) {
                        if (runCatching { MyspotiRuntime.isSignedIn() }.getOrDefault(false)) {
                            sessionConfirmed()
                            return@launch
                        }

                        kotlinx.coroutines.delay(500)

                        val now = android.os.SystemClock.elapsedRealtime()

                        wait.tick(
                            now - last,
                            lifecycle.currentState.isAtLeast(
                                androidx.lifecycle.Lifecycle.State.RESUMED
                            ),
                            MyspotiRuntime.spotify.ready.value
                        )

                        last = now
                    }

                    connecting = false

                    if (wait.playerNeverReady) {
                        Logbook.log(
                            "no session after ${wait.elapsedMs / 1000} s: " +
                                    "the player never became ready (waited ${wait.waitedMs / 1000} s with it ready)"
                        )
                        loginProblem = true
                    } else {
                        Logbook.log(
                            "no session ${SESSION_WAIT_MS / 1000} s after the player came back: sign-in page restored"
                        )

                        if (!showingLogin) showLogin()
                        else MyspotiRuntime.host.openLogin()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!showingLogin) return

        lifecycleScope.launch {
            if (MyspotiRuntime.awaitSignedIn(1_500)) {
                Logbook.log("back with a session")
                sessionConfirmed()
            } else if (
                MyspotiRuntime.spotify.ready.value &&
                MyspotiRuntime.host.url.value.let {
                    it.startsWith("https://open.spotify.com") && !it.contains("/login")
                }
            ) {
                Logbook.log("back without a session: sign-in page restored")
                MyspotiRuntime.host.openLogin()
            }
        }
    }

    private fun watchSession() {
        val api = MyspotiRuntime.spotify

        lifecycleScope.launch {
            combine(
                api.ready,
                MyspotiRuntime.loginRequired
            ) { ready, login ->
                ready to login
            }.collect { (ready, login) ->

                when {
                    login && !showingLogin && signedIn -> {
                        com.music.spotui.di.SessionActions.sessionLost()
                        showLogin()
                    }

                    ready && !login -> {
                        val user = runCatching {
                            MyspotiRuntime.isSignedIn()
                        }.getOrDefault(false)

                        if (user) {
                            sessionConfirmed()
                        } else if (signedIn) {
                            Logbook.log(
                                "anonymous token while the app thought it was signed in: back to the sign-in page"
                            )

                            prefs.edit().putBoolean("LoggedIn", false).apply()
                            signedIn = false
                            connecting = false
                            com.music.spotui.di.SessionActions.sessionLost()
                            showLogin()
                        }
                    }
                }
            }
        }
    }

    private fun handle(intent: Intent?) {
        intent ?: return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val link = intent.data?.toString() ?: return

                if (com.music.spotui.data.api.InviteLinks.handle(link)) return

                val uri = spotifyUri(link) ?: return

                if (
                    uri.split(':').getOrNull(1) in
                    setOf("playlist", "album", "artist", "show")
                ) {
                    com.music.spotui.data.api.InviteLinks.page.value = uri
                    return
                }

                if (
                    uri.startsWith("spotify:track:") &&
                    com.music.spotui.extensions.Extensions.hasAudioSource()
                ) {
                    com.music.spotui.di.SongPlayer.playSong(uri)
                    return
                }

                lifecycleScope.launch {
                    MyspotiRuntime.api.ready.first { it }
                    runCatching { MyspotiRuntime.api.player.play(uri) }
                }
            }

            android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = intent.getStringExtra(
                    android.app.SearchManager.QUERY
                ).orEmpty()

                if (query.isNotBlank()) {
                    lifecycleScope.launch {
                        AutoBrowser.playFromSearch(query)
                    }
                }
            }
        }
    }

    private fun spotifyUri(link: String): String? {
        val m = Regex(
            "(?:open|www|play)\\.spotify\\.com/(?:intl-[a-z-]+/)?(track|album|playlist|artist|show|episode)/([A-Za-z0-9]{22})"
        ).find(link) ?: return null

        return "spotify:${m.groupValues[1]}:${m.groupValues[2]}"
    }
}

/** Shown between Spotify's login page and the first screen. */
@androidx.compose.runtime.Composable
private fun ConnectingScreen() {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(com.music.spotui.ui.theme.AppBackground),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = com.music.spotui.ui.theme.AppAccent
        )
    }
}

/** The sign-in page did not come (or the player did not start after it): what happened, and a way to go again. */
@androidx.compose.runtime.Composable
private fun LoginProblemScreen(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(com.music.spotui.ui.theme.AppBackground)
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.login_failed_title),
            color = com.music.spotui.ui.theme.AppText,
            fontFamily = com.music.spotui.ui.theme.AppSans,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Text(
            stringResource(R.string.login_failed_detail),
            color = com.music.spotui.ui.theme.AppText.copy(alpha = 0.6f),
            fontFamily = com.music.spotui.ui.theme.AppSans,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .background(com.music.spotui.ui.theme.AppAccent)
                .clickable(onClick = onRetry)
        ) {
            Text(
                stringResource(R.string.retry_button),
                color = Color.Black,
                fontFamily = com.music.spotui.ui.theme.AppSans,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }
}

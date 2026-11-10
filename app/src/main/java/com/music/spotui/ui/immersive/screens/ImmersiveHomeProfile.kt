
/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt at edc1e6f.
 * The sheet's rows are those of Myspoti's Classic Home profile sheet. Modified for Myspoti, 2026-10.
 */
package com.music.spotui.ui.immersive.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.music.spotui.data.api.ProfileCache
import com.music.spotui.data.api.SupportReminder
import com.music.spotui.di.Graph
import com.music.spotui.di.SessionActions
import com.music.spotui.ui.components.AboutSheet
import com.music.spotui.ui.components.SupporterBadge
import com.music.spotui.ui.components.SupporterSheet
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.components.ImmersiveCover
import com.music.spotui.ui.immersive.components.immersiveClickable
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.profileRoute

@Composable
internal fun ProfileDisc(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val avatar = ProfileCache.imageUrl
    val initial = ProfileCache.name?.trim()?.firstOrNull()?.uppercase() ?: "•"

    AvatarDisc(
        url = avatar,
        initial = initial,
        contentDescription = stringResource(R.string.recently_played_user_profile),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun AvatarDisc(
    url: String?,
    initial: String,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .let {
                if (onClick != null) {
                    it.immersiveClickable(onClick = onClick)
                } else {
                    it
                }
            }
            .clip(CircleShape)
            .background(Immersive.Accent),
    ) {
        if (!url.isNullOrBlank()) {
            ImmersiveCover(
                url = url,
                modifier = Modifier.size(size),
                shape = CircleShape,
                contentDescription = contentDescription,
                border = false,
            )
        } else {
            Text(
                text = initial,
                style = Immersive.BodyStrong.copy(
                    color = Immersive.Page,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/**
 * Profile sheet: account name and picture, settings, about,
 * support, extensions and sign-out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImmersiveProfileSheet(
    onOpen: (route: String) -> Unit,
    onDismiss: () -> Unit
) {
    val name = ProfileCache.name.orEmpty()
    val avatar = ProfileCache.imageUrl
    val myId = ProfileCache.id
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Immersive.Raised,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // Account header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = myId != null,
                        role = Role.Button
                    ) {
                        myId?.let {
                            onOpen(profileRoute(it, name))
                        }
                    }
                    .padding(
                        horizontal = Immersive.Gutter,
                        vertical = 8.dp
                    ),
            ) {
                AvatarDisc(
                    url = avatar,
                    initial = name.trim().firstOrNull()?.uppercase() ?: "•",
                    contentDescription = null,
                    onClick = null,
                    size = 48.dp,
                )

                Text(
                    text = name.ifBlank {
                        stringResource(R.string.immersive_screens_account)
                    },
                    style = Immersive.Headline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp),
                )

                if (SupportReminder.level != SupportReminder.Level.NONE) {
                    SupporterBadge(
                        Modifier.padding(start = 10.dp)
                    )
                }
            }

            if (myId != null) {
                Text(
                    text = stringResource(R.string.view_profile),
                    style = Immersive.Caption,
                    modifier = Modifier.padding(
                        start = 82.dp,
                        bottom = 4.dp
                    ),
                )
            }

            HorizontalDivider(
                color = Immersive.Separator,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Settings
            SheetRow(
                stringResource(R.string.settings_title),
                stringResource(R.string.settings_detail)
            ) {
                onOpen(Routes.Settings.route)
            }

            // About
            var about by remember { mutableStateOf(false) }

            if (about) {
                AboutSheet(
                    onDismiss = { about = false }
                )
            }

            SheetRow(
                stringResource(R.string.creator_biography_section_title),
                stringResource(R.string.about_detail)
            ) {
                about = true
            }

            // Support HUMBLE MUSIC
            SheetRow(
                title = "Support HUMBLE MUSIC",
                detail = "Support independent development"
            ) {
                val paymentUri = Uri.Builder()
                    .scheme("upi")
                    .authority("pay")
                    .appendQueryParameter(
                        "pa",
                        "maheshmadhav620@okhdfcbank"
                    )
                    .appendQueryParameter(
                        "pn",
                        "HUMBLE MUSIC"
                    )
                    .appendQueryParameter(
                        "tn",
                        "Support HUMBLE MUSIC"
                    )
                    .appendQueryParameter(
                        "cu",
                        "INR"
                    )
                    .build()

                val paymentIntent = Intent(
                    Intent.ACTION_VIEW,
                    paymentUri
                )

                try {
                    context.startActivity(
                        Intent.createChooser(
                            paymentIntent,
                            "Support HUMBLE MUSIC"
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        "No UPI payment app found.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Extensions
            val extRegistry by
            com.music.spotui.extensions.Extensions.registry.collectAsState()

            val installed = extRegistry.extensions

            SheetRow(
                stringResource(R.string.extensions_title),
                if (installed.isEmpty()) {
                    stringResource(R.string.extensions_row_detail)
                } else {
                    stringResource(
                        R.string.extensions_row_count,
                        installed.size,
                        installed.count { it.enabled }
                    )
                }
            ) {
                onOpen(Routes.Extensions.route)
            }

            // Supporter Perks
            var perks by remember { mutableStateOf(false) }

            if (perks) {
                SupporterSheet(
                    onDismiss = { perks = false }
                )
            }

            if (SupportReminder.PERKS_ENABLED) {
                SheetRow(
                    stringResource(R.string.supporter_perks_title),
                    stringResource(R.string.supporter_perks_detail)
                ) {
                    perks = true
                }
            }

            // Logout
            SheetRow(
                stringResource(R.string.connect_device_menu_logout),
                null
            ) {
                onDismiss()
                SessionActions.logout(Graph.app)
            }
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    detail: String?,
    titleColor: androidx.compose.ui.graphics.Color = Immersive.Text,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(
                horizontal = Immersive.Gutter,
                vertical = if (detail == null) 14.dp else 10.dp
            ),
    ) {
        Text(
            title,
            style = Immersive.Body.copy(color = titleColor)
        )

        if (detail != null) {
            Text(
                detail,
                style = Immersive.Caption
            )
        }
    }
}

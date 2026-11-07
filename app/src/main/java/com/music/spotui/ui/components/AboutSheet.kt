
package com.music.spotui.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.BuildConfig
import app.myspoti.engine.R
import com.music.spotui.data.api.SupportReminder
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone

private const val PORTFOLIO_URL = "https://maheshmadhav.vercel.app/"
private const val INSTAGRAM_URL = "https://www.instagram.com/_humble.y_/"
private const val GITHUB_URL = "https://github.com/humblelyy"
private const val LINKEDIN_URL = "https://linkedin.com/in/mahesh-madhav-602683357"

private val CardSurface = tone(0xFF202124)
private val CardBorder = Color.White.copy(alpha = 0.09f)
private val GlassSurface = tone(0xFF171719)

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * HUMBLE MUSIC — Creator identity and project information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current

    com.music.spotui.extensions.ProtectedSurfaceEffect(
        app.myspoti.extensions.ProtectedSurface.ABOUT
    )

    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        containerColor = GlassSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HUMBLE STUDIO",
                    color = AppMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    "ABOUT · ${BuildConfig.VERSION_NAME}",
                    color = AppMuted,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // Creator identity card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(26.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.humble_main_logo),
                        contentDescription = "HUMBLE Studio logo",
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(tone(0xFF080808))
                            .padding(9.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "HUMBLE MUSIC",
                            color = AppText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "An independent creation by",
                            color = AppMuted,
                            fontSize = 11.sp
                        )

                        Text(
                            "Mahesh Madhav",
                            color = AppText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardBorder)
                )

                Text(
                    "Music. Design. Identity.",
                    color = AppText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "A personal music experience, built with attention to detail and a passion for design.",
                    color = AppMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniTag("OPEN SOURCE")
                    MiniTag("HUMBLE STUDIO")
                }
            }

            // Social cards
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("CONNECT WITH THE CREATOR")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialCard(
                        modifier = Modifier.weight(1f),
                        symbol = "↗",
                        title = "Portfolio",
                        detail = "Personal website",
                        onClick = { openUrl(context, PORTFOLIO_URL) }
                    )

                    SocialCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_brand_instagram,
                        title = "Instagram",
                        detail = "@_humble.y_",
                        onClick = { openUrl(context, INSTAGRAM_URL) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SocialCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_brand_github,
                        title = "GitHub",
                        detail = "humblelyy",
                        onClick = { openUrl(context, GITHUB_URL) }
                    )

                    SocialCard(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_brand_linkedin,
                        title = "LinkedIn",
                        detail = "Mahesh Madhav",
                        onClick = { openUrl(context, LINKEDIN_URL) }
                    )
                }
            }

            // Support panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "SUPPORT INDEPENDENT DEVELOPMENT",
                    color = AppMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )

                Text(
                    "Keep HUMBLE growing.",
                    color = AppText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "Enjoying HUMBLE MUSIC? Your support helps us maintain and improve the experience.",
                    color = AppMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tone(0xFF303030))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            SupportReminder.open(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Support HUMBLE  ↗",
                        color = AppText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                "DESIGNED WITH PURPOSE · HUMBLE STUDIO",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                color = AppMuted.copy(alpha = 0.65f),
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        title,
        color = AppMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

@Composable
private fun MiniTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone(0xFF303033))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            color = AppMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Social card with support for platform logo assets
 * or a text symbol for the Portfolio card.
 */
@Composable
private fun SocialCard(
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    symbol: String? = null,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = "$title logo",
                modifier = Modifier.size(25.dp)
            )
        } else {
            Text(
                text = symbol ?: "↗",
                color = AppText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            title,
            color = AppText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            detail,
            color = AppMuted,
            fontSize = 10.sp
        )
    }
}

/**
 * Legacy support button retained for existing call sites.
 */
@Composable
fun GitHubSponsorButton(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, AppText.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable {
                onClick?.invoke() ?: SupportReminder.openSponsors(context)
            }
    ) {
        Text(
            stringResource(R.string.support_github_action),
            color = AppText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Support sheet kept for existing application call sites.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current

    com.music.spotui.extensions.ProtectedSurfaceEffect(
        app.myspoti.extensions.ProtectedSurface.SUPPORT
    )

    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        containerColor = GlassSurface,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                "Support HUMBLE",
                color = AppText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                stringResource(R.string.support_body),
                color = AppMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tone(0xFF303030))
                    .clickable {
                        onDismiss()
                        SupportReminder.open(context)
                    }
            ) {
                Text(
                    "Support HUMBLE",
                    color = AppText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

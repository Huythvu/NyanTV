package com.nyantv.ui.settings.sub_settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nyantv.data.ServiceType
import com.nyantv.ui.utils.SectionCard
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel

@Composable
fun AccountsScreen(vm: AppViewModel, navController: NavController) {
    val loggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    val profile  by vm.profile.collectAsStateWithLifecycle()
    val service  by vm.serviceType.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SubScreenHeader(title = "Accounts & Services", navController = navController)

        // AniList logs in via phone/PC pairing (TV-friendly, no Cloudflare); the others use the
        // in-app WebView. [fresh] forces a signed-out login so a different account can be used.
        fun beginLogin(fresh: Boolean) {
            when {
                service == ServiceType.ANILIST -> navController.navigate("pair/anilist")
                fresh                          -> vm.loginWithDifferentAccount()
                else                           -> vm.login()
            }
        }

        // ── Account ────────────────────────────────────────────────────────────
        SectionCard(title = "Account") {
            if (loggedIn && profile != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.padding(12.dp)
                ) {
                    AsyncImage(
                        model              = profile!!.avatar,
                        contentDescription = null,
                        modifier           = Modifier.size(56.dp).clip(CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile!!.name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(service.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        profile?.animeCount?.let {
                            Text("$it anime · ${profile?.episodesWatched ?: "?"} eps watched",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick  = { beginLogin(fresh = true) },
                            modifier = Modifier.focusBorder(MaterialTheme.shapes.small)
                        ) { Text("Switch account") }
                        OutlinedButton(
                            onClick  = { vm.logout() },
                            modifier = Modifier.focusBorder(MaterialTheme.shapes.small)
                        ) { Text("Logout") }
                    }
                }
            } else {
                Row(
                    modifier              = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("Not logged in", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Button(
                            onClick  = { beginLogin(fresh = false) },
                            modifier = Modifier.focusBorder(CircleShape)
                        ) {
                            Text("Login")
                        }
                    }
                }
            }
        }

        // ── Tracking Service ───────────────────────────────────────────────────
        SectionCard(title = "Tracking Service") {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ServiceType.entries.forEach { svc ->
                    val selected = service == svc
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { vm.switchService(svc) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { vm.switchService(svc) } )
                        Text(svc.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        // Tracking automation + extra options moved to Settings → Tracking.

        // ── Sync Tracking ──────────────────────────────────────────────────────────────
        val syncMal by vm.syncMalWithAnilist.collectAsStateWithLifecycle()

        SectionCard(title = "Sync Tracking") {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync MyAnimeList with AniList", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Automatically sync progress across both services", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Switch(
                        checked = syncMal,
                        onCheckedChange = { vm.setSyncMalWithAnilist(it) },
                        modifier = Modifier.focusBorder(RoundedCornerShape(50))
                    )
                }
            }
        }

        // ── MyAnimeList Titles ──────────────────────────────────────────────────────────
        val malEnglishTitles by vm.malEnglishTitles.collectAsStateWithLifecycle()

        SectionCard(title = "MyAnimeList Titles") {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("English titles", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Show English titles instead of Romaji where available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Switch(
                        checked = malEnglishTitles,
                        onCheckedChange = { vm.setMalEnglishTitles(it) },
                        modifier = Modifier.focusBorder(RoundedCornerShape(50))
                    )
                }
            }
        }

        // ── Manage Homescreen Lists ────────────────────────────────────────────────────
        val anilistContinue  by vm.anilistShowContinue.collectAsStateWithLifecycle()
        val anilistPlanned   by vm.anilistShowPlanned.collectAsStateWithLifecycle()
        val anilistTrending  by vm.anilistShowTrending.collectAsStateWithLifecycle()
        val anilistPopular   by vm.anilistShowPopular.collectAsStateWithLifecycle()
        val malContinue      by vm.malShowContinue.collectAsStateWithLifecycle()
        val malPlanned       by vm.malShowPlanned.collectAsStateWithLifecycle()
        val malTrending      by vm.malShowTrending.collectAsStateWithLifecycle()
        val malPopular       by vm.malShowPopular.collectAsStateWithLifecycle()
        val malSeasonal      by vm.malShowSeasonal.collectAsStateWithLifecycle()
        val malUpcoming      by vm.malShowUpcoming.collectAsStateWithLifecycle()
        val malOrder         by vm.malHomeOrder.collectAsStateWithLifecycle()
        val anilistUpcoming  by vm.anilistShowUpcoming.collectAsStateWithLifecycle()
        val anilistOrder     by vm.anilistHomeOrder.collectAsStateWithLifecycle()
        val anilistLocalCont by vm.anilistShowLocalContinue.collectAsStateWithLifecycle()
        val malLocalCont     by vm.malShowLocalContinue.collectAsStateWithLifecycle()
        val simklContMovies  by vm.simklShowContMovies.collectAsStateWithLifecycle()
        val simklPlanMovies  by vm.simklShowPlanMovies.collectAsStateWithLifecycle()
        val simklContSeries  by vm.simklShowContSeries.collectAsStateWithLifecycle()
        val simklPlanSeries  by vm.simklShowPlanSeries.collectAsStateWithLifecycle()

        SectionCard(
            title = "Manage AniList Homescreen",
            dialogContent = {
                anilistOrder.forEachIndexed { index, key ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    val label = when (key) {
                        "local_continue" -> "Continue Watching"
                        "continue"       -> "Watching Anime"
                        "planned"        -> "Planned Anime"
                        "trending"       -> "Trending Now"
                        "popular"        -> "Popular Anime"
                        "upcoming"       -> "Upcoming"
                        else             -> key
                    }
                    val checked = when (key) {
                        "local_continue" -> anilistLocalCont
                        "continue"       -> anilistContinue
                        "planned"        -> anilistPlanned
                        "trending"       -> anilistTrending
                        "popular"        -> anilistPopular
                        "upcoming"       -> anilistUpcoming
                        else             -> true
                    }
                    HomescreenManageRow(
                        label    = label,
                        checked  = checked,
                        onToggle = { v ->
                            when (key) {
                                "local_continue" -> vm.setAnilistShowLocalContinue(v)
                                "continue"       -> vm.setAnilistShowContinue(v)
                                "planned"        -> vm.setAnilistShowPlanned(v)
                                "trending"       -> vm.setAnilistShowTrending(v)
                                "popular"        -> vm.setAnilistShowPopular(v)
                                "upcoming"       -> vm.setAnilistShowUpcoming(v)
                            }
                        },
                        onUp     = { vm.moveAnilistSection(key, up = true) },
                        onDown   = { vm.moveAnilistSection(key, up = false) },
                        canUp    = index > 0,
                        canDown  = index < anilistOrder.size - 1,
                    )
                }
            }
        ) {}

        SectionCard(
            title = "Manage MyAnimeList Homescreen",
            dialogContent = {
                // Rows are shown in the order they appear on the home screen; the arrows reorder them.
                malOrder.forEachIndexed { index, key ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    val label = when (key) {
                        "local_continue" -> "Continue Watching"
                        "continue" -> "Watching Anime"
                        "planned"  -> "Planned Anime"
                        "trending" -> "Trending Now"
                        "popular"  -> "Popular Anime"
                        "seasonal" -> "Seasonal Anime"
                        "upcoming" -> "Upcoming"
                        else       -> key
                    }
                    val checked = when (key) {
                        "local_continue" -> malLocalCont
                        "continue" -> malContinue
                        "planned"  -> malPlanned
                        "trending" -> malTrending
                        "popular"  -> malPopular
                        "seasonal" -> malSeasonal
                        "upcoming" -> malUpcoming
                        else       -> true
                    }
                    HomescreenManageRow(
                        label    = label,
                        checked  = checked,
                        onToggle = { v ->
                            when (key) {
                                "local_continue" -> vm.setMalShowLocalContinue(v)
                                "continue" -> vm.setMalShowContinue(v)
                                "planned"  -> vm.setMalShowPlanned(v)
                                "trending" -> vm.setMalShowTrending(v)
                                "popular"  -> vm.setMalShowPopular(v)
                                "seasonal" -> vm.setMalShowSeasonal(v)
                                "upcoming" -> vm.setMalShowUpcoming(v)
                            }
                        },
                        onUp     = { vm.moveMalSection(key, up = true) },
                        onDown   = { vm.moveMalSection(key, up = false) },
                        canUp    = index > 0,
                        canDown  = index < malOrder.size - 1,
                    )
                }
            }
        ) {}

        SectionCard(
            title = "Manage Simkl Homescreen",
            dialogContent = {
                HomescreenToggleRow("Continue Watching (Movies)", simklContMovies) { vm.setSimklShowContMovies(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                HomescreenToggleRow("Planned (Movies)", simklPlanMovies) { vm.setSimklShowPlanMovies(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                HomescreenToggleRow("Continue Watching (Series)", simklContSeries) { vm.setSimklShowContSeries(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                HomescreenToggleRow("Planned (Series)", simklPlanSeries) { vm.setSimklShowPlanSeries(it) }
            }
        ) {}

        // ── Card Status Badges ──────────────────────────────────────────────────────────
        val showCardStatus   by vm.showCardStatus.collectAsStateWithLifecycle()
        val cardStatusStates by vm.cardStatusStates.collectAsStateWithLifecycle()

        SectionCard(
            title = "Card Status Badges",
            dialogContent = {
                HomescreenToggleRow("Show status badges", showCardStatus) { vm.setShowCardStatus(it) }
                listOf(
                    "airing"    to "Currently Airing",
                    "finished"  to "Finished Airing",
                    "not_yet"   to "Not Yet Aired",
                    "cancelled" to "Cancelled",
                    "hiatus"    to "Hiatus",
                ).forEach { (key, label) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    HomescreenToggleRow(label, key in cardStatusStates) { vm.setCardStatusState(key, it) }
                }
            }
        ) {}
    }
}

@Composable
private fun HomescreenManageRow(
    label:    String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit,
    onUp:     () -> Unit,
    onDown:   () -> Unit,
    canUp:    Boolean,
    canDown:  Boolean,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        ReorderArrow(Icons.Filled.KeyboardArrowUp,   "Move up",   onUp,   canUp)
        ReorderArrow(Icons.Filled.KeyboardArrowDown, "Move down", onDown, canDown)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked         = checked,
                onCheckedChange = onToggle,
                modifier        = Modifier.focusBorder(RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun ReorderArrow(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .focusBorder(CircleShape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier           = Modifier.size(20.dp),
            tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.25f)
        )
    }
}

@Composable
private fun HomescreenToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked         = checked,
                onCheckedChange = onToggle,
                modifier        = Modifier.focusBorder(RoundedCornerShape(50))
            )
        }
    }
}
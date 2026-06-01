package com.nyantv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nyantv.BuildConfig
import com.nyantv.data.Media
import com.nyantv.data.ServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun HomeHeroCarousel(
    items: List<Media>,
    onItemClick: (Media) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val logos = remember { mutableStateMapOf<String, String?>() }

    LaunchedEffect(items) {
        items.forEach { media ->
            if (!logos.containsKey(media.id)) {
                logos[media.id] = CarouselLogoResolver.resolve(media)
            }
        }
    }

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4500L)
            val next = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(next)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val media = items[page]
            val logoUrl = logos[media.id]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(media) }
            ) {
                AsyncImage(
                    model = media.cover ?: media.poster,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = media.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(48.dp)
                        )
                    } else {
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CarouselStats(media = media)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(items.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(if (selected) 20.dp else 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.45f)
                        )
                )
            }
        }
    }
}

@Composable
private fun CarouselStats(media: Media) {
    val items = buildList {
        media.episodes?.takeIf { it > 0 }?.let { add("$it EP") }
        media.status?.let { add(normalizeLabel(it)) }
        media.format?.let { add(normalizeLabel(it)) }
        seasonLabel(media)?.let { add(it) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items.take(4).forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        media.averageScore?.takeIf { it > 0 }?.let { score ->
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "%.1f".format(score / 10f),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

private fun normalizeLabel(value: String): String =
    value.replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }

private fun seasonLabel(media: Media): String? {
    val season = media.season?.let(::normalizeLabel)
    val year = media.seasonYear
    return when {
        season != null && year != null -> "$season $year"
        season != null -> season
        year != null -> year.toString()
        else -> null
    }
}

private object CarouselLogoResolver {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cache = mutableMapOf<String, String?>()
    private val tmdbRegex = Regex("https://image\\.tmdb\\.org/t/p/original[^\"'\\\\ ]+")

    suspend fun resolve(media: Media): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${media.serviceType}:${media.id}:${media.idMal}:${media.tmdbId}"
        if (cache.containsKey(cacheKey)) return@withContext cache[cacheKey]

        val logo = when (media.serviceType) {
            ServiceType.ANILIST -> fromAniZip("anilist_id", media.id)
                ?: fromAniZip("anilistId", media.id)
            ServiceType.MAL -> {
                val malId = media.idMal ?: media.id
                fromAniZip("mal_id", malId) ?: fromAniZip("malId", malId)
            }
            ServiceType.SIMKL -> fromTmdb(media.tmdbId, media.format == "MOVIE")
        }

        cache[cacheKey] = logo
        logo
    }

    private fun fromAniZip(key: String, id: String): String? {
        if (id.isBlank()) return null
        val endpoints = listOf(
            "https://api.ani.zip/mappings?$key=$id",
            "https://api.ani.zip/anime/$id"
        )
        endpoints.forEach { url ->
            getJson(url)?.let { jsonElement ->
                extractLogoUrl(jsonElement)?.let { return "https://wsrv.nl/?url=$it" }
            }
        }
        return null
    }

    private fun fromTmdb(tmdbId: String?, isMovie: Boolean): String? {
        if (tmdbId.isNullOrBlank()) return null
        val type = if (isMovie) "movie" else "tv"
        BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() }?.let { apiKey ->
            val apiLogo = getJson("https://api.themoviedb.org/3/$type/$tmdbId/images?api_key=$apiKey&include_image_language=en,null")
                ?.let { extractTmdbLogoPath(it) }
                ?.let { "https://wsrv.nl/?url=https://image.tmdb.org/t/p/original$it" }
            if (!apiLogo.isNullOrBlank()) return apiLogo
        }
        return getString("https://www.themoviedb.org/$type/$tmdbId/images/logos")
            ?.let { html -> tmdbRegex.find(html)?.value }
            ?.let { "https://wsrv.nl/?url=$it" }
    }

    private fun extractTmdbLogoPath(element: JsonElement): String? {
        val logos = when (element) {
            is JsonObject -> element["logos"]
            else -> null
        }
        return (logos as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("file_path")
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
    }

    private fun extractLogoUrl(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            val direct = listOf("clearlogo", "clearLogo", "logo", "logoImage", "image")
                .firstNotNullOfOrNull { key ->
                    element[key]?.let { candidate ->
                        (candidate as? JsonPrimitive)?.contentOrNull?.takeIf { isImageUrl(it) }
                    }
                }
            direct ?: element.values.firstNotNullOfOrNull { extractLogoUrl(it) }
        }
        is JsonArray -> element.firstNotNullOfOrNull { extractLogoUrl(it) }
        is JsonPrimitive -> element.contentOrNull?.takeIf { isImageUrl(it) }
        else -> null
    }

    private fun isImageUrl(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")

    private fun getJson(url: String): JsonElement? =
        runCatching {
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body.string()
                json.parseToJsonElement(body)
            }
        }.getOrNull()

    private fun getString(url: String): String? =
        runCatching {
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
        }.getOrNull()
}

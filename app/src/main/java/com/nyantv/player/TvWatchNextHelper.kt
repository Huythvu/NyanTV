package com.nyantv.player

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvWatchNextHelper(private val context: Context) {

    suspend fun updateWatchNext(
        serviceKey:    String,
        mediaId:       String,
        seriesTitle:   String,
        episodeTitle:  String,
        episodeNumber: Int,
        coverUrl:      String?,
        bannerUrl:     String?,
        posterUrl:     String?,
        positionMs:    Long,
        durationMs:    Long,
        episodeDescription:  String? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val imageUrl = listOfNotNull(coverUrl, bannerUrl, posterUrl)
                .firstOrNull { it.isNotBlank() } ?: return@withContext

            removeAllExcept(mediaId)

            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(seriesTitle)
                .setEpisodeTitle(episodeTitle)
                .setEpisodeNumber(episodeNumber)
                .setPosterArtUri(Uri.parse(imageUrl))
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setLastPlaybackPositionMillis(positionMs.toInt())
                .setDurationMillis(durationMs.toInt())
                .setIntentUri(Uri.parse("nyantv://watch/$serviceKey/$mediaId/$episodeNumber"))
                .setInternalProviderId(mediaId)
                .apply { episodeDescription?.let { setDescription(it) } }
                .build()
                .toContentValues()

            val existingId = findExisting(mediaId)
            if (existingId != null) {
                context.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingId),
                    program, null, null,
                )
            } else {
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("TvWatchNext", "updateWatchNext failed", e)
        }
    }

    suspend fun remove(mediaId: String) = withContext(Dispatchers.IO) {
        try {
            val id = findExisting(mediaId) ?: return@withContext
            context.contentResolver.delete(
                TvContractCompat.buildWatchNextProgramUri(id),
                null, null,
            )
        } catch (e: Exception) {
            android.util.Log.e("TvWatchNext", "remove failed", e)
        }
    }

    private val projection = arrayOf(
        TvContractCompat.WatchNextPrograms._ID,
        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
    )

    private fun findExisting(mediaId: String): Long? {
        context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val idCol  = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
            val prvCol = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            while (cursor.moveToNext()) {
                if (cursor.getString(prvCol) == mediaId) return cursor.getLong(idCol)
            }
        }
        return null
    }

    private fun removeAllExcept(mediaId: String) {
        context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val idCol  = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
            val prvCol = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            while (cursor.moveToNext()) {
                if (cursor.getString(prvCol) != mediaId) {
                    context.contentResolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(cursor.getLong(idCol)),
                        null, null,
                    )
                }
            }
        }
    }
}
package com.nyantv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyantv.data.AiringScheduleEntry
import com.nyantv.data.AnilistService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Weekly anime airing schedule (all anime), from AniList's public airing API. */
class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val anilist = AnilistService(app)

    /** One day's worth of airings. [label] is "Today"/"Tomorrow"/weekday; [date] e.g. "Mar 12". */
    data class Day(val label: String, val date: String, val entries: List<AiringScheduleEntry>)

    data class ScheduleState(
        val loading: Boolean = true,
        val error:   Boolean = false,
        val days:    List<Day> = emptyList(),
    )

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ScheduleState(loading = true)
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000L
            val end = now + 7L * 24 * 3600
            val entries = runCatching { anilist.fetchAiringSchedule(now, end) }.getOrNull()
            if (entries == null) {
                _state.value = ScheduleState(loading = false, error = true)
                return@launch
            }
            val weekday = SimpleDateFormat("EEEE", Locale.getDefault())
            val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val days = (0 until 7).map { offset ->
                val start = startOfDaySec(offset)
                val dayEnd = start + 24 * 3600
                val dayEntries = entries
                    .filter { it.airingAtSec in start until dayEnd }
                    .sortedBy { it.airingAtSec }
                val label = when (offset) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> weekday.format(Date(start * 1000L))
                }
                Day(label = label, date = dateFmt.format(Date(start * 1000L)), entries = dayEntries)
            }
            _state.value = ScheduleState(loading = false, days = days)
        }
    }

    /** Local midnight, [offset] days from today, as epoch seconds. */
    private fun startOfDaySec(offset: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }
}

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
        val loading:    Boolean = true,
        val error:      Boolean = false,
        val days:       List<Day> = emptyList(),
        val todayIndex: Int = 0,   // which day in [days] is "today"
    )

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ScheduleState(loading = true)
        viewModelScope.launch {
            // A few days back (to see what already aired) through the coming week.
            val start = startOfDaySec(-PREV_DAYS)
            val end   = startOfDaySec(NEXT_DAYS + 1)   // exclusive: midnight after the last day
            val entries = runCatching { anilist.fetchAiringSchedule(start, end) }.getOrNull()
            if (entries == null) {
                _state.value = ScheduleState(loading = false, error = true)
                return@launch
            }
            val weekday = SimpleDateFormat("EEEE", Locale.getDefault())
            val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val days = (-PREV_DAYS..NEXT_DAYS).map { offset ->
                val dayStart = startOfDaySec(offset)
                val dayEnd   = dayStart + 24 * 3600
                val dayEntries = entries
                    .filter { it.airingAtSec in dayStart until dayEnd }
                    .sortedBy { it.airingAtSec }
                val label = when (offset) {
                    -1   -> "Yesterday"
                    0    -> "Today"
                    1    -> "Tomorrow"
                    else -> weekday.format(Date(dayStart * 1000L))
                }
                Day(label = label, date = dateFmt.format(Date(dayStart * 1000L)), entries = dayEntries)
            }
            _state.value = ScheduleState(loading = false, days = days, todayIndex = PREV_DAYS)
        }
    }

    private companion object {
        const val PREV_DAYS = 3   // days of history
        const val NEXT_DAYS = 6   // days ahead (so 10 days total, today at index PREV_DAYS)
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

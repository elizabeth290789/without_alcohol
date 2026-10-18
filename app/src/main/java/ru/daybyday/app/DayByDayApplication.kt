package ru.daybyday.app

import android.app.Application
import ru.daybyday.app.data.TrackerRepository

class DayByDayApplication : Application() {
    val repository: TrackerRepository by lazy { TrackerRepository(this) }
}

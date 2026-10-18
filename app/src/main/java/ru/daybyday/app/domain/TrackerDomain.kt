package ru.daybyday.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A closed interval contains [start, endExclusive); null means the current period. */
data class Period(
    val id: Long,
    val start: LocalDate,
    val endExclusive: LocalDate? = null,
)

/** One collectible per threshold, with its original period retained for corrections. */
data class Award(val threshold: Long, val date: LocalDate, val periodId: Long)

data class Achievement(
    val threshold: Long,
    val title: String,
    val description: String,
    val symbol: String,
)

data class Level(
    val number: Int,
    val title: String,
    val minDays: Long,
    val maxDays: Long?,
)

data class Progress(
    val currentPeriod: Period?,
    val completedDays: Long,
    val currentDay: Long,
    val bestDays: Long,
    val totalDays: Long,
    val completedPeriods: Int,
    val level: Level,
    val nextAchievement: Achievement?,
    val daysToNext: Long?,
    val achievementProgress: Float,
    val levelProgress: Float,
    val unlockedCount: Int,
)

enum class DayStatus { UNKNOWN, CONFIRMED, CURRENT, FUTURE }

data class CalendarDay(
    val date: LocalDate,
    val status: DayStatus,
    val awards: List<Award>,
)

/** Date-only rules: no elapsed milliseconds, timers, time zones, Android, or persistence. */
object TrackerDomain {
    val achievements: List<Achievement> = listOf(
        Achievement(1, "Первый шаг", "Один полный день. Начало уже положено.", "seed"),
        Achievement(3, "Отличное начало", "Три полных дня заботы о себе.", "spark"),
        Achievement(7, "Первая неделя", "Семь полных дней, шаг за шагом.", "leaf"),
        Achievement(14, "Две недели", "Четырнадцать полных дней на своём пути.", "compass"),
        Achievement(21, "Три недели", "Двадцать один полный день маленьких решений.", "flower"),
        Achievement(30, "Первый месяц", "Тридцать полных дней личного прогресса.", "moon"),
        Achievement(45, "Уверенный курс", "Сорок пять полных дней выбранного курса.", "sail"),
        Achievement(60, "Два месяца", "Шестьдесят полных дней. Твой путь продолжается.", "sun"),
        Achievement(90, "Большая дистанция", "Девяносто полных дней — большая дистанция.", "mountain"),
        Achievement(100, "Сотня", "Сто полных дней. Каждый из них имеет значение.", "gem"),
        Achievement(180, "Полгода", "Сто восемьдесят полных дней постоянства.", "tree"),
        Achievement(270, "Девять месяцев", "Двести семьдесят полных дней своего пути.", "planet"),
        Achievement(365, "Год", "Триста шестьдесят пять полных дней.", "star"),
        Achievement(500, "Пятьсот дней", "Пятьсот полных дней, собранных по одному.", "crown"),
        Achievement(730, "Два года", "Семьсот тридцать полных дней постоянства.", "galaxy"),
        Achievement(1000, "Тысяча дней", "Тысяча полных дней. Твоя особенная история.", "infinity"),
    )

    val levels: List<Level> = listOf(
        Level(1, "Начало пути", 0, 2),
        Level(2, "Первые успехи", 3, 6),
        Level(3, "Набираешь темп", 7, 13),
        Level(4, "Уверенный шаг", 14, 29),
        Level(5, "Месяц за месяцем", 30, 59),
        Level(6, "Держишь курс", 60, 89),
        Level(7, "Новые горизонты", 90, 179),
        Level(8, "Большой путь", 180, 364),
        Level(9, "Годовой рубеж", 365, 729),
        Level(10, "Мастер постоянства", 730, null),
    )

    val messages: List<String> = listOf(
        "Каждый день — отдельное достижение",
        "Прогресс складывается из маленьких шагов",
        "Сегодня ещё одна возможность позаботиться о себе",
        "Твой путь не определяется одним днём",
        "Любой прогресс имеет значение",
        "Можно двигаться в своём темпе",
        "Один день за другим — и путь продолжается",
        "Сегодня есть место для доброты к себе",
    )

    fun completedDays(period: Period, today: LocalDate): Long =
        ChronoUnit.DAYS.between(period.start, period.endExclusive ?: today).coerceAtLeast(0)

    fun snapshot(periods: List<Period>, awards: List<Award>, today: LocalDate): Progress {
        validatePeriods(periods)
        val current = periods.singleOrNull { it.endExclusive == null }
        val days = current?.let { completedDays(it, today) } ?: 0
        val lengths = periods.map { completedDays(it, today) }
        val earned = awards.map { it.threshold }.toSet()
        val next = achievements.firstOrNull { it.threshold !in earned && it.threshold > days }
        val level = levels.last { days >= it.minDays }
        val nextLevel = levels.getOrNull(level.number)
        return Progress(
            currentPeriod = current,
            completedDays = days,
            currentDay = if (current != null && !today.isBefore(current.start)) days + 1 else 0,
            bestDays = lengths.maxOrNull() ?: 0,
            totalDays = lengths.sum(),
            completedPeriods = periods.count { it.endExclusive != null },
            level = level,
            nextAchievement = next,
            daysToNext = next?.let { (it.threshold - days).coerceAtLeast(0) },
            achievementProgress = next?.let {
                (days.toFloat() / it.threshold.toFloat()).coerceIn(0f, 1f)
            } ?: 1f,
            levelProgress = nextLevel?.let {
                ((days - level.minDays).toFloat() / (it.minDays - level.minDays).toFloat())
                    .coerceIn(0f, 1f)
            } ?: 1f,
            unlockedCount = achievements.count { it.threshold in earned },
        )
    }

    /**
     * Backfills milestones skipped while the app was closed, without duplicates.
     * Rollover and clock changes preserve saved awards. An explicit date correction
     * removes only awards sourced to the corrected period, then checks all history.
     */
    fun reconcileAwards(
        periods: List<Period>,
        awards: List<Award>,
        today: LocalDate,
        correctedPeriodIds: Set<Long> = emptySet(),
    ): List<Award> {
        validatePeriods(periods)
        val existingIds = periods.map { it.id }.toSet()
        val validThresholds = achievements.map { it.threshold }.toSet()
        val result = awards.asSequence()
            .filter { it.periodId in existingIds && it.periodId !in correctedPeriodIds }
            .filter { it.threshold in validThresholds }
            .sortedWith(compareBy<Award> { it.date }.thenBy { it.periodId })
            .distinctBy { it.threshold }
            .associateByTo(linkedMapOf()) { it.threshold }
        for (achievement in achievements) {
            if (achievement.threshold in result) continue
            val candidate = periods.asSequence()
                .filter { completedDays(it, today) >= achievement.threshold }
                .map { Award(achievement.threshold, it.start.plusDays(achievement.threshold), it.id) }
                .minWithOrNull(compareBy<Award> { it.date }.thenBy { it.periodId })
            if (candidate != null) result[achievement.threshold] = candidate
        }
        return result.values.sortedBy { it.threshold }
    }

    fun startFirst(start: LocalDate, today: LocalDate, id: Long = 1): List<Period> {
        require(!start.isAfter(today)) { "Дата начала не может быть в будущем" }
        return listOf(Period(id, start))
    }

    fun editStart(periods: List<Period>, newStart: LocalDate, today: LocalDate): List<Period> {
        validatePeriods(periods)
        require(!newStart.isAfter(today)) { "Дата начала не может быть в будущем" }
        val current = requireNotNull(periods.singleOrNull { it.endExclusive == null }) {
            "Сначала выберите дату начала"
        }
        return periods.map { if (it.id == current.id) it.copy(start = newStart) else it }
            .also(::validatePeriods)
    }

    /** The chosen last day is included; the next period starts on a later date. */
    fun startNewPeriod(
        periods: List<Period>,
        endInclusive: LocalDate,
        newStart: LocalDate,
        today: LocalDate,
        newId: Long,
    ): List<Period> {
        validatePeriods(periods)
        val current = requireNotNull(periods.singleOrNull { it.endExclusive == null }) {
            "Сначала выберите дату начала"
        }
        require(periods.none { it.id == newId }) { "Идентификатор периода уже существует" }
        require(!endInclusive.isBefore(current.start)) { "Дата завершения раньше начала периода" }
        require(newStart.isAfter(endInclusive)) { "Новый период должен начаться после предыдущего" }
        require(!newStart.isAfter(today)) { "Дата начала не может быть в будущем" }
        val result = periods.map {
            if (it.id == current.id) it.copy(endExclusive = endInclusive.plusDays(1)) else it
        } + Period(newId, newStart)
        validatePeriods(result)
        return result
    }

    /** Validate before saving so the statistics never double-count a calendar date. */
    fun validatePeriods(periods: List<Period>) {
        require(periods.map { it.id }.toSet().size == periods.size) {
            "Идентификаторы периодов должны быть уникальны"
        }
        require(periods.count { it.endExclusive == null } <= 1) { "Текущий период может быть только один" }
        val ordered = periods.sortedBy { it.start }
        ordered.forEach { period ->
            require(period.endExclusive == null || period.endExclusive.isAfter(period.start)) {
                "Дата завершения должна быть не раньше даты начала"
            }
        }
        ordered.zipWithNext().forEach { (previous, next) ->
            require(previous.endExclusive != null && !next.start.isBefore(previous.endExclusive)) {
                "Периоды не должны пересекаться"
            }
        }
    }

    fun calendarDay(
        date: LocalDate,
        periods: List<Period>,
        awards: List<Award>,
        today: LocalDate,
    ): CalendarDay {
        val matching = periods.firstOrNull {
            !date.isBefore(it.start) && (it.endExclusive == null || date.isBefore(it.endExclusive))
        }
        val status = when {
            date.isAfter(today) -> DayStatus.FUTURE
            matching == null -> DayStatus.UNKNOWN
            date == today && matching.endExclusive == null -> DayStatus.CURRENT
            else -> DayStatus.CONFIRMED
        }
        return CalendarDay(date, status, awards.filter { it.date == date })
    }

    fun dayWord(count: Long): String {
        val lastHundred = abs(count % 100)
        val lastDigit = abs(count % 10)
        return when {
            lastHundred in 11..14 -> "дней"
            lastDigit == 1L -> "день"
            lastDigit in 2..4 -> "дня"
            else -> "дней"
        }
    }
}

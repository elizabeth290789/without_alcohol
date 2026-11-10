package ru.daybyday.app.domain

import java.time.LocalDate

data class ConsumptionEvent(
    val id: Long,
    val date: LocalDate,
    val previousPeriodId: Long,
    val nextPeriodId: Long,
    val previousCoverageStart: LocalDate? = null,
)

data class ConsumptionDateBounds(val minDate: LocalDate, val maxDate: LocalDate)

data class ConsumptionChange(
    val periods: List<Period>,
    val events: List<ConsumptionEvent>,
    val event: ConsumptionEvent,
)

/**
 * A consumption date is a gap of exactly one date between its linked periods.
 * Empty closed periods retain identity and links, but never count as sober days.
 * Awards are intentionally outside this API: their saved receipt ledger is retained.
 */
object ConsumptionDomain {
    fun record(
        periods: List<Period>,
        events: List<ConsumptionEvent>,
        date: LocalDate,
        today: LocalDate,
        newPeriodId: Long,
        newEventId: Long,
    ): ConsumptionChange {
        validate(periods, events)
        // A second tap still succeeds after the active start has moved to tomorrow,
        // or after a device-clock rollback. It must not allocate duplicate IDs.
        events.firstOrNull { it.date == date }?.let {
            return ConsumptionChange(periods, events, it)
        }
        require(!date.isAfter(today)) { "Нельзя отметить будущую дату" }
        require(date < LocalDate.MAX) { "Выберите другую дату" }
        val active = requireNotNull(periods.singleOrNull { it.endExclusive == null }) {
            "Сначала выберите дату начала"
        }
        val minimum = requireNotNull(minimumRecordDate(periods, events))
        require(!date.isBefore(minimum)) { "Выберите дату в текущем периоде" }
        require(events.none { it.id == newEventId }) { "Идентификатор отметки уже существует" }

        if (!date.isBefore(active.start)) {
            require(periods.none { it.id == newPeriodId }) { "Идентификатор периода уже существует" }
            val event = ConsumptionEvent(newEventId, date, active.id, newPeriodId)
            val updatedPeriods = periods.map {
                if (it.id == active.id) it.copy(endExclusive = date) else it
            } + Period(newPeriodId, date.plusDays(1))
            val updatedEvents = events + event
            validate(updatedPeriods, updatedEvents)
            return ConsumptionChange(updatedPeriods, updatedEvents, event)
        }

        // The only supported historical insertion is exactly the day before an
        // unlinked active period. Existing gaps stay unknown.
        require(active.start > LocalDate.MIN && date == active.start.minusDays(1)) {
            "Выберите дату в текущем периоде"
        }
        require(events.none { it.nextPeriodId == active.id }) {
            "Начало текущего периода уже связано с отметкой"
        }
        val lastClosed = periods.asSequence()
            .filter { it.endExclusive != null }
            .maxWithOrNull(
                compareBy<Period> { it.start }
                    .thenBy { it.endExclusive }
                    .thenBy { it.id },
            )
        val reusable = lastClosed?.takeIf { candidate ->
            events.none { it.previousPeriodId == candidate.id } &&
                (candidate.endExclusive == date || candidate.endExclusive == active.start)
        }
        val event: ConsumptionEvent
        val updatedPeriods: List<Period>
        if (reusable != null) {
            require(!reusable.start.isAfter(date)) { "Нельзя сократить соседний период до этой даты" }
            event = ConsumptionEvent(newEventId, date, reusable.id, active.id)
            updatedPeriods = periods.map {
                if (it.id == reusable.id) it.copy(endExclusive = date) else it
            }
        } else {
            require(periods.none { it.id == newPeriodId }) { "Идентификатор периода уже существует" }
            val previous = Period(newPeriodId, date, date)
            event = ConsumptionEvent(
                id = newEventId,
                date = date,
                previousPeriodId = previous.id,
                nextPeriodId = active.id,
                previousCoverageStart = active.start,
            )
            updatedPeriods = periods.flatMap {
                if (it.id == active.id) listOf(previous, it) else listOf(it)
            }
        }
        val updatedEvents = events + event
        validate(updatedPeriods, updatedEvents)
        return ConsumptionChange(updatedPeriods, updatedEvents, event)
    }

    fun edit(
        periods: List<Period>,
        events: List<ConsumptionEvent>,
        eventId: Long,
        newDate: LocalDate,
        today: LocalDate,
    ): ConsumptionChange {
        validate(periods, events)
        val original = requireNotNull(events.singleOrNull { it.id == eventId }) {
            "Отметка не найдена"
        }
        require(!newDate.isAfter(today)) { "Нельзя отметить будущую дату" }
        if (newDate == original.date) return ConsumptionChange(periods, events, original)
        require(events.none { it.id != eventId && it.date == newDate }) {
            "На эту дату уже есть отметка"
        }
        require(newDate < LocalDate.MAX) { "Выберите другую дату" }
        val allowed = bounds(periods, events, eventId, today)
        require(!newDate.isBefore(allowed.minDate) && !newDate.isAfter(allowed.maxDate)) {
            "Дата не должна пересекать соседние периоды и отметки"
        }
        val event = original.copy(date = newDate)
        val updatedPeriods = periods.map { period ->
            when (period.id) {
                original.previousPeriodId -> period.copy(
                    start = original.previousCoverageStart?.let { minOf(it, newDate) } ?: period.start,
                    endExclusive = newDate,
                )
                original.nextPeriodId -> period.copy(start = newDate.plusDays(1))
                else -> period
            }
        }
        val updatedEvents = events.map { if (it.id == eventId) event else it }
        validate(updatedPeriods, updatedEvents)
        return ConsumptionChange(updatedPeriods, updatedEvents, event)
    }

    fun bounds(
        periods: List<Period>,
        events: List<ConsumptionEvent>,
        eventId: Long,
        today: LocalDate,
    ): ConsumptionDateBounds {
        validate(periods, events)
        val event = requireNotNull(events.singleOrNull { it.id == eventId }) { "Отметка не найдена" }
        val previous = periods.single { it.id == event.previousPeriodId }
        val next = periods.single { it.id == event.nextPeriodId }
        val minimum = event.previousCoverageStart?.let(::dayBeforeOrMinimum) ?: previous.start
        val maximum = minOf(today, next.endExclusive?.minusDays(1) ?: today)
        // Empty bounds are readable after a clock rollback. Projection can still
        // display saved history while disabling the date editor.
        return ConsumptionDateBounds(minimum, maximum)
    }

    fun minimumRecordDate(periods: List<Period>, events: List<ConsumptionEvent>): LocalDate? {
        validate(periods, events)
        val active = periods.singleOrNull { it.endExclusive == null } ?: return null
        return if (events.any { it.nextPeriodId == active.id }) active.start
            else dayBeforeOrMinimum(active.start)
    }

    fun validate(periods: List<Period>, events: List<ConsumptionEvent>) {
        require(periods.map { it.id }.toSet().size == periods.size) {
            "Идентификаторы периодов должны быть уникальны"
        }
        require(periods.count { it.endExclusive == null } <= 1) { "Текущий период может быть только один" }
        periods.forEach {
            require(it.endExclusive == null || !it.endExclusive.isBefore(it.start)) {
                "Дата завершения раньше начала периода"
            }
        }
        val ordered = periods.sortedWith(
            compareBy<Period> { it.start }
                .thenBy { it.endExclusive ?: LocalDate.MAX }
                .thenBy { it.id },
        )
        ordered.zipWithNext().forEach { (previous, next) ->
            val end = previous.endExclusive
            require(end != null && !next.start.isBefore(end)) { "Периоды не должны пересекаться" }
        }
        require(events.map { it.id }.toSet().size == events.size) {
            "Идентификаторы отметок должны быть уникальны"
        }
        require(events.map { it.date }.toSet().size == events.size) { "На эту дату уже есть отметка" }
        require(events.map { it.previousPeriodId }.toSet().size == events.size) {
            "У периода может быть только одна завершающая отметка"
        }
        require(events.map { it.nextPeriodId }.toSet().size == events.size) {
            "У периода может быть только одна предшествующая отметка"
        }
        val byId = periods.associateBy { it.id }
        events.forEach { event ->
            require(event.previousPeriodId != event.nextPeriodId) { "Отметка должна связывать разные периоды" }
            require(event.date < LocalDate.MAX) { "Недоступная дата отметки" }
            val previous = requireNotNull(byId[event.previousPeriodId]) { "Предыдущий период не найден" }
            val next = requireNotNull(byId[event.nextPeriodId]) { "Следующий период не найден" }
            require(previous.endExclusive == event.date && next.start == event.date.plusDays(1)) {
                "Границы периодов не соответствуют отметке"
            }
            event.previousCoverageStart?.let { anchor ->
                require(!event.date.isBefore(dayBeforeOrMinimum(anchor))) {
                    "Отметка раньше границы сохранённой истории"
                }
                require(previous.start == minOf(anchor, event.date)) {
                    "Неверная граница сохранённой истории"
                }
                require(events.none { it.nextPeriodId == previous.id }) {
                    "Синтетический период не должен изменять соседнюю отметку"
                }
            }
        }
    }

    private fun dayBeforeOrMinimum(date: LocalDate): LocalDate =
        if (date == LocalDate.MIN) date else date.minusDays(1)
}

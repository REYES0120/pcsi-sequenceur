package pcsi.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun isCognitive(kind: SessionKind): Boolean =
    kind == SessionKind.J0 || kind == SessionKind.DEEPWORK || kind == SessionKind.DM

private fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

/**
 * Minutes de travail cognitif cumulées depuis la dernière remise à zéro.
 * Remise à zéro : fin d'une pause (faite ou skippée), fin d'un sport ou d'un cardio fait,
 * fin d'un repas. Seules les sessions FAITES du jour comptent (J0, deepwork, DM).
 */
fun continuousWork(state: State, now: LocalDateTime): Int {
    val today = now.toLocalDate()
    var reset = LocalDateTime.of(today, LocalTime.MIDNIGHT)
    for (mealEnd in listOf(Params.LUNCH_END, Params.DINNER_END)) {
        val e = LocalDateTime.of(today, mealEnd)
        if (!e.isAfter(now) && e.isAfter(reset)) reset = e
    }
    for (s in state.sessions) {
        if (s.start.toLocalDate() != today) continue
        val resets = (s.kind == SessionKind.PAUSE && s.outcome != Outcome.DECLINED) ||
            ((s.kind == SessionKind.SPORT || s.kind == SessionKind.CARDIO) && s.outcome == Outcome.DONE)
        if (resets && s.end.isAfter(reset)) reset = s.end
    }
    val r = reset
    return state.sessions
        .filter {
            isCognitive(it.kind) &&
                it.outcome == Outcome.DONE &&
                it.start.toLocalDate() == today &&
                !it.start.isBefore(r)
        }
        .sumOf { it.minutes }
}

private fun clotureDoneToday(state: State, today: LocalDate): Boolean =
    state.sessions.any {
        it.kind == SessionKind.CLOTURE && it.outcome != Outcome.DECLINED && it.start.toLocalDate() == today
    }

/**
 * (H8) Sport proposé après le dernier cours de la journée (hors EPS), avant le dîner, si :
 * pas déjà fait ou refusé aujourd'hui, moins de 3 séances cette semaine, le travail du soir
 * n'a pas commencé, pas de J0 de la veille en attente, et au moins 45 min libres.
 */
private fun sportProposal(state: State, now: LocalDateTime, raw: Int): Action? {
    val today = now.toLocalDate()
    if (!now.toLocalTime().isBefore(Params.DINNER_START)) return null
    if (state.sessions.any { it.kind == SessionKind.SPORT && it.start.toLocalDate() == today }) return null
    val week = weekStart(today)
    val count = state.sessions.count {
        it.kind == SessionKind.SPORT &&
            it.outcome == Outcome.DONE &&
            !it.start.toLocalDate().isBefore(week) &&
            !it.start.toLocalDate().isAfter(today)
    }
    if (count >= Params.SPORT_MAX_PER_WEEK) return null
    val last = Constraints.classesOn(state, today).maxByOrNull { it.end } ?: return null
    if (last.type == SlotType.EPS) return null
    val lastEnd = LocalDateTime.of(today, last.end)
    if (now.isBefore(lastEnd)) return null
    val eveningStarted = state.sessions.any {
        isCognitive(it.kind) && it.start.toLocalDate() == today && !it.start.isBefore(lastEnd)
    }
    if (eveningStarted) return null
    if (J0.pending(state, now).any { it.forDay == today.minusDays(1) }) return null
    if (raw < Params.SPORT_MIN_FREE_MIN) return null
    return Action.Sport(Params.SPORT_MIN)
}

/** Dimanche : cardio zone 2 proposé à partir de 10h (ou à la demande), une fois par semaine. */
private fun cardioProposal(state: State, now: LocalDateTime, raw: Int, onDemand: Boolean): Action? {
    if (now.dayOfWeek != DayOfWeek.SUNDAY) return null
    if (!onDemand && now.toLocalTime().isBefore(Params.CARDIO_PROPOSAL_TIME)) return null
    val today = now.toLocalDate()
    if (state.sessions.any { it.kind == SessionKind.CARDIO && it.start.toLocalDate() == today }) return null
    val week = weekStart(today)
    val doneThisWeek = state.sessions.any {
        it.kind == SessionKind.CARDIO &&
            it.outcome == Outcome.DONE &&
            !it.start.toLocalDate().isBefore(week)
    }
    if (doneThisWeek) return null
    if (raw < Params.CARDIO_MIN) return null
    return Action.Cardio(Params.CARDIO_MIN)
}

/**
 * Point d'entrée unique, appelé à chaque déclencheur. Recalcule tout depuis l'état : rien n'est reporté.
 * Ordre exact : contraintes fixes, pause, sport (cardio le dimanche), J0, deepwork, repos.
 */
fun nextAction(state: State, now: LocalDateTime, onDemand: Boolean = false): Action {
    val today = now.toLocalDate()
    val time = now.toLocalTime()

    // 1. Contraintes fixes
    if (time.isBefore(Params.WAKE)) return Action.None(Reason.NIGHT, LocalDateTime.of(today, Params.WAKE))
    if (!time.isBefore(Params.CLOTURE_START)) {
        return if (clotureDoneToday(state, today)) Action.None(Reason.NIGHT) else Action.Cloture(Params.CLOTURE_MIN)
    }
    val active = Constraints.activeBlock(state, now)
    if (active != null) {
        val reason = when (active.kind) {
            BlockKind.MEAL -> Reason.MEAL
            BlockKind.CLASS -> Reason.CLASS
            else -> Reason.EXTERNAL
        }
        return Action.None(reason, active.end)
    }
    val soon = Constraints.externalSoon(state, now)
    if (soon != null) return Action.None(Reason.EXTERNAL, soon.end)

    val raw = Constraints.rawFreeMinutes(state, now)
    val dispo = Constraints.tempsDispo(state, now)

    // 2. Pause (sacrée : testée avant tout le reste)
    val continuous = continuousWork(state, now)
    if (continuous >= Params.PAUSE_THRESHOLD_MIN) {
        val wanted =
            if (continuous <= Params.PAUSE_SHORT_MAX_CUMUL_MIN) Params.PAUSE_SHORT_MIN else Params.PAUSE_LONG_MIN
        val minutes = minOf(wanted, raw)
        return if (minutes >= Params.PAUSE_MIN_USEFUL_MIN) Action.Pause(minutes) else Action.None(Reason.TOO_SHORT)
    }

    // 3. Sport (en semaine) ou cardio (dimanche)
    val sport = sportProposal(state, now, raw)
    if (sport != null) return sport
    val cardio = cardioProposal(state, now, raw, onDemand)
    if (cardio != null) return cardio

    // 4. J0 : priorité absolue sur le deepwork quand il y a au moins 30 min
    val pending = J0.pending(state, now)
    if (pending.isNotEmpty() && dispo >= Params.J0_MIN_AVAILABLE_MIN) {
        return Action.J0Macro(pending, dispo)
    }

    // 5. Deepwork (un J0 en attente avec moins de 30 min donne un deepwork court)
    return DeepWork.propose(state, now, dispo)
}

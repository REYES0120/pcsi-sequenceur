package pcsi.engine

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

object DeepWork {

    /** Minutes de deepwork FINI de la matière sur 7 jours glissants (le J0 et les DM ne comptent pas). */
    fun deepworkMinutes7d(state: State, subject: Subject, now: LocalDateTime): Int =
        state.sessions
            .filter {
                it.kind == SessionKind.DEEPWORK &&
                    it.outcome == Outcome.DONE &&
                    it.subject == subject &&
                    it.start.isAfter(now.minusDays(7)) &&
                    !it.start.isAfter(now)
            }
            .sumOf { it.minutes }

    /**
     * Règle des DM : samedi ou dimanche, un DM non terminé dont le rendu tombe dans la semaine
     * qui suit (du lundi prochain au dimanche suivant) est forcé. Le plus proche d'abord.
     */
    fun forcedDm(state: State, now: LocalDateTime): Dm? {
        val dow = now.dayOfWeek
        if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) return null
        val nextMonday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val nextSunday = nextMonday.plusDays(6)
        return state.dms
            .filter {
                !state.dmDone.contains(it.id) &&
                    !it.due.isBefore(nextMonday) &&
                    !it.due.isAfter(nextSunday)
            }
            .sortedWith(compareBy<Dm>({ it.due }, { it.subject.ordinal }, { it.id }))
            .firstOrNull()
    }

    /**
     * Contrainte minimum de kholle : colle dans ≤7 j ET moins de 2h30 de deepwork sur 7 jours.
     * Renvoie (matière, début de la colle la plus proche), la colle la plus proche d'abord.
     */
    fun forcedByColle(state: State, now: LocalDateTime): List<Pair<Subject, LocalDateTime>> =
        Params.DEEPWORK_SUBJECTS
            .mapNotNull { s ->
                val colles = Score.collesWithin7(state, s, now)
                if (colles.isEmpty()) {
                    null
                } else if (deepworkMinutes7d(state, s, now) >= Params.KHOLLE_FORCE_MIN_DEEPWORK_MIN) {
                    null
                } else {
                    Pair(s, colles.minOf { it.startAt })
                }
            }
            .sortedBy { it.second }

    /**
     * Propose la prochaine micro-session. `dispo` = temps_dispo (buffer déjà retiré).
     * Ordre : durée minimale, DM du week-end, colle forcée, puis meilleur score au-dessus du seuil.
     * L'interleaving vient de la pénalité de récence, pas d'un découpage A/B.
     */
    fun propose(state: State, now: LocalDateTime, dispo: Int): Action {
        if (dispo < Params.DEEPWORK_MIN_PROPOSE_MIN) return Action.None(Reason.TOO_SHORT)
        val macro = minOf(dispo, Params.DEEPWORK_MACRO_MAX_MIN)
        val micro = minOf(Params.DEEPWORK_MICRO_MAX_MIN, macro)

        val dm = forcedDm(state, now)
        if (dm != null) return Action.Deepwork(dm.subject, null, micro, macro, DeepKind.DM_WEEKEND)

        val candidates = Score.candidates(state, now)
        for (forced in forcedByColle(state, now)) {
            val best = candidates.firstOrNull { it.chapter.chapter.subject == forced.first }
            if (best != null) {
                return Action.Deepwork(forced.first, best.chapter.chapter.name, micro, macro, DeepKind.KHOLLE_FORCED)
            }
        }

        val top = candidates.firstOrNull() ?: return Action.None(Reason.REST)
        if (top.score.total <= Params.SCORE_THRESHOLD) return Action.None(Reason.REST)
        return Action.Deepwork(
            top.chapter.chapter.subject, top.chapter.chapter.name, micro, macro, DeepKind.SCORE
        )
    }
}

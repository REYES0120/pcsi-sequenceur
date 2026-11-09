package pcsi.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object J0 {

    private fun endedBy(day: LocalDate, end: LocalTime, now: LocalDateTime): Boolean =
        !LocalDateTime.of(day, end).isAfter(now)

    /**
     * (H10) Matières en attente de J0 : un créneau cours, TD ou TP (hors EPS) terminé, aujourd'hui
     * ou hier, sans J0 fait ni skippé pour (matière, jour). Plus vieux que la veille : abandonné.
     * Rien n'est stocké : l'attente se déduit de l'emploi du temps et du journal des sessions.
     * Ordre : la veille d'abord, puis l'ordre des cours.
     */
    fun pending(state: State, now: LocalDateTime): List<J0Item> {
        val today = now.toLocalDate()
        val out = ArrayList<J0Item>()
        for (day in listOf(today.minusDays(1), today)) {
            val subjects = Constraints.classesOn(state, day)
                .filter { it.type != SlotType.EPS && it.subject != null && endedBy(day, it.end, now) }
                .mapNotNull { it.subject }
                .distinct()
            for (s in subjects) {
                val handled = state.sessions.any {
                    it.kind == SessionKind.J0 &&
                        it.subject == s &&
                        it.forDay == day &&
                        it.outcome != Outcome.DECLINED
                }
                if (!handled) out.add(J0Item(s, day))
            }
        }
        return out
    }

    /**
     * Après ton choix d'une matière : timer = min(40 min, temps_dispo / N_restant).
     * Plancher de 10 min, jamais au-delà du temps disponible. Null si la matière n'est plus en attente
     * ou s'il n'y a pas 10 min.
     */
    fun micro(state: State, now: LocalDateTime, choice: J0Item): Action.J0Micro? {
        val pending = pending(state, now)
        if (!pending.contains(choice)) return null
        val dispo = Constraints.tempsDispo(state, now)
        val share = dispo / pending.size
        val wanted = minOf(Params.J0_MICRO_MAX_MIN, share)
        val minutes = minOf(maxOf(wanted, Params.MIN_SESSION_MIN), dispo)
        if (minutes < Params.MIN_SESSION_MIN) return null
        return Action.J0Micro(choice, minutes)
    }
}

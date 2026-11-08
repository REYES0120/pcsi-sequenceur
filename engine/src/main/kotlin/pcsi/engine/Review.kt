package pcsi.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** validations = nombre de paliers validés ; nextReview = null quand tous les paliers sont faits. */
data class ChapterState(
    val chapter: Chapter,
    val validations: Int,
    val nextReview: LocalDate?
)

object Review {

    fun states(state: State): List<ChapterState> = state.chapters.map { chapterState(state, it) }

    /**
     * (H1) Palier 0 validé si un J0 FAIT existe pour la matière et le jour de la vue.
     * Chaque deepwork FINI sur ce chapitre valide un palier de plus.
     * Un skip ne valide rien. Les échéances se comptent depuis la date de vue.
     */
    fun chapterState(state: State, c: Chapter): ChapterState {
        val j0Done = state.sessions.any {
            it.kind == SessionKind.J0 &&
                it.outcome == Outcome.DONE &&
                it.subject == c.subject &&
                it.forDay == c.seen
        }
        val deepworkDone = state.sessions.count {
            it.kind == SessionKind.DEEPWORK &&
                it.outcome == Outcome.DONE &&
                it.subject == c.subject &&
                it.chapter == c.name
        }
        val validations = (if (j0Done) 1 else 0) + deepworkDone
        val offsets = Params.REVIEW_OFFSETS_DAYS
        val next: LocalDate? =
            if (validations < offsets.size) c.seen.plusDays(offsets[validations].toLong()) else null
        return ChapterState(c, validations, next)
    }

    /** urgence_chapitre (section 7). */
    fun urgence(next: LocalDate?, today: LocalDate): Double {
        if (next == null) return 0.0
        val late = ChronoUnit.DAYS.between(next, today)
        return when {
            late >= 2L -> 6.0
            late == 1L -> 5.0
            late == 0L -> 4.0
            late == -1L -> 3.0
            late == -2L -> 2.0
            late == -3L -> 1.0
            else -> 0.0
        }
    }
}

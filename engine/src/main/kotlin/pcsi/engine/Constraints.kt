package pcsi.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class BlockKind { CLASS, COLLE, DS, MEAL }

/** Bloc de temps fixe dans la journée : aucune session de travail ne peut y tomber. */
data class Block(
    val kind: BlockKind,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val label: String
)

object Constraints {

    fun isDayOff(state: State, date: LocalDate): Boolean = state.daysOff.any { it.contains(date) }

    /** Semaine A ou B, d'après un lundi de référence de semaine A. */
    fun weekKind(state: State, date: LocalDate): WeekKind {
        val a = state.weekAReference.with(DayOfWeek.MONDAY)
        val d = date.with(DayOfWeek.MONDAY)
        val weeks = ChronoUnit.WEEKS.between(a, d)
        return if (Math.floorMod(weeks, 2L) == 0L) WeekKind.A else WeekKind.B
    }

    /** Créneaux de cours du jour (semestre courant, bonne semaine), triés. Aucun les jours de congé. */
    fun classesOn(state: State, date: LocalDate): List<Slot> {
        if (isDayOff(state, date)) return emptyList()
        val wk = weekKind(state, date)
        return state.slots
            .filter {
                it.semester == state.semester &&
                    it.day == date.dayOfWeek &&
                    (it.week == WeekKind.BOTH || it.week == wk)
            }
            .sortedBy { it.start }
    }

    /** Tous les blocs fixes du jour : cours, colles, DS, déjeuner, dîner. */
    fun blocksOn(state: State, date: LocalDate): List<Block> {
        val out = ArrayList<Block>()
        for (s in classesOn(state, date)) {
            out.add(
                Block(
                    BlockKind.CLASS,
                    LocalDateTime.of(date, s.start),
                    LocalDateTime.of(date, s.end),
                    s.subject?.name ?: "EPS"
                )
            )
        }
        for (c in state.colles) {
            if (c.date == date) out.add(Block(BlockKind.COLLE, c.startAt, c.endAt, c.subject.name))
        }
        for (d in state.ds) {
            if (d.date == date) {
                out.add(Block(BlockKind.DS, d.startAt, d.endAt, d.subjects.joinToString("+") { it.name }))
            }
        }
        out.add(Block(BlockKind.MEAL, LocalDateTime.of(date, Params.LUNCH_START), LocalDateTime.of(date, Params.LUNCH_END), "Déjeuner"))
        out.add(Block(BlockKind.MEAL, LocalDateTime.of(date, Params.DINNER_START), LocalDateTime.of(date, Params.DINNER_END), "Dîner"))
        return out.sortedBy { it.start }
    }

    /** Bloc en cours à l'instant `now` (celui qui finit le plus tard s'il y en a plusieurs). */
    fun activeBlock(state: State, now: LocalDateTime): Block? =
        blocksOn(state, now.toLocalDate())
            .filter { !now.isBefore(it.start) && now.isBefore(it.end) }
            .maxByOrNull { it.end }

    /** Colle ou DS en cours, ou qui commence dans moins de 45 min : on n'a rien à proposer. */
    fun externalSoon(state: State, now: LocalDateTime): Block? {
        val limit = now.plusMinutes(Params.EXTERNAL_MARGIN_MIN.toLong())
        return blocksOn(state, now.toLocalDate()).firstOrNull {
            (it.kind == BlockKind.COLLE || it.kind == BlockKind.DS) &&
                it.end.isAfter(now) &&
                !it.start.isAfter(limit)
        }
    }

    /** Minutes jusqu'à la prochaine contrainte fixe (début de bloc, ou clôture à 22h15). */
    fun rawFreeMinutes(state: State, now: LocalDateTime): Int {
        val date = now.toLocalDate()
        var next: LocalDateTime = LocalDateTime.of(date, Params.CLOTURE_START)
        for (b in blocksOn(state, date)) {
            if (b.start.isAfter(now) && b.start.isBefore(next)) next = b.start
        }
        val m = ChronoUnit.MINUTES.between(now, next)
        return if (m < 0L) 0 else m.toInt()
    }

    /** temps_dispo = temps libre jusqu'à la prochaine contrainte, moins le buffer de 5 min. */
    fun tempsDispo(state: State, now: LocalDateTime): Int =
        maxOf(0, rawFreeMinutes(state, now) - Params.BUFFER_MIN)

    /**
     * Instants où l'application doit recalculer : fins de cours, de colle, de DS et de repas,
     * clôture, et le dimanche 10h (cardio). Ce sont des horaires, pas un plan.
     */
    fun triggersOn(state: State, date: LocalDate): List<LocalDateTime> {
        val out = ArrayList<LocalDateTime>()
        for (b in blocksOn(state, date)) out.add(b.end)
        out.add(LocalDateTime.of(date, Params.CLOTURE_START))
        if (date.dayOfWeek == DayOfWeek.SUNDAY) out.add(LocalDateTime.of(date, Params.CARDIO_PROPOSAL_TIME))
        return out.distinct().sorted()
    }
}

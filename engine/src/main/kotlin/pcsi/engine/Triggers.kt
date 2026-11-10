package pcsi.engine

import java.time.LocalDateTime

object Triggers {
    /**
     * Prochain instant de recalcul après `now` (fin de cours, de colle, de DS, de repas, clôture,
     * dimanche 10h), cherché jusqu'à 14 jours devant. Le téléphone ne programme qu'une alarme à la fois.
     */
    fun next(state: State, now: LocalDateTime): LocalDateTime? {
        var day = now.toLocalDate()
        for (i in 0..14) {
            val t = Constraints.triggersOn(state, day).firstOrNull { it.isAfter(now) }
            if (t != null) return t
            day = day.plusDays(1)
        }
        return null
    }
}

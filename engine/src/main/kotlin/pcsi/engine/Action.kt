package pcsi.engine

import java.time.LocalDate
import java.time.LocalDateTime

/** Pourquoi le moteur ne propose rien. */
enum class Reason { NIGHT, MEAL, CLASS, EXTERNAL, TOO_SHORT, REST }

/** D'où vient le choix du deepwork. */
enum class DeepKind { SCORE, KHOLLE_FORCED, DM_WEEKEND }

/** Une matière en attente de J0, pour le jour de cours `forDay` (aujourd'hui ou hier). */
data class J0Item(val subject: Subject, val forDay: LocalDate)

/**
 * Ce que le moteur propose maintenant. Aucune action ne démarre un timer : c'est toujours
 * « Prêt ? » d'abord. `None` ne produit aucune notification.
 */
sealed class Action {
    data class None(val reason: Reason, val until: LocalDateTime? = null) : Action()
    data class Cloture(val minutes: Int) : Action()
    data class Pause(val minutes: Int) : Action()
    data class Sport(val minutes: Int) : Action()
    data class Cardio(val minutes: Int) : Action()

    /** « J0 — choisis une matière » avec la liste des matières en attente. */
    data class J0Macro(val pending: List<J0Item>, val minutesAvailable: Int) : Action()

    /** Une matière de J0 choisie : « J0 Maths — 40 min estimées. Prêt ? » */
    data class J0Micro(val item: J0Item, val minutes: Int) : Action()

    /**
     * « Deepwork {macro} min — première micro-session : {matière} {micro} min. Prêt ? »
     * `chapter` est null pour un DM.
     */
    data class Deepwork(
        val subject: Subject,
        val chapter: String?,
        val microMinutes: Int,
        val macroMinutes: Int,
        val kind: DeepKind
    ) : Action()
}

package pcsi.engine

import java.time.LocalDate

/**
 * Les seuls textes affichés : matière, type de session, durée estimée, « Prêt ? ».
 * Aucun protocole, aucune phrase de coaching.
 */
object Texts {

    fun subject(s: Subject): String = when (s) {
        Subject.MATHS -> "Maths"
        Subject.PHYSIQUE -> "Physique"
        Subject.CHIMIE -> "Chimie"
        Subject.SI -> "SI"
        Subject.ANGLAIS -> "Anglais"
        Subject.INFO -> "Info"
        Subject.PHILO -> "Philo"
    }

    fun j0Item(item: J0Item, today: LocalDate): String {
        val name = subject(item.subject)
        return when {
            item.forDay == today -> name
            item.forDay == today.minusDays(1) -> "$name (hier)"
            else -> "$name (${item.forDay})"
        }
    }

    fun title(a: Action, today: LocalDate): String = when (a) {
        is Action.None -> ""
        is Action.Cloture -> "Clôture — ${a.minutes} min. Prêt ?"
        is Action.Pause -> "Pause — ${a.minutes} min. Prêt ?"
        is Action.Sport -> "Sport — ${a.minutes} min musculation. Oui / Non ?"
        is Action.Cardio -> "Cardio zone 2 — ${a.minutes} min. Oui / Non ?"
        is Action.J0Macro -> "J0 — choisis une matière"
        is Action.J0Micro -> "J0 ${j0Item(a.item, today)} — ${a.minutes} min estimées. Prêt ?"
        is Action.Deepwork -> {
            val name = if (a.kind == DeepKind.DM_WEEKEND) "DM" else "Deepwork"
            val subj = subject(a.subject)
            if (a.macroMinutes > a.microMinutes) {
                "$name ${a.macroMinutes} min — micro-session : $subj ${a.microMinutes} min. Prêt ?"
            } else if (a.kind == DeepKind.DM_WEEKEND) {
                "Prochaine session : DM $subj — ${a.microMinutes} min estimées. Prêt ?"
            } else {
                "Prochaine session : Deepwork $subj — ${a.microMinutes} min estimées. Prêt ?"
            }
        }
    }

    /** Deuxième ligne : le chapitre choisi pour un deepwork, ou la liste du J0. */
    fun body(a: Action, today: LocalDate): String = when (a) {
        is Action.Deepwork -> if (a.chapter == null) "" else "Chapitre : ${a.chapter}"
        is Action.J0Macro -> a.pending.joinToString(", ") { j0Item(it, today) }
        else -> ""
    }

    fun kindLabel(kind: SessionKind, subject: Subject?): String {
        val subj = if (subject == null) "" else " " + subject(subject)
        return when (kind) {
            SessionKind.J0 -> "J0$subj"
            SessionKind.DEEPWORK -> "Deepwork$subj"
            SessionKind.DM -> "DM$subj"
            SessionKind.SPORT -> "Sport"
            SessionKind.CARDIO -> "Cardio"
            SessionKind.PAUSE -> "Pause"
            SessionKind.CLOTURE -> "Clôture"
        }
    }

    fun activeLabel(s: ActiveSession): String = kindLabel(s.kind, s.subject)
}

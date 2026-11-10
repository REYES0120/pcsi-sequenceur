package pcsi.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Une proposition affichée : elle expire après 15 min sans validation et n'est alors pas comptée. */
data class Proposal(val id: String, val action: Action, val createdAt: LocalDateTime) {
    val expiresAt: LocalDateTime get() = createdAt.plusMinutes(Params.PROPOSAL_EXPIRY_MIN.toLong())
}

/** Session dont le timer tourne. Le timer a démarré au « Prêt », jamais avant. */
data class ActiveSession(
    val kind: SessionKind,
    val subject: Subject?,
    val chapter: String?,
    val forDay: LocalDate?,
    val startedAt: LocalDateTime,
    val plannedMinutes: Int,
    /** Vrai après « débordé » : le décompte continue, rien n'est forcé. */
    val overrun: Boolean = false
) {
    val plannedEnd: LocalDateTime get() = startedAt.plusMinutes(plannedMinutes.toLong())
}

/** EARLY : « fini » avant la fin ou après un débordement (durée réelle). TIMER_DONE : « terminé » (durée prévue). */
enum class FinishMode { EARLY, TIMER_DONE }

/**
 * Transitions entre une proposition, une session en cours et le journal.
 * Skip : session comptée faite, sans validation de chapitre. Refus : « Non » au sport ou au cardio.
 * Non-validation : aucune trace (la proposition expire).
 */
object Lifecycle {

    fun isExpired(p: Proposal, now: LocalDateTime): Boolean = !now.isBefore(p.expiresAt)

    /** Seul le J0 « macro » et l'absence de proposition ne démarrent pas de timer. */
    fun startable(a: Action): Boolean = when (a) {
        is Action.Cloture, is Action.Pause, is Action.Sport, is Action.Cardio,
        is Action.J0Micro, is Action.Deepwork -> true
        else -> false
    }

    /** Démarre la session au « Prêt ». Null si la proposition est périmée ou ne démarre pas de timer. */
    fun start(p: Proposal, now: LocalDateTime): ActiveSession? {
        if (isExpired(p, now)) return null
        return when (val a = p.action) {
            is Action.Cloture -> ActiveSession(SessionKind.CLOTURE, null, null, null, now, a.minutes)
            is Action.Pause -> ActiveSession(SessionKind.PAUSE, null, null, null, now, a.minutes)
            is Action.Sport -> ActiveSession(SessionKind.SPORT, null, null, null, now, a.minutes)
            is Action.Cardio -> ActiveSession(SessionKind.CARDIO, null, null, null, now, a.minutes)
            is Action.J0Micro -> ActiveSession(SessionKind.J0, a.item.subject, null, a.item.forDay, now, a.minutes)
            is Action.Deepwork ->
                if (a.kind == DeepKind.DM_WEEKEND) {
                    ActiveSession(SessionKind.DM, a.subject, null, null, now, a.microMinutes)
                } else {
                    ActiveSession(SessionKind.DEEPWORK, a.subject, a.chapter, null, now, a.microMinutes)
                }
            else -> null
        }
    }

    fun finish(s: ActiveSession, now: LocalDateTime, mode: FinishMode): SessionRecord {
        val minutes = when (mode) {
            FinishMode.TIMER_DONE -> s.plannedMinutes
            FinishMode.EARLY -> maxOf(1, ((Duration.between(s.startedAt, now).seconds + 30) / 60).toInt())
        }
        return SessionRecord(s.kind, Outcome.DONE, s.startedAt, minutes, s.subject, s.chapter, s.forDay)
    }

    /** Skip d'une proposition : compté fait, durée 0, aucun chapitre validé. Null pour le sport et le cardio (Oui / Non). */
    fun skip(a: Action, now: LocalDateTime): SessionRecord? = when (a) {
        is Action.Cloture -> SessionRecord(SessionKind.CLOTURE, Outcome.SKIPPED, now, 0)
        is Action.Pause -> SessionRecord(SessionKind.PAUSE, Outcome.SKIPPED, now, 0)
        is Action.J0Micro -> skipJ0(a.item, now)
        is Action.Deepwork -> SessionRecord(
            if (a.kind == DeepKind.DM_WEEKEND) SessionKind.DM else SessionKind.DEEPWORK,
            Outcome.SKIPPED, now, 0, a.subject, a.chapter
        )
        else -> null
    }

    /** Skip d'une matière choisie dans la liste du J0 (matière sans contenu). */
    fun skipJ0(item: J0Item, now: LocalDateTime): SessionRecord =
        SessionRecord(SessionKind.J0, Outcome.SKIPPED, now, 0, item.subject, null, item.forDay)

    /** « Non » au sport ou au cardio : ni compté, ni skippé, juste pas fait aujourd'hui. */
    fun decline(a: Action, now: LocalDateTime): SessionRecord? = when (a) {
        is Action.Sport -> SessionRecord(SessionKind.SPORT, Outcome.DECLINED, now, 0)
        is Action.Cardio -> SessionRecord(SessionKind.CARDIO, Outcome.DECLINED, now, 0)
        else -> null
    }
}

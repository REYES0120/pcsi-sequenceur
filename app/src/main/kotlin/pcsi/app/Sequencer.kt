package pcsi.app

import android.content.Context
import pcsi.engine.Action
import pcsi.engine.FinishMode
import pcsi.engine.J0
import pcsi.engine.J0Item
import pcsi.engine.Lifecycle
import pcsi.engine.Proposal
import pcsi.engine.Reason
import pcsi.engine.Triggers
import pcsi.engine.nextAction
import java.time.LocalDateTime
import java.util.UUID

/**
 * Chef d'orchestre : à chaque déclencheur, recalcule tout depuis l'état et propose.
 * Ne démarre jamais un timer sans « Prêt ». Une non-validation n'enregistre rien.
 */
object Sequencer {

    /** Recalcule et poste la proposition. `manual` = demande manuelle (cardio du dimanche avant 10h). */
    fun recalc(ctx: Context, manual: Boolean = false): Action {
        val now = LocalDateTime.now()
        Notifier.ensureChannels(ctx)
        val state = Repo.loadState(ctx)
        if (state == null) {
            Repo.saveProposal(ctx, null)
            Notifier.cancelProposal(ctx)
            Scheduler.scheduleTrigger(ctx, null)
            WidgetProvider.refresh(ctx)
            return Action.None(Reason.REST)
        }
        Scheduler.scheduleTrigger(ctx, Triggers.next(state, now))

        val active = Repo.active(ctx)
        if (active != null) {
            // une session tourne : on réarme seulement l'alarme de fin (utile après un redémarrage)
            if (!active.overrun) Scheduler.scheduleTimerEnd(ctx, Scheduler.millis(active.plannedEnd))
            WidgetProvider.refresh(ctx)
            return Action.None(Reason.REST)
        }

        val action = nextAction(state, now, manual)
        if (action is Action.None) {
            Repo.saveProposal(ctx, null)
            Notifier.cancelProposal(ctx)
        } else {
            val p = Proposal(UUID.randomUUID().toString(), action, now)
            Repo.saveProposal(ctx, p)
            Notifier.postProposal(ctx, p)
        }
        WidgetProvider.refresh(ctx)
        return action
    }

    /** Proposition encore valable et bien celle qu'on valide (sinon : périmée, on recalcule). */
    private fun validProposal(ctx: Context, id: String, now: LocalDateTime): Proposal? {
        val p = Repo.proposal(ctx) ?: return null
        if (p.id != id || Lifecycle.isExpired(p, now)) return null
        return p
    }

    /** « Prêt » (ou « Oui ») : le timer démarre maintenant, pas avant. */
    fun ready(ctx: Context, id: String) {
        val now = LocalDateTime.now()
        val p = validProposal(ctx, id, now)
        if (p == null) {
            recalc(ctx)
            return
        }
        val s = Lifecycle.start(p, now) ?: return
        Repo.saveActive(ctx, s)
        Repo.saveProposal(ctx, null)
        Notifier.cancelProposal(ctx)
        Notifier.postActive(ctx, s)
        Scheduler.scheduleTimerEnd(ctx, Scheduler.millis(s.plannedEnd))
        WidgetProvider.refresh(ctx)
    }

    /** « Skip » : session comptée faite, aucun chapitre validé, puis recalcul. */
    fun skip(ctx: Context, id: String) {
        val now = LocalDateTime.now()
        val p = validProposal(ctx, id, now)
        if (p == null) {
            recalc(ctx)
            return
        }
        val record = Lifecycle.skip(p.action, now) ?: return
        Repo.record(ctx, record, now)
        recalc(ctx)
    }

    /** « Non » au sport ou au cardio : pas fait aujourd'hui, pas reproposé. */
    fun decline(ctx: Context, id: String) {
        val now = LocalDateTime.now()
        val p = validProposal(ctx, id, now)
        if (p == null) {
            recalc(ctx)
            return
        }
        val record = Lifecycle.decline(p.action, now) ?: return
        Repo.record(ctx, record, now)
        recalc(ctx)
    }

    /** « Fini » (durée réelle) ou « Terminé » (durée prévue) : session validée, puis micro-session suivante. */
    fun finish(ctx: Context, mode: FinishMode) {
        val now = LocalDateTime.now()
        val s = Repo.active(ctx) ?: return
        Repo.record(ctx, Lifecycle.finish(s, now, mode), now)
        Repo.saveActive(ctx, null)
        Notifier.cancelActive(ctx)
        Notifier.cancelEnd(ctx)
        Scheduler.cancelTimer(ctx)
        recalc(ctx)
    }

    /** « Débordé » : le décompte devient un chrono qui monte, rien n'est forcé. */
    fun overrun(ctx: Context) {
        val s = Repo.active(ctx) ?: return
        val o = s.copy(overrun = true)
        Repo.saveActive(ctx, o)
        Notifier.cancelEnd(ctx)
        Notifier.postActive(ctx, o)
        WidgetProvider.refresh(ctx)
    }

    /** L'alarme de fin de timer a sonné. */
    fun timerEnded(ctx: Context) {
        val s = Repo.active(ctx) ?: return
        if (s.overrun) return
        Notifier.ensureChannels(ctx)
        Notifier.postEnd(ctx, s)
    }

    /** J0 : une matière choisie dans la liste → « J0 Maths — 40 min estimées. Prêt ? » */
    fun chooseJ0(ctx: Context, item: J0Item): Boolean {
        val now = LocalDateTime.now()
        val state = Repo.loadState(ctx) ?: return false
        val micro = J0.micro(state, now, item) ?: return false
        val p = Proposal(UUID.randomUUID().toString(), micro, now)
        Repo.saveProposal(ctx, p)
        Notifier.postProposal(ctx, p)
        WidgetProvider.refresh(ctx)
        return true
    }

    /** J0 : matière sans contenu, skippée explicitement. */
    fun skipJ0(ctx: Context, item: J0Item) {
        val now = LocalDateTime.now()
        Repo.record(ctx, Lifecycle.skipJ0(item, now), now)
        recalc(ctx)
    }
}

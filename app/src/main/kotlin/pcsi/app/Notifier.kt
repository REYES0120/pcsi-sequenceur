package pcsi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import pcsi.engine.ActiveSession
import pcsi.engine.Action
import pcsi.engine.Params
import pcsi.engine.Proposal
import pcsi.engine.Texts

/** Notifications : une proposition, un timer en cours, une fin de timer. Jamais de phrase de coaching. */
object Notifier {
    private const val CH_PROPOSAL = "proposals"
    private const val CH_TIMER = "timer"
    private const val CH_ALERT = "alert"
    private const val ID_PROPOSAL = 1
    private const val ID_ACTIVE = 2
    private const val ID_END = 3

    private fun manager(ctx: Context): NotificationManager = ctx.getSystemService(NotificationManager::class.java)

    fun ensureChannels(ctx: Context) {
        val nm = manager(ctx)
        nm.createNotificationChannel(NotificationChannel(CH_PROPOSAL, "Propositions", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Timer en cours", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Fin de timer", NotificationManager.IMPORTANCE_HIGH))
    }

    /** Bouton qui envoie une action à ActionReceiver (notification ou widget). */
    fun broadcast(ctx: Context, action: String, proposalId: String?, code: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
        i.setAction(action)
        if (proposalId != null) i.putExtra(Actions.EXTRA_ID, proposalId)
        return PendingIntent.getBroadcast(
            ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun button(label: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(android.R.drawable.ic_menu_send, label, pi).build()

    fun postProposal(ctx: Context, p: Proposal) {
        val today = p.createdAt.toLocalDate()
        val a = p.action
        val b = Notification.Builder(ctx, CH_PROPOSAL)
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
        b.setContentTitle(Texts.title(a, today))
        b.setContentIntent(openApp(ctx))
        b.setTimeoutAfter(Params.PROPOSAL_EXPIRY_MIN * 60_000L)
        val body = Texts.body(a, today)
        if (body.isNotEmpty()) b.setContentText(body)
        when (a) {
            is Action.Sport, is Action.Cardio -> {
                b.addAction(button("Oui", broadcast(ctx, Actions.READY, p.id, 11)))
                b.addAction(button("Non", broadcast(ctx, Actions.NO, p.id, 12)))
            }
            is Action.J0Macro -> {
                // on touche la notification pour choisir la matière dans l'application
            }
            else -> {
                b.addAction(button("Prêt", broadcast(ctx, Actions.READY, p.id, 11)))
                b.addAction(button("Skip", broadcast(ctx, Actions.SKIP, p.id, 13)))
            }
        }
        manager(ctx).notify(ID_PROPOSAL, b.build())
    }

    /** Timer en cours : le décompte est affiché par le système, il continue même si l'app est arrêtée. */
    fun postActive(ctx: Context, s: ActiveSession) {
        val b = Notification.Builder(ctx, CH_TIMER)
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        b.setContentTitle(Texts.activeLabel(s))
        b.setOngoing(true)
        b.setContentIntent(openApp(ctx))
        b.setUsesChronometer(true)
        if (s.overrun) {
            b.setWhen(Scheduler.millis(s.startedAt))
            b.setChronometerCountDown(false)
            b.setContentText("Débordé")
        } else {
            b.setWhen(Scheduler.millis(s.plannedEnd))
            b.setChronometerCountDown(true)
        }
        b.addAction(button("Fini", broadcast(ctx, Actions.FINISH, null, 14)))
        manager(ctx).notify(ID_ACTIVE, b.build())
    }

    fun postEnd(ctx: Context, s: ActiveSession) {
        val b = Notification.Builder(ctx, CH_ALERT)
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        b.setContentTitle(Texts.activeLabel(s) + " — timer terminé")
        b.setContentIntent(openApp(ctx))
        b.addAction(button("Terminé", broadcast(ctx, Actions.TIMER_DONE, null, 15)))
        b.addAction(button("Débordé", broadcast(ctx, Actions.OVERRUN, null, 16)))
        manager(ctx).notify(ID_END, b.build())
    }

    fun cancelProposal(ctx: Context) = manager(ctx).cancel(ID_PROPOSAL)

    fun cancelActive(ctx: Context) = manager(ctx).cancel(ID_ACTIVE)

    fun cancelEnd(ctx: Context) = manager(ctx).cancel(ID_END)
}

package pcsi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pcsi.engine.FinishMode

/** Boutons des notifications et du widget. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Actions.EXTRA_ID) ?: ""
        when (intent.action) {
            Actions.READY -> Sequencer.ready(context, id)
            Actions.SKIP -> Sequencer.skip(context, id)
            Actions.NO -> Sequencer.decline(context, id)
            Actions.FINISH -> Sequencer.finish(context, FinishMode.EARLY)
            Actions.TIMER_DONE -> Sequencer.finish(context, FinishMode.TIMER_DONE)
            Actions.OVERRUN -> Sequencer.overrun(context)
            Actions.RECALC -> {
                Sequencer.recalc(context, true)
            }
            else -> {}
        }
    }
}

/** Alarme du prochain horaire fixe (fin de cours, de repas, clôture, dimanche 10h). */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sequencer.recalc(context)
    }
}

/** Alarme de fin de timer. */
class TimerEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sequencer.timerEnded(context)
    }
}

/** Alarme de clôture automatique : le recalcul clôture la session si l'heure est passée. */
class AutoCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sequencer.recalc(context)
    }
}

/** Redémarrage, mise à jour de l'app, changement d'heure ou de fuseau : on réarme tout. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sequencer.recalc(context)
    }
}

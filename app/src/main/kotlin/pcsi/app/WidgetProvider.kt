package pcsi.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import pcsi.engine.Action
import pcsi.engine.Lifecycle
import pcsi.engine.Texts
import java.time.LocalDateTime

/** Widget : une ligne et au plus trois boutons. Aucun protocole, aucune fiche. */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val now = LocalDateTime.now()
            val active = Repo.active(ctx)
            val p = Repo.proposal(ctx)

            v.setViewVisibility(R.id.w_ready, View.VISIBLE)
            v.setViewVisibility(R.id.w_skip, View.VISIBLE)
            v.setViewVisibility(R.id.w_more, View.VISIBLE)
            v.setOnClickPendingIntent(R.id.w_more, Notifier.broadcast(ctx, Actions.RECALC, null, 21))

            if (active != null) {
                val state = if (active.overrun) " — débordé" else " — en cours"
                v.setTextViewText(R.id.w_text, Texts.activeLabel(active) + state)
                v.setTextViewText(R.id.w_ready, "Fini")
                v.setOnClickPendingIntent(R.id.w_ready, Notifier.broadcast(ctx, Actions.FINISH, null, 22))
                v.setViewVisibility(R.id.w_skip, View.GONE)
                v.setViewVisibility(R.id.w_more, View.GONE)
            } else if (p != null && !Lifecycle.isExpired(p, now)) {
                v.setTextViewText(R.id.w_text, Texts.title(p.action, p.createdAt.toLocalDate()))
                val a = p.action
                if (a is Action.Sport || a is Action.Cardio) {
                    v.setTextViewText(R.id.w_ready, "Oui")
                    v.setTextViewText(R.id.w_skip, "Non")
                    v.setOnClickPendingIntent(R.id.w_ready, Notifier.broadcast(ctx, Actions.READY, p.id, 23))
                    v.setOnClickPendingIntent(R.id.w_skip, Notifier.broadcast(ctx, Actions.NO, p.id, 24))
                } else if (a is Action.J0Macro) {
                    v.setTextViewText(R.id.w_ready, "Choisir")
                    v.setOnClickPendingIntent(R.id.w_ready, Notifier.openApp(ctx))
                    v.setViewVisibility(R.id.w_skip, View.GONE)
                } else {
                    v.setTextViewText(R.id.w_ready, "Prêt")
                    v.setTextViewText(R.id.w_skip, "Skip")
                    v.setOnClickPendingIntent(R.id.w_ready, Notifier.broadcast(ctx, Actions.READY, p.id, 23))
                    v.setOnClickPendingIntent(R.id.w_skip, Notifier.broadcast(ctx, Actions.SKIP, p.id, 25))
                }
            } else {
                v.setTextViewText(R.id.w_text, "Rien à proposer")
                v.setViewVisibility(R.id.w_ready, View.GONE)
                v.setViewVisibility(R.id.w_skip, View.GONE)
            }
            return v
        }
    }
}

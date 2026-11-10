package pcsi.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Une seule alarme de recalcul à la fois (le prochain horaire fixe) et une alarme de fin de timer.
 * Fin de timer : alarme de type « réveil » (la plus prioritaire pour Android, même en veille profonde).
 */
object Scheduler {
    private const val RC_TRIGGER = 100
    private const val RC_TIMER = 101

    fun millis(t: LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun broadcast(ctx: Context, cls: Class<*>, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduleTrigger(ctx: Context, at: LocalDateTime?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, TriggerReceiver::class.java, RC_TRIGGER)
        am.cancel(pi)
        if (at == null) return
        val m = millis(at)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m, pi)
        } catch (ex: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m, pi)
        }
    }

    fun scheduleTimerEnd(ctx: Context, endMillis: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, TimerEndReceiver::class.java, RC_TIMER)
        am.cancel(pi)
        val show = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(endMillis, show), pi)
        } catch (ex: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi)
        }
    }

    fun cancelTimer(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(broadcast(ctx, TimerEndReceiver::class.java, RC_TIMER))
    }
}

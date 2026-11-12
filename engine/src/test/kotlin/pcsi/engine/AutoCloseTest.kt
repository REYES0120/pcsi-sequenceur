package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AutoCloseTest {

    private fun at(h: Int, m: Int, s: Int = 0): LocalDateTime = LocalDateTime.of(2026, 11, 9, h, m, s)

    // démarrée à 20h05, 45 min prévues → fin du timer à 20h50
    private val active = ActiveSession(SessionKind.DEEPWORK, Subject.MATHS, "Suites", null, at(20, 5), 45)

    @Test
    fun clotureQuinzeMinutesApresLaFinDuTimer() {
        assertEquals(at(21, 5), Lifecycle.autoCloseAt(active))
    }

    @Test
    fun pasDeClotureAvantLEcheance() {
        assertFalse(Lifecycle.autoCloseDue(active, at(20, 40))) // timer en cours
        assertFalse(Lifecycle.autoCloseDue(active, at(20, 50))) // timer terminé, on attend la réponse
        assertFalse(Lifecycle.autoCloseDue(active, at(21, 4, 59)))
        assertNull(Lifecycle.autoClose(active, at(21, 4, 59)))
    }

    @Test
    fun clotureALEcheanceEnDureePrevue() {
        assertTrue(Lifecycle.autoCloseDue(active, at(21, 5)))
        val r = Lifecycle.autoClose(active, at(21, 5))!!
        assertEquals(
            SessionRecord(SessionKind.DEEPWORK, Outcome.DONE, at(20, 5), 45, Subject.MATHS, "Suites", null),
            r
        )
        // même une clôture tardive (téléphone éteint, alarme perdue) garde la durée prévue
        assertEquals(45, Lifecycle.autoClose(active, at(23, 0))!!.minutes)
    }

    @Test
    fun aucuneClotureApresUnDebord() {
        val overrun = active.copy(overrun = true)
        assertNull(Lifecycle.autoCloseAt(overrun))
        assertFalse(Lifecycle.autoCloseDue(overrun, at(23, 59)))
        assertNull(Lifecycle.autoClose(overrun, at(23, 59)))
    }

    @Test
    fun leJ0EtLeSportSontAussiClotures() {
        val j0 = ActiveSession(SessionKind.J0, Subject.SI, null, LocalDateTime.of(2026, 11, 8, 0, 0).toLocalDate(), at(18, 0), 40)
        val r = Lifecycle.autoClose(j0, at(18, 55))!!
        assertEquals(Outcome.DONE, r.outcome)
        assertEquals(40, r.minutes)
        assertEquals(j0.forDay, r.forDay)
        val sport = ActiveSession(SessionKind.SPORT, null, null, null, at(18, 0), 40)
        assertEquals(SessionKind.SPORT, Lifecycle.autoClose(sport, at(18, 55))!!.kind)
    }

    @Test
    fun laSessionClotureCompteDansLeTravailContinu() {
        val data = Importer.parse("""{"version": 1, "semester": 1, "weekA": "2026-11-09", "slots": []}""").data!!
        val record = Lifecycle.autoClose(active, at(21, 5))!!
        val st = StateBuilder.build(data, listOf(Entry(at(21, 5), Payload.Session(record))))
        assertEquals(45, continuousWork(st, at(21, 6)))
    }
}

package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LifecycleTest {

    private fun at(day: Int, h: Int, m: Int, s: Int = 0): LocalDateTime = LocalDateTime.of(2026, 11, day, h, m, s)
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 11, day)
    private fun prop(a: Action, created: LocalDateTime = at(9, 20, 0)) = Proposal("p1", a, created)

    // ---------- Démarrage ----------

    @Test
    fun lesPropositionsDemarrablesDonnentLaBonneSession() {
        val now = at(9, 20, 3)
        assertEquals(
            ActiveSession(SessionKind.DEEPWORK, Subject.MATHS, "Suites", null, now, 45),
            Lifecycle.start(prop(Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE)), now)
        )
        assertEquals(
            ActiveSession(SessionKind.DM, Subject.MATHS, null, null, now, 45),
            Lifecycle.start(prop(Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND)), now)
        )
        assertEquals(
            ActiveSession(SessionKind.J0, Subject.SI, null, d(8), now, 25),
            Lifecycle.start(prop(Action.J0Micro(J0Item(Subject.SI, d(8)), 25)), now)
        )
        assertEquals(ActiveSession(SessionKind.SPORT, null, null, null, now, 40), Lifecycle.start(prop(Action.Sport(40)), now))
        assertEquals(ActiveSession(SessionKind.CARDIO, null, null, null, now, 35), Lifecycle.start(prop(Action.Cardio(35)), now))
        assertEquals(ActiveSession(SessionKind.PAUSE, null, null, null, now, 15), Lifecycle.start(prop(Action.Pause(15)), now))
        assertEquals(ActiveSession(SessionKind.CLOTURE, null, null, null, now, 15), Lifecycle.start(prop(Action.Cloture(15)), now))
    }

    @Test
    fun leJ0MacroNeDemarrePasDeTimer() {
        val macro = Action.J0Macro(listOf(J0Item(Subject.MATHS, d(9))), 120)
        assertFalse(Lifecycle.startable(macro))
        assertNull(Lifecycle.start(prop(macro), at(9, 20, 1)))
        assertFalse(Lifecycle.startable(Action.None(Reason.REST)))
        assertTrue(Lifecycle.startable(Action.Pause(15)))
    }

    @Test
    fun uneProprositionPerimeeNeDemarrePas() {
        val p = prop(Action.Sport(40), at(9, 20, 0))
        assertNotNull(Lifecycle.start(p, at(9, 20, 14)))
        assertNull(Lifecycle.start(p, at(9, 20, 15))) // 15 min pile : expirée
        assertTrue(Lifecycle.isExpired(p, at(9, 20, 15)))
        assertFalse(Lifecycle.isExpired(p, at(9, 20, 14)))
    }

    // ---------- Fin de session ----------

    private val active = ActiveSession(SessionKind.DEEPWORK, Subject.MATHS, "Suites", null, at(9, 20, 5), 45)

    @Test
    fun finiAvantLaFinEnregistreLaDureeReelle() {
        // 27 min 20 s → 27 ; 27 min 40 s → 28
        assertEquals(27, Lifecycle.finish(active, at(9, 20, 32, 20), FinishMode.EARLY).minutes)
        assertEquals(28, Lifecycle.finish(active, at(9, 20, 32, 40), FinishMode.EARLY).minutes)
        // jamais moins d'une minute
        assertEquals(1, Lifecycle.finish(active, at(9, 20, 5, 10), FinishMode.EARLY).minutes)
    }

    @Test
    fun termineEnregistreLaDureePrevueEtDebordeLaDureeReelle() {
        assertEquals(45, Lifecycle.finish(active, at(9, 21, 20), FinishMode.TIMER_DONE).minutes)
        // débordé puis « fini » 12 min après la fin : 57 min réelles
        val overrun = active.copy(overrun = true)
        assertEquals(57, Lifecycle.finish(overrun, at(9, 21, 2), FinishMode.EARLY).minutes)
    }

    @Test
    fun leRecordGardeMatiereChapitreEtJour() {
        val r = Lifecycle.finish(active, at(9, 20, 50), FinishMode.TIMER_DONE)
        assertEquals(SessionRecord(SessionKind.DEEPWORK, Outcome.DONE, at(9, 20, 5), 45, Subject.MATHS, "Suites", null), r)
        val j0 = ActiveSession(SessionKind.J0, Subject.SI, null, d(8), at(9, 18, 0), 40)
        assertEquals(d(8), Lifecycle.finish(j0, at(9, 18, 40), FinishMode.TIMER_DONE).forDay)
    }

    // ---------- Skip et refus ----------

    @Test
    fun skipEstCompteFaitAvecDuree0() {
        val now = at(9, 20, 3)
        assertEquals(
            SessionRecord(SessionKind.J0, Outcome.SKIPPED, now, 0, Subject.SI, null, d(8)),
            Lifecycle.skip(Action.J0Micro(J0Item(Subject.SI, d(8)), 25), now)
        )
        assertEquals(
            SessionRecord(SessionKind.DEEPWORK, Outcome.SKIPPED, now, 0, Subject.MATHS, "Suites"),
            Lifecycle.skip(Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE), now)
        )
        assertEquals(
            SessionRecord(SessionKind.DM, Outcome.SKIPPED, now, 0, Subject.MATHS, null),
            Lifecycle.skip(Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND), now)
        )
        assertEquals(SessionRecord(SessionKind.PAUSE, Outcome.SKIPPED, now, 0), Lifecycle.skip(Action.Pause(15), now))
        assertEquals(SessionRecord(SessionKind.CLOTURE, Outcome.SKIPPED, now, 0), Lifecycle.skip(Action.Cloture(15), now))
        // sport et cardio : pas de skip, c'est Oui / Non
        assertNull(Lifecycle.skip(Action.Sport(40), now))
        assertNull(Lifecycle.skip(Action.Cardio(35), now))
        assertNull(Lifecycle.skip(Action.J0Macro(emptyList(), 60), now))
    }

    @Test
    fun nonAuSportEstUnRefusPasUnSkip() {
        val now = at(12, 18, 5)
        assertEquals(SessionRecord(SessionKind.SPORT, Outcome.DECLINED, now, 0), Lifecycle.decline(Action.Sport(40), now))
        assertEquals(SessionRecord(SessionKind.CARDIO, Outcome.DECLINED, now, 0), Lifecycle.decline(Action.Cardio(35), now))
        assertNull(Lifecycle.decline(Action.Pause(15), now))
    }

    // ---------- Skip ou non-validation : l'effet sur le moteur ----------

    private val data: StaticData = Importer.parse(
        """
        {"version": 1, "semester": 1, "weekA": "2026-11-09",
         "slots": [{"day": "MON", "start": "11:05", "end": "12:00", "subject": "CHIMIE", "type": "TD", "week": "A"}]}
        """.trimIndent()
    ).data!!

    private fun state(vararg records: SessionRecord): State =
        StateBuilder.build(data, records.map { Entry(it.start, Payload.Session(it)) })

    @Test
    fun skipSortLaMatiereDeLAttenteMaisLaNonValidationLaLaisseDue() {
        val now = at(9, 16, 10)
        // sport refusé : on est au J0
        val declined = SessionRecord(SessionKind.SPORT, Outcome.DECLINED, at(9, 16, 0), 0)
        val macro = nextAction(state(declined), now) as Action.J0Macro
        val micro = J0.micro(state(declined), now, macro.pending[0])!!

        // non-validation : rien n'est enregistré → la même matière est encore due
        assertEquals(1, (nextAction(state(declined), at(9, 16, 40)) as Action.J0Macro).pending.size)

        // skip : compté fait → plus rien en attente
        val skip = Lifecycle.skip(micro, at(9, 16, 12))!!
        assertTrue(nextAction(state(declined, skip), at(9, 16, 40)) !is Action.J0Macro)
    }

    @Test
    fun finiEstValideEtSortLaMatiereDeLAttente() {
        val declined = SessionRecord(SessionKind.SPORT, Outcome.DECLINED, at(9, 16, 0), 0)
        val s = ActiveSession(SessionKind.J0, Subject.CHIMIE, null, d(9), at(9, 16, 10), 40)
        val done = Lifecycle.finish(s, at(9, 16, 50), FinishMode.TIMER_DONE)
        assertEquals(Outcome.DONE, done.outcome)
        assertEquals(0, J0.pending(state(declined, done), at(9, 16, 50)).size)
    }

    // ---------- Persistance ----------

    @Test
    fun sessionEnCoursAllerRetour() {
        val a = ActiveSession(SessionKind.DEEPWORK, Subject.MATHS, "Suites \"é\"", null, at(9, 20, 5, 30), 45, true)
        assertEquals(a, Persist.decodeActive(Persist.encodeActive(a)))
        val b = ActiveSession(SessionKind.J0, Subject.SI, null, d(8), at(9, 20, 5), 25)
        assertEquals(b, Persist.decodeActive(Persist.encodeActive(b)))
        val c = ActiveSession(SessionKind.SPORT, null, null, null, at(9, 20, 5), 40)
        assertEquals(c, Persist.decodeActive(Persist.encodeActive(c)))
        assertNull(Persist.decodeActive("pas du json"))
    }

    @Test
    fun propositionAllerRetourPourChaqueAction() {
        val actions = listOf(
            Action.Cloture(15),
            Action.Pause(20),
            Action.Sport(40),
            Action.Cardio(35),
            Action.J0Macro(listOf(J0Item(Subject.MATHS, d(8)), J0Item(Subject.SI, d(9))), 120),
            Action.J0Micro(J0Item(Subject.SI, d(9)), 25),
            Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE),
            Action.Deepwork(Subject.PHYSIQUE, "Thermo", 20, 20, DeepKind.KHOLLE_FORCED),
            Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND)
        )
        for (a in actions) {
            val p = Proposal("id-" + a.hashCode(), a, at(9, 20, 0, 15))
            assertEquals(p, Persist.decodeProposal(Persist.encodeProposal(p)))
        }
        assertNull(Persist.decodeProposal("{}"))
        assertNull(Persist.decodeProposal("""{"id":"x","createdAt":"2026-11-09T20:00","type":"inconnu"}"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun uneAbsenceDePropositionNeSeStockePas() {
        Persist.encodeProposal(Proposal("x", Action.None(Reason.REST), at(9, 20, 0)))
    }

    // ---------- Textes ----------

    @Test
    fun textesDeNotification() {
        val today = d(9)
        assertEquals(
            "J0 Maths — 40 min estimées. Prêt ?",
            Texts.title(Action.J0Micro(J0Item(Subject.MATHS, today), 40), today)
        )
        assertEquals(
            "J0 SI (hier) — 25 min estimées. Prêt ?",
            Texts.title(Action.J0Micro(J0Item(Subject.SI, d(8)), 25), today)
        )
        assertEquals("J0 — choisis une matière", Texts.title(Action.J0Macro(emptyList(), 60), today))
        assertEquals(
            "Deepwork 90 min — micro-session : Maths 45 min. Prêt ?",
            Texts.title(Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE), today)
        )
        assertEquals(
            "Prochaine session : Deepwork Physique — 20 min estimées. Prêt ?",
            Texts.title(Action.Deepwork(Subject.PHYSIQUE, "Thermo", 20, 20, DeepKind.SCORE), today)
        )
        assertEquals(
            "DM 90 min — micro-session : Maths 45 min. Prêt ?",
            Texts.title(Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND), today)
        )
        assertEquals("Sport — 40 min musculation. Oui / Non ?", Texts.title(Action.Sport(40), today))
        assertEquals("Cardio zone 2 — 35 min. Oui / Non ?", Texts.title(Action.Cardio(35), today))
        assertEquals("Pause — 15 min. Prêt ?", Texts.title(Action.Pause(15), today))
        assertEquals("Clôture — 15 min. Prêt ?", Texts.title(Action.Cloture(15), today))
    }

    @Test
    fun deuxiemeLigneEtLibelleDeSession() {
        val today = d(9)
        assertEquals("Chapitre : Suites", Texts.body(Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE), today))
        assertEquals("", Texts.body(Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND), today))
        assertEquals(
            "Maths (hier), Chimie",
            Texts.body(Action.J0Macro(listOf(J0Item(Subject.MATHS, d(8)), J0Item(Subject.CHIMIE, today)), 60), today)
        )
        assertEquals("Deepwork Maths", Texts.activeLabel(active))
        assertEquals("Sport", Texts.activeLabel(ActiveSession(SessionKind.SPORT, null, null, null, at(9, 18, 0), 40)))
    }

    // ---------- Prochain déclencheur ----------

    @Test
    fun prochainDeclencheur() {
        val st = StateBuilder.build(data, emptyList())
        // lundi 16h : prochaine fin de bloc = fin du dîner à 20h
        assertEquals(at(9, 20, 0), Triggers.next(st, at(9, 16, 0)))
        // lundi 22h20 : le lendemain, première fin de bloc = fin du déjeuner à 14h
        assertEquals(at(10, 14, 0), Triggers.next(st, at(9, 22, 20)))
        // samedi soir → dimanche : 10h pour le cardio
        assertEquals(at(15, 10, 0), Triggers.next(st, at(14, 22, 20)))
    }
}

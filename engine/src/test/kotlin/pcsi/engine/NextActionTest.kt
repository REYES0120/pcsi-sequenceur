package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Semaine de référence : lundi 9 nov. 2026 (semaine A) → mardi 10, mercredi 11, jeudi 12,
 * vendredi 13, samedi 14, dimanche 15.
 */
class NextActionTest {

    private fun at(day: Int, h: Int, m: Int): LocalDateTime = LocalDateTime.of(2026, 11, day, h, m)
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 11, day)

    private fun slot(
        day: DayOfWeek,
        start: String,
        end: String,
        subject: Subject?,
        type: SlotType = SlotType.COURS,
        week: WeekKind = WeekKind.BOTH
    ) = Slot(day, LocalTime.parse(start), LocalTime.parse(end), subject, type, week, 1)

    private val slots = listOf(
        slot(DayOfWeek.MONDAY, "11:05", "12:00", Subject.CHIMIE, SlotType.TD, WeekKind.A),
        slot(DayOfWeek.MONDAY, "14:00", "15:50", Subject.SI, SlotType.COURS, WeekKind.A),
        slot(DayOfWeek.TUESDAY, "08:00", "09:50", Subject.MATHS),
        slot(DayOfWeek.TUESDAY, "10:05", "12:00", Subject.CHIMIE),
        slot(DayOfWeek.TUESDAY, "16:05", "17:05", Subject.PHYSIQUE),
        slot(DayOfWeek.TUESDAY, "17:05", "18:00", null, SlotType.EPS),
        slot(DayOfWeek.WEDNESDAY, "14:00", "15:50", Subject.PHILO),
        slot(DayOfWeek.THURSDAY, "15:00", "18:00", Subject.MATHS)
    )

    private val base = emptyState().copy(slots = slots)

    private fun j0(sub: Subject, forDay: LocalDate, start: LocalDateTime, outcome: Outcome = Outcome.DONE) =
        SessionRecord(
            SessionKind.J0, outcome, start, if (outcome == Outcome.DONE) 30 else 0, sub, null, forDay
        )

    private fun dw(sub: Subject, chapter: String?, start: LocalDateTime, minutes: Int) =
        SessionRecord(SessionKind.DEEPWORK, Outcome.DONE, start, minutes, sub, chapter)

    private fun sess(kind: SessionKind, outcome: Outcome, start: LocalDateTime, minutes: Int) =
        SessionRecord(kind, outcome, start, minutes)

    /** J0 du lundi déjà traités (avant le dîner : ils ne comptent pas dans le travail continu du soir). */
    private val mondayJ0Done = listOf(
        j0(Subject.CHIMIE, d(9), at(9, 16, 10)),
        j0(Subject.SI, d(9), at(9, 16, 45))
    )

    private val twoChapters = listOf(
        Chapter(Subject.MATHS, "Suites", d(4)),
        Chapter(Subject.PHYSIQUE, "Thermo", d(4))
    )

    // ---------- Contraintes fixes ----------

    @Test
    fun nuitAvantLeReveil() {
        assertEquals(Action.None(Reason.NIGHT, at(9, 7, 0)), nextAction(base, at(9, 6, 30)))
    }

    @Test
    fun repasBloques() {
        assertEquals(Action.None(Reason.MEAL, at(10, 14, 0)), nextAction(base, at(10, 12, 30)))
        assertEquals(Action.None(Reason.MEAL, at(10, 20, 0)), nextAction(base, at(10, 19, 30)))
    }

    @Test
    fun coursEnCours() {
        assertEquals(Action.None(Reason.CLASS, at(10, 9, 50)), nextAction(base, at(10, 9, 0)))
    }

    @Test
    fun colleDansMoinsDe45MinutesOnAttend() {
        val st = base.copy(colles = listOf(Colle(d(11), LocalTime.of(17, 0), LocalTime.of(18, 0), Subject.PHYSIQUE)))
        assertEquals(Action.None(Reason.EXTERNAL, at(11, 18, 0)), nextAction(st, at(11, 16, 20)))
        // 50 min avant la colle : on propose encore (J0 en attente, 45 min dispo)
        val r = nextAction(st, at(11, 16, 10))
        assertTrue(r is Action.J0Macro)
        assertEquals(45, (r as Action.J0Macro).minutesAvailable)
    }

    @Test
    fun clotureA2215() {
        assertEquals(Action.Cloture(15), nextAction(base, at(9, 22, 15)))
        assertEquals(Action.Cloture(15), nextAction(base, at(9, 22, 20)))
        // 22h14 : trop court pour quoi que ce soit
        assertEquals(Action.None(Reason.TOO_SHORT), nextAction(base, at(9, 22, 14)))
        val done = base.copy(sessions = listOf(sess(SessionKind.CLOTURE, Outcome.DONE, at(9, 22, 15), 15)))
        assertEquals(Action.None(Reason.NIGHT), nextAction(done, at(9, 22, 40)))
    }

    @Test
    fun semaineAEtB() {
        assertEquals(WeekKind.A, Constraints.weekKind(base, d(9)))
        assertEquals(WeekKind.B, Constraints.weekKind(base, d(16)))
        assertEquals(WeekKind.A, Constraints.weekKind(base, d(23)))
        assertEquals(WeekKind.B, Constraints.weekKind(base, d(8)))
        assertEquals(2, Constraints.classesOn(base, d(9)).size)
        assertEquals(0, Constraints.classesOn(base, d(16)).size) // lundi de semaine B
    }

    @Test
    fun joursDeCongeSansCours() {
        val st = base.copy(daysOff = listOf(DateSpan(d(11), d(11))))
        assertEquals(0, Constraints.classesOn(st, d(11)).size)
        assertEquals(1, Constraints.classesOn(base, d(11)).size)
    }

    @Test
    fun declencheurs() {
        val t = Constraints.triggersOn(base, d(10))
        assertEquals(
            listOf(at(10, 9, 50), at(10, 12, 0), at(10, 14, 0), at(10, 17, 5), at(10, 18, 0), at(10, 20, 0), at(10, 22, 15)),
            t
        )
        assertTrue(Constraints.triggersOn(base, d(15)).contains(at(15, 10, 0)))
    }

    // ---------- Pause ----------

    private fun eveningSessions(secondMinutes: Int): List<SessionRecord> = mondayJ0Done + listOf(
        dw(Subject.MATHS, "Suites", at(9, 20, 5), 45),
        dw(Subject.MATHS, "Suites", at(9, 20, 50), secondMinutes)
    )

    @Test
    fun pauseDe15MinA90Pile() {
        val st = base.copy(sessions = eveningSessions(45))
        assertEquals(90, continuousWork(st, at(9, 21, 36)))
        assertEquals(Action.Pause(15), nextAction(st, at(9, 21, 36)))
    }

    @Test
    fun pauseDe20MinAuDessusDe90() {
        val st = base.copy(sessions = eveningSessions(55))
        assertEquals(Action.Pause(20), nextAction(st, at(9, 21, 46)))
    }

    @Test
    fun pasDePauseSousSoixanteMinutes() {
        val st = base.copy(sessions = eveningSessions(14))
        assertEquals(59, continuousWork(st, at(9, 21, 10)))
        assertFalse(nextAction(st, at(9, 21, 10)) is Action.Pause)
    }

    @Test
    fun pauseSkippeeRemetLeCompteurAZero() {
        val st = base.copy(
            sessions = eveningSessions(45) + sess(SessionKind.PAUSE, Outcome.SKIPPED, at(9, 21, 36), 0)
        )
        assertEquals(0, continuousWork(st, at(9, 21, 37)))
        assertFalse(nextAction(st, at(9, 21, 37)) is Action.Pause)
    }

    @Test
    fun leRepasRemetLeCompteurAZero() {
        val st = base.copy(
            sessions = listOf(dw(Subject.MATHS, "Suites", at(9, 18, 0), 90)) // avant le dîner
        )
        assertEquals(0, continuousWork(st, at(9, 20, 5)))
    }

    @Test
    fun leSportRemetLeCompteurAZero() {
        val st = base.copy(
            sessions = listOf(
                dw(Subject.MATHS, "Suites", at(12, 15, 0), 80),
                sess(SessionKind.SPORT, Outcome.DONE, at(12, 17, 0), 40)
            )
        )
        // Jeudi 17h40 : 80 min de travail avant le sport, remis à zéro
        assertEquals(0, continuousWork(st, at(12, 17, 45)))
    }

    // ---------- Sport et cardio ----------

    @Test
    fun sportApresLeDernierCours() {
        val st = base.copy(sessions = listOf(j0(Subject.PHILO, d(11), at(11, 20, 0))))
        assertEquals(Action.Sport(40), nextAction(st, at(12, 18, 5)))
    }

    @Test
    fun pasDeSportSiLeDernierCoursEstEPS() {
        assertFalse(nextAction(base, at(10, 18, 5)) is Action.Sport)
    }

    @Test
    fun pasDeSportSiJ0DeLaVeilleEnAttente() {
        val r = nextAction(base, at(12, 18, 5))
        assertTrue(r is Action.J0Macro)
    }

    @Test
    fun pasDeSportAuDessusDeTroisSeances() {
        val st = base.copy(
            sessions = listOf(
                j0(Subject.PHILO, d(11), at(11, 20, 0)),
                sess(SessionKind.SPORT, Outcome.DONE, at(9, 18, 0), 40),
                sess(SessionKind.SPORT, Outcome.DONE, at(10, 18, 0), 40),
                sess(SessionKind.SPORT, Outcome.DONE, at(11, 18, 0), 40)
            )
        )
        assertFalse(nextAction(st, at(12, 18, 5)) is Action.Sport)
    }

    @Test
    fun nonAuSportPasReproposeAujourdhui() {
        val st = base.copy(
            sessions = listOf(
                j0(Subject.PHILO, d(11), at(11, 20, 0)),
                sess(SessionKind.SPORT, Outcome.DECLINED, at(12, 18, 1), 0)
            )
        )
        assertFalse(nextAction(st, at(12, 18, 5)) is Action.Sport)
    }

    @Test
    fun pasDeSportUneFoisLeTravailDuSoirCommence() {
        val st = base.copy(
            sessions = listOf(
                j0(Subject.PHILO, d(11), at(11, 20, 0)),
                j0(Subject.MATHS, d(12), at(12, 18, 1))
            )
        )
        assertFalse(nextAction(st, at(12, 18, 40)) is Action.Sport)
    }

    @Test
    fun cardioLeDimancheA10h() {
        assertEquals(Action.Cardio(35), nextAction(base, at(15, 10, 0)))
    }

    @Test
    fun cardioPasAvant10hSaufSurDemande() {
        assertEquals(Action.None(Reason.REST), nextAction(base, at(15, 9, 0)))
        assertEquals(Action.Cardio(35), nextAction(base, at(15, 9, 0), onDemand = true))
    }

    @Test
    fun cardioRefuseOuDejaFaitCetteSemaine() {
        val declined = base.copy(sessions = listOf(sess(SessionKind.CARDIO, Outcome.DECLINED, at(15, 10, 1), 0)))
        assertEquals(Action.None(Reason.REST), nextAction(declined, at(15, 10, 30)))
        val done = base.copy(sessions = listOf(sess(SessionKind.CARDIO, Outcome.DONE, at(14, 16, 0), 35)))
        assertEquals(Action.None(Reason.REST), nextAction(done, at(15, 10, 30)))
    }

    // ---------- J0 ----------

    private val sportRefuseLundi = sess(SessionKind.SPORT, Outcome.DECLINED, at(9, 15, 55), 0)

    @Test
    fun j0MacroListeLesMatieresDuJour() {
        val st = base.copy(sessions = listOf(sportRefuseLundi))
        val r = nextAction(st, at(9, 16, 0))
        assertEquals(
            Action.J0Macro(listOf(J0Item(Subject.CHIMIE, d(9)), J0Item(Subject.SI, d(9))), 175),
            r
        )
    }

    @Test
    fun j0MicroTimerEstLeMinDe40EtDuTempsParMatiere() {
        val st = base.copy(sessions = listOf(sportRefuseLundi))
        assertEquals(
            Action.J0Micro(J0Item(Subject.CHIMIE, d(9)), 40),
            J0.micro(st, at(9, 16, 0), J0Item(Subject.CHIMIE, d(9)))
        )
        // 18h20 : 35 min dispo, 2 matières → 17 min
        assertEquals(
            Action.J0Micro(J0Item(Subject.CHIMIE, d(9)), 17),
            J0.micro(st, at(9, 18, 20), J0Item(Subject.CHIMIE, d(9)))
        )
        // une matière qui n'est pas en attente : rien
        assertNull(J0.micro(st, at(9, 16, 0), J0Item(Subject.MATHS, d(9))))
    }

    @Test
    fun j0LeSkipEtLeFaitSortentLaMatiereDeLAttente() {
        val skipped = base.copy(
            sessions = listOf(sportRefuseLundi, j0(Subject.CHIMIE, d(9), at(9, 16, 0), Outcome.SKIPPED))
        )
        assertEquals(listOf(J0Item(Subject.SI, d(9))), J0.pending(skipped, at(9, 16, 5)))
        val allDone = base.copy(sessions = listOf(sportRefuseLundi) + mondayJ0Done)
        assertEquals(0, J0.pending(allDone, at(9, 17, 30)).size)
    }

    @Test
    fun j0MicroApresSkipRedonneLeTempsAuxAutres() {
        val st = base.copy(
            sessions = listOf(sportRefuseLundi, j0(Subject.CHIMIE, d(9), at(9, 18, 15), Outcome.SKIPPED))
        )
        // 18h20 : 35 min dispo, une seule matière restante → 35 → plafonné à 35 (min(40, 35))
        assertEquals(
            Action.J0Micro(J0Item(Subject.SI, d(9)), 35),
            J0.micro(st, at(9, 18, 20), J0Item(Subject.SI, d(9)))
        )
    }

    @Test
    fun j0DeLaVeilleResteJusquAuLendemainSeulement() {
        // mercredi 17h : mardi (hier) en attente, lundi (avant-hier) abandonné
        val items = J0.pending(base, at(11, 17, 0))
        assertTrue(items.none { it.forDay == d(9) })
        assertEquals(
            listOf(
                J0Item(Subject.MATHS, d(10)),
                J0Item(Subject.CHIMIE, d(10)),
                J0Item(Subject.PHYSIQUE, d(10)),
                J0Item(Subject.PHILO, d(11))
            ),
            items
        )
    }

    @Test
    fun j0SeulementPourLesCoursTermines() {
        // mardi 11h : Maths fini, Chimie (10h05-12h) pas encore ; Chimie et SI de lundi encore dus
        val items = J0.pending(base, at(10, 11, 0))
        assertEquals(
            listOf(
                J0Item(Subject.CHIMIE, d(9)),
                J0Item(Subject.SI, d(9)),
                J0Item(Subject.MATHS, d(10))
            ),
            items
        )
    }

    @Test
    fun j0TropCourtDonneUnDeepworkCourt() {
        // 21h30 mardi avec J0 de lundi en attente : 40 min dispo ≥ 30 → J0 ; à 21h50 → 20 min → deepwork court
        val st = base.copy(chapters = twoChapters)
        val r1 = nextAction(st, at(10, 21, 30))
        assertTrue(r1 is Action.J0Macro)
        val r2 = nextAction(st, at(10, 21, 50))
        // 25 min libres → 20 min dispo : < 30 donc pas de J0 ; ≥ 20 donc deepwork
        assertTrue(r2 is Action.Deepwork)
        assertEquals(20, (r2 as Action.Deepwork).microMinutes)
    }

    // ---------- Deepwork ----------

    private val dwState = base.copy(chapters = twoChapters, sessions = mondayJ0Done)

    @Test
    fun deepworkPremiereMicroSessionDe45SurBloc90() {
        assertEquals(
            Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE),
            nextAction(dwState, at(9, 20, 5))
        )
    }

    @Test
    fun deepworkDeuxiemeMicroSessionChangeDeMatiere() {
        val st = dwState.copy(sessions = dwState.sessions + dw(Subject.MATHS, "Suites", at(9, 20, 5), 45))
        // 20h50 : 45 min cumulées (pas de pause) ; la récence pousse Physique devant Maths
        val r = nextAction(st, at(9, 20, 50))
        assertEquals(Action.Deepwork(Subject.PHYSIQUE, "Thermo", 45, 80, DeepKind.SCORE), r)
    }

    @Test
    fun deepworkMinimumVingtMinutes() {
        val ok = nextAction(dwState, at(9, 21, 50))
        assertEquals(Action.Deepwork(Subject.MATHS, "Suites", 20, 20, DeepKind.SCORE), ok)
        assertEquals(Action.None(Reason.TOO_SHORT), nextAction(dwState, at(9, 21, 51)))
    }

    @Test
    fun repasLibreQuandAucunChapitre() {
        assertEquals(Action.None(Reason.REST), nextAction(base.copy(sessions = mondayJ0Done), at(9, 20, 5)))
    }

    // ---------- Colle forcée ----------

    private val collePhysique = Colle(d(12), LocalTime.of(13, 0), LocalTime.of(14, 0), Subject.PHYSIQUE)
    private val dsMaths = Ds(d(14), LocalTime.of(8, 0), LocalTime.of(12, 0), listOf(Subject.MATHS))
    private val colleState = base.copy(
        chapters = listOf(Chapter(Subject.MATHS, "Suites", d(4)), Chapter(Subject.PHYSIQUE, "Thermo", d(9))),
        colles = listOf(collePhysique),
        ds = listOf(dsMaths),
        sessions = mondayJ0Done
    )

    @Test
    fun colleForceeEcraseLeScore() {
        // Maths a 13,5 de score (DS dans 5 j), mais la colle de Physique impose Physique
        assertEquals(
            Action.Deepwork(Subject.PHYSIQUE, "Thermo", 45, 90, DeepKind.KHOLLE_FORCED),
            nextAction(colleState, at(9, 20, 5))
        )
    }

    @Test
    fun colleForceeFinitApresDeuxHeuresTrente() {
        val st = colleState.copy(
            sessions = colleState.sessions + dw(Subject.PHYSIQUE, "Thermo", LocalDateTime.of(2026, 11, 8, 10, 0), 150)
        )
        assertEquals(
            Action.Deepwork(Subject.MATHS, "Suites", 45, 90, DeepKind.SCORE),
            nextAction(st, at(9, 20, 5))
        )
    }

    @Test
    fun colleLaPlusProcheEstForceeEnPremier() {
        val st = colleState.copy(
            chapters = colleState.chapters + Chapter(Subject.CHIMIE, "Atome", d(9)),
            colles = listOf(
                collePhysique,
                Colle(d(11), LocalTime.of(13, 0), LocalTime.of(14, 0), Subject.CHIMIE)
            )
        )
        val forced = DeepWork.forcedByColle(st, at(9, 20, 5))
        assertEquals(listOf(Subject.CHIMIE, Subject.PHYSIQUE), forced.map { it.first })
    }

    // ---------- DM du week-end ----------

    private val dm18 = Dm("dm1", Subject.MATHS, d(18))

    /** Le cardio reste proposé tout le dimanche tant qu'il n'est ni fait ni refusé. */
    private val cardioRefuse = sess(SessionKind.CARDIO, Outcome.DECLINED, at(15, 10, 1), 0)

    @Test
    fun dmForceLeDimancheAvantLeScore() {
        val st = base.copy(dms = listOf(dm18))
        assertEquals(
            Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND),
            nextAction(st.copy(sessions = listOf(cardioRefuse)), at(15, 14, 30))
        )
        // le samedi aussi
        assertEquals(
            Action.Deepwork(Subject.MATHS, null, 45, 90, DeepKind.DM_WEEKEND),
            nextAction(st, at(14, 14, 30))
        )
    }

    @Test
    fun dmFenetreDeLaSemaineQuiSuit() {
        val sunday = at(15, 14, 30)
        fun forced(due: Int) = DeepWork.forcedDm(base.copy(dms = listOf(Dm("x", Subject.MATHS, d(due)))), sunday)
        assertNull(forced(13)) // dû cette semaine : hors règle
        assertEquals("x", forced(16)?.id) // lundi prochain
        assertEquals("x", forced(22)?.id) // dimanche suivant
        assertNull(forced(23)) // semaine d'après
    }

    @Test
    fun dmPasEnSemaineNiSiTermine() {
        val st = base.copy(dms = listOf(dm18))
        assertNull(DeepWork.forcedDm(st, at(13, 14, 30))) // vendredi
        assertNull(DeepWork.forcedDm(st.copy(dmDone = setOf("dm1")), at(15, 14, 30)))
    }

    @Test
    fun dmPasseAvantLaColleForcee() {
        val st = base.copy(
            dms = listOf(dm18),
            chapters = listOf(Chapter(Subject.PHYSIQUE, "Thermo", d(10))),
            colles = listOf(Colle(d(17), LocalTime.of(13, 0), LocalTime.of(14, 0), Subject.PHYSIQUE)),
            sessions = listOf(cardioRefuse)
        )
        val r = nextAction(st, at(15, 14, 30)) as Action.Deepwork
        assertEquals(DeepKind.DM_WEEKEND, r.kind)
        assertEquals(Subject.MATHS, r.subject)
    }

    @Test
    fun leDmDeuxiemeMicroSessionResteDMTantQueNonTermine() {
        val st = base.copy(
            dms = listOf(dm18),
            sessions = listOf(
                cardioRefuse,
                sess(SessionKind.DM, Outcome.DONE, at(15, 14, 10), 45).copy(subject = Subject.MATHS)
            )
        )
        // 14h55 : 45 min cumulées, pas de pause ; le DM n'est pas marqué terminé → encore forcé
        val r = nextAction(st, at(15, 14, 55)) as Action.Deepwork
        assertEquals(DeepKind.DM_WEEKEND, r.kind)
    }
}

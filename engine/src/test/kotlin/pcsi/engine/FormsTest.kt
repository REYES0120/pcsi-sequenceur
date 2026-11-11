package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FormsTest {

    // Dimanche 15 nov. 2026, 14h30
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 15, 14, 30)
    private val today: LocalDate = now.toLocalDate()

    private val data: StaticData = Importer.parse(
        """{"version": 1, "semester": 1, "weekA": "2026-11-09", "slots": []}"""
    ).data!!

    private fun state(vararg entries: Entry): State = StateBuilder.build(data, entries.toList())

    private fun ok(r: FormResult): Entry {
        assertTrue(r.toString(), r is FormResult.Ok)
        return (r as FormResult.Ok).entry
    }

    private fun err(r: FormResult): String {
        assertTrue(r.toString(), r is FormResult.Err)
        return (r as FormResult.Err).message
    }

    // ---------- Chapitre vu ----------

    @Test
    fun chapitreNettoyeEtEnregistre() {
        val e = ok(Forms.chapterSeen(null, Subject.MATHS, "  Suites   et  séries \n", today.minusDays(1), now))
        assertEquals(Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites et séries", today.minusDays(1))), e.payload)
        assertEquals(now, e.at)
    }

    @Test
    fun chapitreVideTropLongOuDansLeFutur() {
        assertTrue(err(Forms.chapterSeen(null, Subject.MATHS, "   ", today, now)).contains("vide"))
        assertTrue(err(Forms.chapterSeen(null, Subject.MATHS, "x".repeat(81), today, now)).contains("trop long"))
        ok(Forms.chapterSeen(null, Subject.MATHS, "x".repeat(80), today, now))
        assertTrue(err(Forms.chapterSeen(null, Subject.MATHS, "Suites", today.plusDays(1), now)).contains("futur"))
    }

    @Test
    fun chapitreDejaSaisiSansTenirCompteDesMajuscules() {
        val existing = Entry(now, Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites", today.minusDays(3))))
        val st = state(existing)
        assertTrue(err(Forms.chapterSeen(st, Subject.MATHS, "  suites ", today, now)).contains("déjà"))
        // la même chose dans une autre matière est un autre chapitre
        ok(Forms.chapterSeen(st, Subject.PHYSIQUE, "Suites", today, now))
    }

    @Test
    fun chapitrePasPourLesMatieresSansDeepwork() {
        assertTrue(err(Forms.chapterSeen(null, Subject.INFO, "Python", today, now)).contains("deepwork"))
    }

    @Test
    fun lEtatDedoublonneLesChapitresSansTenirCompteDesMajuscules() {
        val a = Entry(now, Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites", today.minusDays(5))))
        val b = Entry(now.plusMinutes(1), Payload.ChapterSeen(Chapter(Subject.MATHS, "SUITES", today.minusDays(1))))
        val st = state(a, b)
        assertEquals(1, st.chapters.size)
        assertEquals("Suites", st.chapters[0].name)
    }

    // ---------- Difficulté et priorités ----------

    @Test
    fun difficulteEtPriorites() {
        val d = ok(Forms.difficulty(Subject.PHYSIQUE, now))
        assertEquals(Payload.Difficulty(DifficultyDecl(Subject.PHYSIQUE, now)), d.payload)
        assertTrue(err(Forms.difficulty(Subject.INFO, now)).contains("deepwork"))

        val p = ok(Forms.priority(setOf(Subject.MATHS, Subject.SI), now))
        assertEquals(Payload.Priority(PriorityDecl(setOf(Subject.MATHS, Subject.SI), now)), p.payload)
        // aucune matière prioritaire
        val none = ok(Forms.priority(emptySet(), now))
        assertTrue((none.payload as Payload.Priority).decl.subjects.isEmpty())
        assertTrue(err(Forms.priority(setOf(Subject.MATHS, Subject.PHILO), now)).contains("deepwork"))
    }

    // ---------- Rang ----------

    @Test
    fun rangValide() {
        val e = ok(Forms.rank(Subject.MATHS, " 12 ", "41", today.minusDays(1), now))
        assertEquals(Payload.RankEntry(Rank(Subject.MATHS, today.minusDays(1), 12, 41)), e.payload)
        ok(Forms.rank(Subject.MATHS, "41", "41", today, now)) // dernier
        ok(Forms.rank(Subject.MATHS, "1", "1", today, now))
    }

    @Test
    fun rangInvalide() {
        assertTrue(err(Forms.rank(Subject.MATHS, "douze", "41", today, now)).startsWith("Rang"))
        assertTrue(err(Forms.rank(Subject.MATHS, "12", "", today, now)).startsWith("Effectif"))
        assertTrue(err(Forms.rank(Subject.MATHS, "0", "41", today, now)).contains("commence à 1"))
        assertTrue(err(Forms.rank(Subject.MATHS, "42", "41", today, now)).contains("dépasser"))
        assertTrue(err(Forms.rank(Subject.MATHS, "5", "301", today, now)).contains("Effectif"))
        assertTrue(err(Forms.rank(Subject.MATHS, "5", "41", today.plusDays(1), now)).contains("futur"))
        assertTrue(err(Forms.rank(Subject.INFO, "5", "41", today, now)).contains("deepwork"))
    }

    @Test
    fun dernierEffectifPourPreremplir() {
        assertNull(Forms.lastRankSize(state()))
        val st = state(
            Entry(now, Payload.RankEntry(Rank(Subject.MATHS, today.minusDays(40), 5, 44))),
            Entry(now, Payload.RankEntry(Rank(Subject.PHYSIQUE, today.minusDays(8), 9, 42)))
        )
        assertEquals(42, Forms.lastRankSize(st))
    }

    // ---------- DM ----------

    @Test
    fun dmAjoutEtDatePassee() {
        val e = ok(Forms.dmAdd(Subject.MATHS, today.plusDays(3), now, "dm1"))
        assertEquals(Payload.DmAdded(Dm("dm1", Subject.MATHS, today.plusDays(3))), e.payload)
        ok(Forms.dmAdd(Subject.MATHS, today, now, "dm2")) // rendu aujourd'hui : accepté
        assertTrue(err(Forms.dmAdd(Subject.MATHS, today.minusDays(1), now, "dm3")).contains("passée"))
        // l'identifiant par défaut est unique
        val a = (ok(Forms.dmAdd(Subject.MATHS, today, now)).payload as Payload.DmAdded).dm.id
        val b = (ok(Forms.dmAdd(Subject.MATHS, today, now)).payload as Payload.DmAdded).dm.id
        assertTrue(a != b)
    }

    @Test
    fun dmTermine() {
        val added = Entry(now, Payload.DmAdded(Dm("dm1", Subject.MATHS, today.plusDays(3))), "e1")
        val st = state(added)
        assertEquals(listOf("dm1"), Forms.openDms(st).map { it.id })
        val e = ok(Forms.dmDone(st, "dm1", now))
        assertEquals(Payload.DmDone("dm1"), e.payload)
        assertTrue(err(Forms.dmDone(st, "nope", now)).contains("inconnu"))
        val st2 = state(added, e)
        assertTrue(Forms.openDms(st2).isEmpty())
        assertTrue(err(Forms.dmDone(st2, "dm1", now)).contains("déjà"))
    }

    @Test
    fun lesDmOuvertsSontTriesParDateDeRendu() {
        val st = state(
            Entry(now, Payload.DmAdded(Dm("b", Subject.PHYSIQUE, today.plusDays(9)))),
            Entry(now, Payload.DmAdded(Dm("a", Subject.MATHS, today.plusDays(2))))
        )
        assertEquals(listOf("a", "b"), Forms.openDms(st).map { it.id })
    }

    // ---------- Annulation ----------

    @Test
    fun annulerUneSaisie() {
        val a = Entry(now, Payload.Difficulty(DifficultyDecl(Subject.MATHS, now)), "a")
        val entries = listOf(a)
        val r = ok(Forms.retract(entries, "a", now.plusMinutes(1)))
        assertEquals(Payload.Retract("a"), r.payload)
        assertTrue(err(Forms.retract(entries, "inconnu", now)).contains("n'existe plus"))
        // déjà annulée
        assertTrue(err(Forms.retract(entries + r, "a", now)).contains("n'existe plus"))
        // on n'annule pas une annulation
        assertTrue(err(Forms.retract(entries + r, r.id, now)).contains("n'existe plus"))
    }

    @Test
    fun lesSaisiesRecentesSansAnnulationsEtLesPlusRecentesDAbord() {
        val e1 = Entry(now.minusHours(3), Payload.Difficulty(DifficultyDecl(Subject.MATHS, now)), "e1")
        val e2 = Entry(now.minusHours(2), Payload.Difficulty(DifficultyDecl(Subject.SI, now)), "e2")
        val e3 = Entry(now.minusHours(1), Payload.Difficulty(DifficultyDecl(Subject.CHIMIE, now)), "e3")
        val cancel = Entry(now, Payload.Retract("e2"), "x")
        val list = Describe.recent(listOf(e1, e2, e3, cancel), 10)
        assertEquals(listOf("e3", "e1"), list.map { it.id })
        assertEquals(1, Describe.recent(listOf(e1, e2, e3), 1).size)
    }

    // ---------- Descriptions ----------

    @Test
    fun descriptionsLisibles() {
        val t = LocalDateTime.of(2026, 11, 12, 20, 5)
        fun line(p: Payload, all: List<Entry> = emptyList()) = Describe.entry(Entry(t, p), all)

        assertEquals(
            "12/11 20:05 · Deepwork Maths — 45 min",
            line(Payload.Session(SessionRecord(SessionKind.DEEPWORK, Outcome.DONE, t, 45, Subject.MATHS, "Suites")))
        )
        assertEquals(
            "12/11 20:05 · J0 SI — skip (cours du 11/11)",
            line(Payload.Session(SessionRecord(SessionKind.J0, Outcome.SKIPPED, t, 0, Subject.SI, null, LocalDate.of(2026, 11, 11))))
        )
        assertEquals("12/11 20:05 · Sport — refusé", line(Payload.Session(SessionRecord(SessionKind.SPORT, Outcome.DECLINED, t, 0))))
        assertEquals(
            "Chapitre vu : Maths — Suites (le 04/11)",
            line(Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites", LocalDate.of(2026, 11, 4))))
        )
        assertEquals("Difficulté : Physique (12/11 20:05)", line(Payload.Difficulty(DifficultyDecl(Subject.PHYSIQUE, t))))
        assertEquals("Priorités : aucune", line(Payload.Priority(PriorityDecl(emptySet(), t))))
        assertEquals("Priorités : Maths, SI", line(Payload.Priority(PriorityDecl(linkedSetOf(Subject.MATHS, Subject.SI), t))))
        assertEquals(
            "Rang Maths : 12/41 (DS du 07/11)",
            line(Payload.RankEntry(Rank(Subject.MATHS, LocalDate.of(2026, 11, 7), 12, 41)))
        )
        val added = Entry(t, Payload.DmAdded(Dm("dm1", Subject.MATHS, LocalDate.of(2026, 11, 18))), "m1")
        assertEquals("DM Maths — rendu le 18/11", Describe.entry(added, listOf(added)))
        assertEquals("DM terminé : Maths (rendu le 18/11)", line(Payload.DmDone("dm1"), listOf(added)))
        assertEquals("DM terminé : dm9", line(Payload.DmDone("dm9")))
    }

    // ---------- Effet sur le moteur ----------

    @Test
    fun saisiesAlimententLeScore() {
        val chapter = ok(Forms.chapterSeen(null, Subject.MATHS, " Suites ", today.minusDays(3), now))
        val diff = ok(Forms.difficulty(Subject.MATHS, now.minusDays(1)))
        val rank = ok(Forms.rank(Subject.MATHS, "35", "40", today.minusDays(8), now))
        val st = state(chapter, diff, rank)
        val c = Score.candidates(st, now)
        assertEquals(1, c.size)
        // base 3 + urgence 6 (vu il y a 3 j, jamais révisé) + difficulté 1,5 + rang faible (35/40 > 75 %) 1,5
        assertEquals(12.0, c[0].score.total, 1e-9)
    }

    @Test
    fun priorityEtDmPilotentLeMoteur() {
        val dm = ok(Forms.dmAdd(Subject.MATHS, today.plusDays(2), now, "dm1"))
        val st1 = state(dm)
        // dimanche : le DM est forcé
        val withDm = DeepWork.forcedDm(st1, now)
        assertNotNull(withDm)
        // une fois marqué terminé, il ne l'est plus
        val st2 = state(dm, ok(Forms.dmDone(st1, "dm1", now)))
        assertNull(DeepWork.forcedDm(st2, now))
        // « aucune matière prioritaire » efface la priorité précédente
        val prio = ok(Forms.priority(setOf(Subject.MATHS), now.minusHours(2)))
        val none = ok(Forms.priority(emptySet(), now.minusHours(1)))
        assertEquals(1.5, Score.bonusPriorite(state(prio), Subject.MATHS, now), 1e-9)
        assertEquals(0.0, Score.bonusPriorite(state(prio, none), Subject.MATHS, now), 1e-9)
        assertFalse(Forms.openDms(st2).any { it.id == "dm1" })
    }

    @Test
    fun annulerUnRangRetireSonEffet() {
        val rank = ok(Forms.rank(Subject.MATHS, "35", "40", today.minusDays(8), now))
        assertEquals(1.5, Score.bonusRang(state(rank), Subject.MATHS), 1e-9)
        val cancel = ok(Forms.retract(listOf(rank), rank.id, now))
        assertEquals(0.0, Score.bonusRang(state(rank, cancel), Subject.MATHS), 1e-9)
    }
}

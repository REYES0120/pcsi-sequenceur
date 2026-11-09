package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StateBuilderTest {

    private val json = """
        {
          "version": 1, "semester": 1, "weekA": "2026-11-09",
          "daysOff": [{"from": "2026-12-19", "to": "2027-01-03", "label": "Noël"}],
          "slots": [
            {"day": "MON", "start": "11:05", "end": "12:00", "subject": "CHIMIE", "type": "TD", "week": "A"},
            {"day": "WED", "start": "14:00", "end": "15:50", "subject": "PHILO"}
          ],
          "colles": [{"date": "2026-11-09", "start": "13:00", "end": "14:00", "subject": "CHIMIE"}],
          "ds": [{"date": "2026-11-14", "start": "08:00", "end": "12:00", "subjects": ["MATHS"]}]
        }
    """.trimIndent()

    private val data: StaticData = Importer.parse(json).data!!

    private fun at(day: Int, h: Int, m: Int): LocalDateTime = LocalDateTime.of(2026, 11, day, h, m)

    private fun session(id: String, start: LocalDateTime, minutes: Int = 30) =
        Entry(start, Payload.Session(SessionRecord(SessionKind.DEEPWORK, Outcome.DONE, start, minutes, Subject.MATHS, "Suites")), id)

    @Test
    fun leStatiqueEstRepris() {
        val s = StateBuilder.build(data, emptyList())
        assertEquals(1, s.semester)
        assertEquals(LocalDate.of(2026, 11, 9), s.weekAReference)
        assertEquals(2, s.slots.size)
        assertEquals(1, s.colles.size)
        assertEquals(1, s.ds.size)
        assertEquals(1, s.daysOff.size)
        assertTrue(s.sessions.isEmpty())
    }

    @Test
    fun lesSessionsSontTrieesParDebut() {
        val s = StateBuilder.build(
            data,
            listOf(session("b", at(9, 21, 0)), session("a", at(9, 20, 0)), session("c", at(9, 22, 0)))
        )
        assertEquals(listOf(at(9, 20, 0), at(9, 21, 0), at(9, 22, 0)), s.sessions.map { it.start })
    }

    @Test
    fun unRetractAnnuleLEntreeVisee() {
        val s = StateBuilder.build(
            data,
            listOf(
                session("a", at(9, 20, 0)),
                session("b", at(9, 21, 0)),
                Entry(at(9, 21, 5), Payload.Retract("a"), "r1")
            )
        )
        assertEquals(listOf(at(9, 21, 0)), s.sessions.map { it.start })
    }

    @Test
    fun unRetractSurUnIdInconnuNeFaitRien() {
        val s = StateBuilder.build(data, listOf(session("a", at(9, 20, 0)), Entry(at(9, 21, 0), Payload.Retract("nope"), "r1")))
        assertEquals(1, s.sessions.size)
    }

    @Test
    fun unRetractNePeutPasEtreAnnule() {
        val s = StateBuilder.build(
            data,
            listOf(
                session("a", at(9, 20, 0)),
                Entry(at(9, 21, 0), Payload.Retract("a"), "r1"),
                Entry(at(9, 21, 5), Payload.Retract("r1"), "r2")
            )
        )
        // r1 reste effectif : la session « a » reste annulée
        assertEquals(0, s.sessions.size)
    }

    @Test
    fun chapitresSansDoublon() {
        val e1 = Entry(at(4, 18, 0), Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites", LocalDate.of(2026, 11, 4))), "c1")
        val e2 = Entry(at(5, 18, 0), Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites", LocalDate.of(2026, 11, 5))), "c2")
        val e3 = Entry(at(5, 18, 1), Payload.ChapterSeen(Chapter(Subject.PHYSIQUE, "Suites", LocalDate.of(2026, 11, 5))), "c3")
        val s = StateBuilder.build(data, listOf(e1, e2, e3))
        assertEquals(2, s.chapters.size)
        // le premier enregistrement fait foi
        assertEquals(LocalDate.of(2026, 11, 4), s.chapters.first { it.subject == Subject.MATHS }.seen)
    }

    @Test
    fun declarationsRangsEtDm() {
        val entries = listOf(
            Entry(at(9, 10, 0), Payload.Difficulty(DifficultyDecl(Subject.PHYSIQUE, at(9, 10, 0))), "d1"),
            Entry(at(9, 10, 1), Payload.Priority(PriorityDecl(setOf(Subject.MATHS), at(9, 10, 1))), "p1"),
            Entry(at(9, 10, 2), Payload.Priority(PriorityDecl(emptySet(), at(9, 10, 2))), "p2"),
            Entry(at(9, 10, 3), Payload.RankEntry(Rank(Subject.MATHS, LocalDate.of(2026, 11, 7), 12, 41)), "r1"),
            Entry(at(9, 10, 4), Payload.DmAdded(Dm("dm1", Subject.MATHS, LocalDate.of(2026, 11, 18))), "m1"),
            Entry(at(14, 10, 4), Payload.DmDone("dm1"), "m2")
        )
        val s = StateBuilder.build(data, entries)
        assertEquals(1, s.difficulty.size)
        assertEquals(2, s.priority.size)
        assertTrue(s.priority[1].subjects.isEmpty())
        assertEquals(1, s.ranks.size)
        assertEquals(listOf(Dm("dm1", Subject.MATHS, LocalDate.of(2026, 11, 18))), s.dms)
        assertEquals(setOf("dm1"), s.dmDone)
    }

    @Test
    fun unDmRetireNEstPlusDuTout() {
        val s = StateBuilder.build(
            data,
            listOf(
                Entry(at(9, 10, 4), Payload.DmAdded(Dm("dm1", Subject.MATHS, LocalDate.of(2026, 11, 18))), "m1"),
                Entry(at(9, 10, 5), Payload.Retract("m1"), "x1")
            )
        )
        assertTrue(s.dms.isEmpty())
    }

    @Test
    fun deBoutEnBoutDuJsonAuMoteur() {
        // lundi 9 nov. 2026, 16h : le TD de Chimie (11h05-12h) est fini, le sport est proposé d'abord
        val state0 = StateBuilder.build(data, emptyList())
        assertEquals(Action.Sport(40), nextAction(state0, at(9, 16, 0)))

        // « Non » au sport, enregistré dans le journal : on passe au J0 (16h05 → dîner à 19h : 175 min, moins 5 de buffer = 170)
        val declined = Entry(
            at(9, 16, 1),
            Payload.Session(SessionRecord(SessionKind.SPORT, Outcome.DECLINED, at(9, 16, 1), 0)),
            "s1"
        )
        val state1 = StateBuilder.build(data, listOf(declined))
        assertEquals(
            Action.J0Macro(listOf(J0Item(Subject.CHIMIE, LocalDate.of(2026, 11, 9))), 170),
            nextAction(state1, at(9, 16, 5))
        )

        // J0 fait : plus rien en attente, et sans chapitre le moteur laisse le repos libre
        val j0Done = Entry(
            at(9, 16, 50),
            Payload.Session(
                SessionRecord(SessionKind.J0, Outcome.DONE, at(9, 16, 10), 40, Subject.CHIMIE, null, LocalDate.of(2026, 11, 9))
            ),
            "s2"
        )
        val state2 = StateBuilder.build(data, listOf(declined, j0Done))
        assertEquals(Action.None(Reason.REST), nextAction(state2, at(9, 17, 0)))

        // une session lancée par erreur est annulée : le J0 redevient dû
        val state3 = StateBuilder.build(data, listOf(declined, j0Done, Entry(at(9, 17, 1), Payload.Retract("s2"), "x")))
        assertTrue(nextAction(state3, at(9, 17, 5)) is Action.J0Macro)
        assertFalse(state3.sessions.any { it.kind == SessionKind.J0 })
    }
}

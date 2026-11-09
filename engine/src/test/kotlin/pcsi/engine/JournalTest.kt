package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime

class JournalTest {

    private val t = LocalDateTime.of(2026, 11, 9, 20, 5)

    private fun roundTrip(e: Entry) {
        assertEquals(e, Journal.decode(Journal.encode(e)))
    }

    private fun tempDir(): File = Files.createTempDirectory("pcsi-test").toFile()

    @Test
    fun allerRetourDeChaqueTypeDEntree() {
        roundTrip(
            Entry(t, Payload.Session(SessionRecord(SessionKind.DEEPWORK, Outcome.DONE, t, 45, Subject.MATHS, "Suites", null)), "id-1")
        )
        roundTrip(
            Entry(
                t,
                Payload.Session(SessionRecord(SessionKind.J0, Outcome.SKIPPED, t, 0, Subject.SI, null, LocalDate.of(2026, 11, 9))),
                "id-2"
            )
        )
        roundTrip(Entry(t, Payload.Session(SessionRecord(SessionKind.SPORT, Outcome.DECLINED, t, 0)), "id-3"))
        roundTrip(Entry(t, Payload.ChapterSeen(Chapter(Subject.MATHS, "Suites", LocalDate.of(2026, 11, 4))), "id-4"))
        roundTrip(Entry(t, Payload.Difficulty(DifficultyDecl(Subject.PHYSIQUE, t)), "id-5"))
        roundTrip(Entry(t, Payload.Priority(PriorityDecl(setOf(Subject.MATHS, Subject.SI), t)), "id-6"))
        roundTrip(Entry(t, Payload.Priority(PriorityDecl(emptySet(), t)), "id-7"))
        roundTrip(Entry(t, Payload.RankEntry(Rank(Subject.MATHS, LocalDate.of(2026, 11, 7), 12, 41)), "id-8"))
        roundTrip(Entry(t, Payload.DmAdded(Dm("dm1", Subject.MATHS, LocalDate.of(2026, 11, 18))), "id-9"))
        roundTrip(Entry(t, Payload.DmDone("dm1"), "id-10"))
        roundTrip(Entry(t, Payload.Retract("id-1"), "id-11"))
    }

    @Test
    fun allerRetourAvecSecondesEtCaracteresSpeciaux() {
        val name = "Suites & séries \"ε\" \\ x — ordre 1"
        roundTrip(Entry(LocalDateTime.of(2026, 11, 9, 20, 5, 30), Payload.ChapterSeen(Chapter(Subject.MATHS, name, LocalDate.of(2026, 11, 4))), "id-x"))
    }

    @Test
    fun uneEntreeTientSurUneSeuleLigne() {
        val e = Entry(t, Payload.ChapterSeen(Chapter(Subject.MATHS, "a\nb", LocalDate.of(2026, 11, 4))), "id-n")
        assertFalse(Journal.encode(e).contains("\n"))
        roundTrip(e)
    }

    @Test
    fun lignesIllisiblesOuIncompletes() {
        assertNull(Journal.decode("pas du json"))
        assertNull(Journal.decode("""{"id":"x","at":"2026-11-09T20:05","type":"inconnu"}"""))
        assertNull(Journal.decode("""{"id":"x","at":"2026-11-09T20:05","type":"rank","subject":"MATHS"}"""))
        assertNull(Journal.decode("""{"id":"x","at":"hier","type":"dm_done","dmId":"dm1"}"""))
        assertNull(Journal.decode("""{"id":"x","at":"2026-11-09T20:05","type":"difficulty","subject":"MATH","declaredAt":"2026-11-09T20:05"}"""))
        assertNotNull(Journal.decode("""{"id":"x","at":"2026-11-09T20:05","type":"dm_done","dmId":"dm1"}"""))
    }

    @Test
    fun decodeAllIgnoreLesLignesVidesEtCompteLesAutres() {
        val good = Journal.encode(Entry(t, Payload.DmDone("dm1"), "id-1"))
        val r = Journal.decodeAll(listOf(good, "", "   ", "{coupé", good))
        assertEquals(2, r.entries.size)
        assertEquals(1, r.skipped.size)
        assertTrue(r.skipped[0].contains("4"))
    }

    @Test
    fun fichierAjoutEtRelectureDansLOrdre() {
        val store = JournalStore(File(tempDir(), "journal.jsonl"))
        assertEquals(0, store.readAll().entries.size)
        val a = Entry(t, Payload.DmDone("a"), "id-a")
        val b = Entry(t.plusMinutes(1), Payload.DmDone("b"), "id-b")
        store.append(a)
        store.append(b)
        val r = store.readAll()
        assertEquals(listOf(a, b), r.entries)
        assertEquals(0, r.skipped.size)
    }

    @Test
    fun reprisApresUneEcritureCoupee() {
        val file = File(tempDir(), "journal.jsonl")
        val store = JournalStore(file)
        store.append(Entry(t, Payload.DmDone("a"), "id-a"))
        file.appendText("""{"id":"zz","at":"2026-11""") // coupure en plein milieu, sans fin de ligne
        store.append(Entry(t, Payload.DmDone("c"), "id-c"))
        val r = store.readAll()
        assertEquals(listOf("id-a", "id-c"), r.entries.map { it.id })
        assertEquals(1, r.skipped.size)
    }

    @Test
    fun importValideEcritLeFichier() {
        val file = File(tempDir(), "data.json")
        val store = DataStore(file)
        assertNull(store.load())
        val text = """{"version": 1, "semester": 1, "weekA": "2026-11-09", "slots": [{"day": "WED", "start": "14:00", "end": "15:50", "subject": "PHILO"}]}"""
        val r = store.import(text)
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(text, file.readText())
        assertEquals(1, store.load()!!.data!!.slots.size)
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun importErroneNEcraseJamaisLesBonnesDonnees() {
        val file = File(tempDir(), "data.json")
        val store = DataStore(file)
        val good = """{"version": 1, "semester": 1, "weekA": "2026-11-09", "slots": [{"day": "WED", "start": "14:00", "end": "15:50", "subject": "PHILO"}]}"""
        store.import(good)
        val bad = good.replace("14:00", "99:00")
        val r = store.import(bad)
        assertFalse(r.ok)
        assertEquals(good, file.readText())
        assertFalse(File(file.path + ".tmp").exists())
    }
}

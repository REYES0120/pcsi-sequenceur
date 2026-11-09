package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ImporterTest {

    private fun doc(
        slots: String,
        colles: String = "[]",
        ds: String = "[]",
        daysOff: String = "[]",
        weekA: String = "2026-11-09",
        version: String = "1"
    ): String =
        """{"version": $version, "semester": 1, "weekA": "$weekA", "daysOff": $daysOff, "slots": $slots, "colles": $colles, "ds": $ds}"""

    private val chimieTd =
        """{"day": "MON", "start": "11:05", "end": "12:00", "subject": "CHIMIE", "type": "TD", "week": "A"}"""
    private val eps = """{"day": "TUE", "start": "17:05", "end": "18:00", "type": "EPS"}"""
    private val philo = """{"day": "WED", "start": "14:00", "end": "15:50", "subject": "PHILO"}"""

    @Test
    fun fichierValideEtComplet() {
        val text = doc(
            slots = "[$chimieTd, $eps, $philo]",
            colles = """[{"date": "2026-11-09", "start": "13:00", "end": "14:00", "subject": "CHIMIE", "colleur": "M. Chenevois"}]""",
            ds = """[{"date": "2026-11-14", "start": "08:00", "end": "12:00", "subjects": ["MATHS"]}]""",
            daysOff = """[{"from": "2026-12-19", "to": "2027-01-03", "label": "Noël"}]"""
        )
        val r = Importer.parse(text)
        assertTrue(r.errors.toString(), r.ok)
        assertTrue(r.warnings.toString(), r.warnings.isEmpty())
        val d = r.data!!
        assertEquals(1, d.semester)
        assertEquals(LocalDate.of(2026, 11, 9), d.weekAReference)
        assertEquals(3, d.slots.size)
        assertEquals(
            Slot(DayOfWeek.MONDAY, LocalTime.of(11, 5), LocalTime.of(12, 0), Subject.CHIMIE, SlotType.TD, WeekKind.A, 1),
            d.slots[0]
        )
        assertNull(d.slots[1].subject)
        assertEquals(SlotType.EPS, d.slots[1].type)
        // valeurs par défaut : type COURS, semaine BOTH, semestre du fichier
        assertEquals(
            Slot(DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 50), Subject.PHILO, SlotType.COURS, WeekKind.BOTH, 1),
            d.slots[2]
        )
        assertEquals(DateSpan(LocalDate.of(2026, 12, 19), LocalDate.of(2027, 1, 3)), d.daysOff[0])
        assertEquals(
            Colle(LocalDate.of(2026, 11, 9), LocalTime.of(13, 0), LocalTime.of(14, 0), Subject.CHIMIE),
            d.colles[0]
        )
        assertEquals(listOf(Subject.MATHS), d.ds[0].subjects)
    }

    @Test
    fun jourEnFrancaisEtMinusculesAcceptes() {
        val r = Importer.parse(doc("""[{"day": "lundi", "start": "08:00", "end": "09:50", "subject": "maths"}]"""))
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(DayOfWeek.MONDAY, r.data!!.slots[0].day)
        assertEquals(Subject.MATHS, r.data!!.slots[0].subject)
    }

    @Test
    fun jsonInvalide() {
        val r = Importer.parse("{ pas du json")
        assertFalse(r.ok)
        assertNull(r.data)
        assertTrue(r.errors[0].startsWith("JSON invalide"))
    }

    @Test
    fun racineQuiNEstPasUnObjet() {
        val r = Importer.parse("[1, 2]")
        assertFalse(r.ok)
        assertTrue(r.errors[0].contains("objet"))
    }

    @Test
    fun versionEtChampsObligatoires() {
        val missing = Importer.parse("""{"semester": 1, "weekA": "2026-11-09", "slots": []}""")
        assertTrue(missing.errors.any { it.startsWith("version") })
        val wrong = Importer.parse(doc("[$philo]", version = "2"))
        assertTrue(wrong.errors.any { it.contains("version") && it.contains("2") })
    }

    @Test
    fun heureInvalideIndiqueLeChemin() {
        val r = Importer.parse(doc("[" + chimieTd.replace("11:05", "25:00") + "]"))
        assertFalse(r.ok)
        assertTrue(r.errors.toString(), r.errors.any { it.startsWith("slots[0].start") && it.contains("25:00") })
    }

    @Test
    fun finAvantDebut() {
        val r = Importer.parse(doc("[" + chimieTd.replace("12:00", "11:00") + "]"))
        assertTrue(r.errors.any { it.startsWith("slots[0]") && it.contains("fin") })
    }

    @Test
    fun matiereInconnueListeLesValeursPossibles() {
        val r = Importer.parse(doc("[" + chimieTd.replace("CHIMIE", "MATH") + "]"))
        assertTrue(r.errors.any { it.startsWith("slots[0].subject") && it.contains("MATH") && it.contains("MATHS") })
    }

    @Test
    fun matiereObligatoireSaufPourLEPS() {
        val r = Importer.parse(doc("""[$philo, {"day": "TUE", "start": "17:05", "end": "18:00"}]"""))
        assertTrue(r.errors.toString(), r.errors.any { it.startsWith("slots[1].subject") })
    }

    @Test
    fun plusieursErreursSontToutesRemontees() {
        val bad1 = chimieTd.replace("11:05", "25:00")
        val bad2 = philo.replace("PHILO", "XXX")
        val r = Importer.parse(doc("[$bad1, $bad2]"))
        assertTrue(r.errors.any { it.startsWith("slots[0].start") })
        assertTrue(r.errors.any { it.startsWith("slots[1].subject") })
    }

    @Test
    fun semaineANonLundiEstUnAvertissement() {
        val r = Importer.parse(doc("[$philo]", weekA = "2026-11-10"))
        assertTrue(r.ok)
        assertTrue(r.warnings.any { it.contains("lundi") })
    }

    @Test
    fun cleInconnueEstIgnoreeAvecAvertissement() {
        val r = Importer.parse(doc("""[{"day": "WED", "start": "14:00", "end": "15:50", "subject": "PHILO", "salle": "413"}]"""))
        assertTrue(r.ok)
        assertTrue(r.warnings.any { it.startsWith("slots[0].salle") })
    }

    @Test
    fun chevauchementDeCoursEstUnAvertissement() {
        val other = """{"day": "MON", "start": "11:30", "end": "12:30", "subject": "SI", "week": "A"}"""
        val r = Importer.parse(doc("[$chimieTd, $other]"))
        assertTrue(r.ok)
        assertTrue(r.warnings.any { it.contains("Chevauchement") })
    }

    @Test
    fun pasDeChevauchementEntreSemainesAEtB() {
        val b = """{"day": "MON", "start": "11:05", "end": "12:00", "subject": "PHYSIQUE", "week": "B"}"""
        val r = Importer.parse(doc("[$chimieTd, $b]"))
        assertTrue(r.warnings.toString(), r.warnings.none { it.contains("Chevauchement") })
    }

    @Test
    fun collePendantUnCoursOuUnCongeEstSignalee() {
        val duringClass = """{"date": "2026-11-09", "start": "11:30", "end": "12:30", "subject": "MATHS"}"""
        val onHoliday = """{"date": "2026-12-25", "start": "13:00", "end": "14:00", "subject": "MATHS"}"""
        val r = Importer.parse(
            doc(
                slots = "[$chimieTd]",
                colles = "[$duringClass, $onHoliday]",
                daysOff = """[{"from": "2026-12-19", "to": "2027-01-03"}]"""
            )
        )
        assertTrue(r.ok)
        assertTrue(r.warnings.any { it.contains("pendant un cours") })
        assertTrue(r.warnings.any { it.contains("jour de congé") })
    }

    @Test
    fun congeAlEnversEstUneErreur() {
        val r = Importer.parse(doc("[$philo]", daysOff = """[{"from": "2027-01-03", "to": "2026-12-19"}]"""))
        assertTrue(r.errors.any { it.startsWith("daysOff[0]") })
    }

    @Test
    fun dsSansMatiereEstUneErreur() {
        val r = Importer.parse(
            doc("[$philo]", ds = """[{"date": "2026-11-14", "start": "08:00", "end": "12:00", "subjects": []}]""")
        )
        assertTrue(r.errors.any { it.startsWith("ds[0].subjects") })
    }

    @Test
    fun dsAvecChimieEtSi() {
        val r = Importer.parse(
            doc("[$philo]", ds = """[{"date": "2026-11-28", "start": "08:00", "end": "12:00", "subjects": ["CHIMIE", "SI"]}]""")
        )
        assertTrue(r.ok)
        assertEquals(listOf(Subject.CHIMIE, Subject.SI), r.data!!.ds[0].subjects)
        assertNotNull(r.data)
    }

    @Test
    fun semestreInvalide() {
        val r = Importer.parse(doc("[$philo]").replace("\"semester\": 1", "\"semester\": 3"))
        assertTrue(r.errors.any { it.startsWith("semester") })
    }
}

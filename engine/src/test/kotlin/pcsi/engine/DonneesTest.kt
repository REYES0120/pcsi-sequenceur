package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Vérifie les vrais fichiers donnees/data.json et donnees/data_cameo.json avec le vrai importeur. */
class DonneesTest {

    private fun load(name: String): ImportResult {
        val dir = listOf("../donnees", "donnees").map { File(it) }.first { it.isDirectory }
        return Importer.parse(File(dir, name).readText(Charsets.UTF_8))
    }

    private fun colle(date: String, start: Int, subject: Subject) =
        Colle(LocalDate.parse(date), LocalTime.of(start, 0), LocalTime.of(start + 1, 0), subject)

    @Test
    fun monFichier() {
        val r = load("data.json")
        assertTrue(r.errors.toString(), r.ok)
        val d = r.data!!
        assertEquals(21, d.slots.size)
        assertEquals(24, d.colles.size)
        assertEquals(7, d.ds.size)
        assertEquals(3, d.daysOff.size)
        // avertissements connus : la colle d'anglais du 11/11 (Armistice) et celle du 05/01 à 17h-18h,
        // qui tombe sur la Physique et l'EPS du mardi
        assertEquals(r.warnings.toString(), 3, r.warnings.size)
        assertTrue(r.warnings.toString(), r.warnings.all { it.contains("jour de congé") || it.contains("pendant un cours") })
        // les trois règles du colloscope : lundi de la semaine + jour de la ligne ; colonne « 13/01 » = semaine du 11/01
        assertTrue(d.colles.contains(colle("2026-11-09", 13, Subject.CHIMIE)))
        assertTrue(d.colles.contains(colle("2026-11-11", 17, Subject.ANGLAIS)))
        assertTrue(d.colles.contains(colle("2026-11-20", 12, Subject.MATHS)))
        assertTrue(d.colles.contains(colle("2026-12-01", 18, Subject.PHYSIQUE)))
        assertTrue(d.colles.contains(colle("2027-01-15", 15, Subject.MATHS)))
        assertTrue(d.colles.none { it.date == LocalDate.parse("2027-01-17") })
        // DS Chimie et SI toujours ensemble
        assertEquals(listOf(Subject.CHIMIE, Subject.SI), d.ds[2].subjects)
        assertEquals(listOf(Subject.CHIMIE, Subject.SI), d.ds[5].subjects)
    }

    @Test
    fun fichierDeCameo() {
        val r = load("data_cameo.json")
        assertTrue(r.errors.toString(), r.ok)
        val d = r.data!!
        assertEquals(21, d.slots.size)
        assertEquals(24, d.colles.size)
        assertEquals(7, d.ds.size)
        assertEquals(r.warnings.toString(), 3, r.warnings.size)
        assertTrue(r.warnings.toString(), r.warnings.all { it.contains("jour de congé") || it.contains("pendant un cours") })
        assertTrue(d.colles.contains(colle("2027-01-13", 16, Subject.MATHS)))
        assertTrue(d.colles.contains(colle("2027-01-14", 13, Subject.PHYSIQUE)))
        assertTrue(d.colles.contains(colle("2026-11-09", 19, Subject.CHIMIE)))
    }

    @Test
    fun memeEmploiDuTempsMaisPasLesMemesColles() {
        val a = load("data.json").data!!
        val b = load("data_cameo.json").data!!
        assertEquals(a.slots, b.slots)
        assertEquals(a.ds, b.ds)
        assertEquals(a.daysOff, b.daysOff)
        assertEquals(a.weekAReference, b.weekAReference)
        assertNotEquals(a.colles, b.colles)
    }

    @Test
    fun semainesAEtBDeLEmploiDuTemps() {
        val st = StateBuilder.build(load("data.json").data!!, emptyList())
        // lundi 9 nov. (A) : TP chimie, TD chimie, cours de SI ; lundi 16 nov. (B) : TP physique, TD de SI
        assertEquals(3, Constraints.classesOn(st, LocalDate.of(2026, 11, 9)).size)
        assertEquals(2, Constraints.classesOn(st, LocalDate.of(2026, 11, 16)).size)
        // le 11 novembre est férié
        assertEquals(0, Constraints.classesOn(st, LocalDate.of(2026, 11, 11)).size)
        // pendant les vacances de Noël
        assertEquals(0, Constraints.classesOn(st, LocalDate.of(2026, 12, 22)).size)
        // jeudi : Info cours seulement en semaine A
        assertEquals(4, Constraints.classesOn(st, LocalDate.of(2026, 11, 12)).size)
        assertEquals(3, Constraints.classesOn(st, LocalDate.of(2026, 11, 19)).size)
    }

    @Test
    fun leMoteurTourneSurLesVraiesDonnees() {
        val st = StateBuilder.build(load("data.json").data!!, emptyList())
        // lundi 9 nov. 16h : dernier cours (SI) fini à 15h50, 3 h libres avant le dîner → sport
        assertEquals(Action.Sport(40), nextAction(st, LocalDateTime.of(2026, 11, 9, 16, 0)))
        // mercredi 2 déc. 16h30 : colle de maths en cours (16h-17h), plus de cours
        assertEquals(
            Action.None(Reason.EXTERNAL, LocalDateTime.of(2026, 12, 2, 17, 0)),
            nextAction(st, LocalDateTime.of(2026, 12, 2, 16, 30))
        )
        // samedi 14 nov. 9h : DS de maths
        assertEquals(
            Action.None(Reason.EXTERNAL, LocalDateTime.of(2026, 11, 14, 12, 0)),
            nextAction(st, LocalDateTime.of(2026, 11, 14, 9, 0))
        )
        // le prochain déclencheur depuis le lundi 9 nov. 16h est la fin du dîner à 20h
        assertEquals(LocalDateTime.of(2026, 11, 9, 20, 0), Triggers.next(st, LocalDateTime.of(2026, 11, 9, 16, 0)))
    }
}

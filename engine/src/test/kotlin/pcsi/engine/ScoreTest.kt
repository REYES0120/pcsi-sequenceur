package pcsi.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScoreTest {
    // Mercredi 11 novembre 2026, 21h00
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 11, 21, 0)

    private fun chap(s: Subject, name: String, seen: String) = Chapter(s, name, LocalDate.parse(seen))

    private fun dw(
        s: Subject,
        minutes: Int,
        daysAgo: Long = 0L,
        chapter: String? = null,
        outcome: Outcome = Outcome.DONE
    ) = SessionRecord(
        SessionKind.DEEPWORK,
        outcome,
        now.minusDays(daysAgo).minusMinutes(minutes.toLong()),
        minutes,
        s,
        chapter
    )

    private fun ds(date: String, vararg subjects: Subject) =
        Ds(LocalDate.parse(date), LocalTime.of(8, 0), LocalTime.of(12, 0), subjects.toList())

    private fun colle(date: String, subject: Subject) =
        Colle(LocalDate.parse(date), LocalTime.of(13, 0), LocalTime.of(14, 0), subject)

    @Test
    fun scoreAssemble() {
        val st = emptyState().copy(
            chapters = listOf(chap(Subject.MATHS, "Suites", "2026-11-09")),
            ds = listOf(ds("2026-11-14", Subject.MATHS))
        )
        val c = Score.candidates(st, now)
        assertEquals(1, c.size)
        // base 3 + urgence 6 (retard de 2 j) + DS dans 3 j : 5
        assertEquals(14.0, c[0].score.total, 1e-9)
    }

    @Test
    fun paliersDeRevision() {
        val seen = LocalDate.parse("2026-11-04")
        val chapter = chap(Subject.MATHS, "Suites", "2026-11-04")
        val j0 = SessionRecord(
            SessionKind.J0, Outcome.DONE, now.minusDays(7), 30, Subject.MATHS, null, seen
        )
        val base = emptyState().copy(chapters = listOf(chapter))

        // rien de validé : palier 0, dû à la date de vue
        val s0 = Review.chapterState(base, chapter)
        assertEquals(0, s0.validations)
        assertEquals(seen, s0.nextReview)

        // J0 fait : palier 1, dû à J+3
        val s1 = Review.chapterState(base.copy(sessions = listOf(j0)), chapter)
        assertEquals(1, s1.validations)
        assertEquals(seen.plusDays(3), s1.nextReview)

        // J0 fait + un deepwork fini : palier 2, dû à J+7 = aujourd'hui
        val s2 = Review.chapterState(
            base.copy(sessions = listOf(j0, dw(Subject.MATHS, 45, 1, "Suites"))), chapter
        )
        assertEquals(2, s2.validations)
        assertEquals(LocalDate.parse("2026-11-11"), s2.nextReview)
        assertEquals(4.0, Review.urgence(s2.nextReview, now.toLocalDate()), 1e-9)
    }

    @Test
    fun skipNeValidePasLeChapitre() {
        val seen = LocalDate.parse("2026-11-04")
        val chapter = chap(Subject.MATHS, "Suites", "2026-11-04")
        val j0Skip = SessionRecord(
            SessionKind.J0, Outcome.SKIPPED, now.minusDays(7), 0, Subject.MATHS, null, seen
        )
        val dwSkip = dw(Subject.MATHS, 0, 1, "Suites", Outcome.SKIPPED)
        val st = emptyState().copy(chapters = listOf(chapter), sessions = listOf(j0Skip, dwSkip))
        assertEquals(0, Review.chapterState(st, chapter).validations)
    }

    @Test
    fun tableUrgence() {
        val today = LocalDate.parse("2026-11-11")
        assertEquals(6.0, Review.urgence(today.minusDays(3), today), 1e-9)
        assertEquals(5.0, Review.urgence(today.minusDays(1), today), 1e-9)
        assertEquals(4.0, Review.urgence(today, today), 1e-9)
        assertEquals(3.0, Review.urgence(today.plusDays(1), today), 1e-9)
        assertEquals(2.0, Review.urgence(today.plusDays(2), today), 1e-9)
        assertEquals(1.0, Review.urgence(today.plusDays(3), today), 1e-9)
        assertEquals(0.0, Review.urgence(today.plusDays(4), today), 1e-9)
        assertEquals(0.0, Review.urgence(null, today), 1e-9)
    }

    @Test
    fun bonusDsParTranche() {
        fun bonus(date: String) =
            Score.bonusDs(emptyState().copy(ds = listOf(ds(date, Subject.MATHS))), Subject.MATHS, now)
        assertEquals(5.0, bonus("2026-11-14"), 1e-9) // 3 j
        assertEquals(4.5, bonus("2026-11-17"), 1e-9) // 6 j
        assertEquals(4.0, bonus("2026-11-21"), 1e-9) // 10 j
        assertEquals(0.0, bonus("2026-11-26"), 1e-9) // 15 j
        assertEquals(0.0, bonus("2026-11-07"), 1e-9) // déjà passé
        // une autre matière n'est pas concernée
        assertEquals(
            0.0,
            Score.bonusDs(emptyState().copy(ds = listOf(ds("2026-11-14", Subject.MATHS))), Subject.PHYSIQUE, now),
            1e-9
        )
        // Chimie et SI partagent le même DS
        val both = emptyState().copy(ds = listOf(ds("2026-11-14", Subject.CHIMIE, Subject.SI)))
        assertEquals(5.0, Score.bonusDs(both, Subject.CHIMIE, now), 1e-9)
        assertEquals(5.0, Score.bonusDs(both, Subject.SI, now), 1e-9)
    }

    @Test
    fun bonusCollePlateau() {
        fun bonus(date: String) =
            Score.bonusColle(emptyState().copy(colles = listOf(colle(date, Subject.MATHS))), Subject.MATHS, now)
        assertEquals(2.0, bonus("2026-11-12"), 1e-9)
        assertEquals(2.0, bonus("2026-11-18"), 1e-9) // 7 j
        assertEquals(0.0, bonus("2026-11-19"), 1e-9) // 8 j
        assertEquals(0.0, bonus("2026-11-10"), 1e-9) // passée
    }

    @Test
    fun bonusRangMauvaisRangEstBonus() {
        fun bonus(rank: Int, size: Int) =
            Score.bonusRang(
                emptyState().copy(ranks = listOf(Rank(Subject.MATHS, LocalDate.parse("2026-11-07"), rank, size))),
                Subject.MATHS
            )
        assertEquals(-0.5, bonus(10, 40), 1e-9) // 25 %
        assertEquals(0.0, bonus(20, 40), 1e-9) // 50 %
        assertEquals(0.5, bonus(30, 40), 1e-9) // 75 %
        assertEquals(1.5, bonus(31, 40), 1e-9) // > 75 %
        assertEquals(0.0, Score.bonusRang(emptyState(), Subject.MATHS), 1e-9) // pas de rang
    }

    @Test
    fun rangLePlusRecentFaitFoi() {
        val st = emptyState().copy(
            ranks = listOf(
                Rank(Subject.MATHS, LocalDate.parse("2026-10-03"), 38, 40),
                Rank(Subject.MATHS, LocalDate.parse("2026-11-07"), 5, 40)
            )
        )
        assertEquals(-0.5, Score.bonusRang(st, Subject.MATHS), 1e-9)
    }

    @Test
    fun penaliteRecence() {
        fun pen(daysAgo: Long, outcome: Outcome = Outcome.DONE) =
            Score.penaliteRecence(
                emptyState().copy(sessions = listOf(dw(Subject.MATHS, if (outcome == Outcome.DONE) 30 else 0, daysAgo, null, outcome))),
                Subject.MATHS,
                now
            )
        assertEquals(2.0, pen(0), 1e-9)
        assertEquals(1.0, pen(1), 1e-9)
        assertEquals(0.5, pen(2), 1e-9)
        assertEquals(0.0, pen(3), 1e-9)
        assertEquals(2.0, pen(0, Outcome.SKIPPED), 1e-9) // un skip compte comme fait
        assertEquals(0.0, Score.penaliteRecence(emptyState(), Subject.MATHS, now), 1e-9)
    }

    @Test
    fun j0CompteDansLaRecence() {
        val j0 = SessionRecord(SessionKind.J0, Outcome.DONE, now.minusMinutes(90), 30, Subject.PHYSIQUE, null, now.toLocalDate())
        val st = emptyState().copy(sessions = listOf(j0))
        assertEquals(2.0, Score.penaliteRecence(st, Subject.PHYSIQUE, now), 1e-9)
    }

    @Test
    fun penaliteSurcharge() {
        fun others(minutes: Int) = listOf(
            dw(Subject.PHYSIQUE, minutes), dw(Subject.SI, minutes),
            dw(Subject.CHIMIE, minutes), dw(Subject.ANGLAIS, minutes)
        )
        fun pen(mathsMinutes: Int, otherMinutes: Int) =
            Score.penaliteSurcharge(
                emptyState().copy(sessions = listOf(dw(Subject.MATHS, mathsMinutes)) + others(otherMinutes)),
                Subject.MATHS,
                now
            )
        assertEquals(3.0, pen(300, 60), 1e-9) // 5 h contre 1 h de moyenne : > 2 ×
        assertEquals(1.5, pen(96, 60), 1e-9) // 1,6 h : > 1,5 ×
        assertEquals(0.0, pen(90, 60), 1e-9) // 1,5 h pile : pas au-dessus
        assertEquals(0.0, pen(300, 0), 1e-9) // moyenne des autres nulle
    }

    @Test
    fun surchargeIgnoreLesSessionsDePlusDeSeptJours() {
        val st = emptyState().copy(
            sessions = listOf(dw(Subject.MATHS, 300, 8), dw(Subject.PHYSIQUE, 60))
        )
        assertEquals(0.0, Score.penaliteSurcharge(st, Subject.MATHS, now), 1e-9)
    }

    @Test
    fun difficulteEtPrioriteSurSeptJours() {
        val dif6 = emptyState().copy(difficulty = listOf(DifficultyDecl(Subject.MATHS, now.minusDays(6))))
        val dif8 = emptyState().copy(difficulty = listOf(DifficultyDecl(Subject.MATHS, now.minusDays(8))))
        assertEquals(1.5, Score.bonusDifficulte(dif6, Subject.MATHS, now), 1e-9)
        assertEquals(0.0, Score.bonusDifficulte(dif8, Subject.MATHS, now), 1e-9)
        assertEquals(0.0, Score.bonusDifficulte(dif6, Subject.PHYSIQUE, now), 1e-9)

        val prio = emptyState().copy(priority = listOf(PriorityDecl(setOf(Subject.MATHS), now.minusDays(1))))
        assertEquals(1.5, Score.bonusPriorite(prio, Subject.MATHS, now), 1e-9)
        assertEquals(0.0, Score.bonusPriorite(prio, Subject.PHYSIQUE, now), 1e-9)

        // la dernière déclaration l'emporte ; vide = aucune matière prioritaire
        val none = prio.copy(
            priority = prio.priority + PriorityDecl(emptySet(), now.minusHours(1))
        )
        assertEquals(0.0, Score.bonusPriorite(none, Subject.MATHS, now), 1e-9)
    }

    @Test
    fun departageParRevueLaPlusAncienne() {
        val st = emptyState().copy(
            chapters = listOf(
                chap(Subject.MATHS, "B", "2026-11-21"),
                chap(Subject.PHYSIQUE, "X", "2026-11-20"),
                chap(Subject.MATHS, "A", "2026-11-20")
            )
        )
        val names = Score.candidates(st, now).map { it.chapter.chapter.name }
        // scores égaux (base 3, urgence 0) : date de revue, puis matière (Maths avant Physique)
        assertEquals(listOf("A", "X", "B"), names)
    }

    @Test
    fun infoPhiloFrancaisHorsDeepwork() {
        val st = emptyState().copy(chapters = listOf(chap(Subject.INFO, "Python", "2026-11-09")))
        assertEquals(0, Score.candidates(st, now).size)
    }
}

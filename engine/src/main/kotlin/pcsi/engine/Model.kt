package pcsi.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Subject { MATHS, PHYSIQUE, CHIMIE, SI, ANGLAIS, INFO, PHILO }

enum class SlotType { COURS, TD, TP, EPS }

enum class WeekKind { A, B, BOTH }

/** Créneau récurrent de l'emploi du temps. `subject` est null pour l'EPS. */
data class Slot(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val subject: Subject?,
    val type: SlotType,
    val week: WeekKind,
    val semester: Int
)

/** Plage de dates (bornes incluses) : vacances, jours fériés (plage d'un jour). */
data class DateSpan(val from: LocalDate, val to: LocalDate) {
    fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(to)
}

data class Colle(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subject: Subject
) {
    val startAt: LocalDateTime get() = LocalDateTime.of(date, start)
    val endAt: LocalDateTime get() = LocalDateTime.of(date, end)
}

/** Un DS peut concerner plusieurs matières (ex. Chimie et SI sur le même créneau). */
data class Ds(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subjects: List<Subject>
) {
    val startAt: LocalDateTime get() = LocalDateTime.of(date, start)
    val endAt: LocalDateTime get() = LocalDateTime.of(date, end)
}

data class Chapter(val subject: Subject, val name: String, val seen: LocalDate)

/** Devoir maison : id unique, matière, date de rendu. Terminé quand son id est dans State.dmDone. */
data class Dm(val id: String, val subject: Subject, val due: LocalDate)

data class Rank(val subject: Subject, val date: LocalDate, val rank: Int, val size: Int)

/** Déclaration de difficulté : portée 7 jours, ne joue que sur le deepwork. */
data class DifficultyDecl(val subject: Subject, val at: LocalDateTime)

/** Matières prioritaires déclarées. Un ensemble vide signifie « aucune matière prioritaire ». */
data class PriorityDecl(val subjects: Set<Subject>, val at: LocalDateTime)

enum class SessionKind { J0, DEEPWORK, SPORT, CARDIO, PAUSE, CLOTURE, DM }

/**
 * DONE : session faite (fini, terminé, débordé), avec sa durée réelle.
 * SKIPPED : skip explicite, comptée comme faite mais sans validation de chapitre.
 * DECLINED : refus du sport ou du cardio (« Non »), ni compté ni skippé.
 */
enum class Outcome { DONE, SKIPPED, DECLINED }

data class SessionRecord(
    val kind: SessionKind,
    val outcome: Outcome,
    val start: LocalDateTime,
    val minutes: Int,
    val subject: Subject? = null,
    val chapter: String? = null,
    /** Pour un J0 : le jour des cours traités (peut être la veille). */
    val forDay: LocalDate? = null
) {
    val end: LocalDateTime get() = start.plusMinutes(minutes.toLong())
}

/**
 * Tout l'état dont le moteur a besoin. Aucun plan n'y est stocké :
 * ce qui a été fait (sessions), ce qui est dû et ce qui vient (se déduisent des données),
 * ce que tu as déclaré (difficulté, priorités, rangs).
 */
data class State(
    val semester: Int,
    val weekAReference: LocalDate,
    val daysOff: List<DateSpan>,
    val slots: List<Slot>,
    val colles: List<Colle>,
    val ds: List<Ds>,
    val chapters: List<Chapter>,
    val ranks: List<Rank>,
    val difficulty: List<DifficultyDecl>,
    val priority: List<PriorityDecl>,
    val sessions: List<SessionRecord>,
    val dms: List<Dm> = emptyList(),
    val dmDone: Set<String> = emptySet()
)

fun emptyState(weekAReference: LocalDate = LocalDate.of(2026, 11, 9)): State = State(
    semester = 1,
    weekAReference = weekAReference,
    daysOff = emptyList(),
    slots = emptyList(),
    colles = emptyList(),
    ds = emptyList(),
    chapters = emptyList(),
    ranks = emptyList(),
    difficulty = emptyList(),
    priority = emptyList(),
    sessions = emptyList()
)

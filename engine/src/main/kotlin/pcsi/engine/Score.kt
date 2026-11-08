package pcsi.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class ScoreBreakdown(
    val base: Double,
    val urgence: Double,
    val ds: Double,
    val colle: Double,
    val difficulte: Double,
    val priorite: Double,
    val rang: Double,
    val recence: Double,
    val surcharge: Double
) {
    val total: Double
        get() = base + urgence + ds + colle + difficulte + priorite + rang - recence - surcharge
}

data class Candidate(val chapter: ChapterState, val score: ScoreBreakdown)

object Score {

    private fun within7d(at: LocalDateTime, now: LocalDateTime): Boolean =
        at.isAfter(now.minusDays(7)) && !at.isAfter(now)

    /** bonus_DS : DS ≤3 j : 5 · ≤7 j : 4,5 · ≤14 j : 4 · au-delà : 0. Un DS déjà fini ne compte plus. */
    fun bonusDs(state: State, subject: Subject, now: LocalDateTime): Double {
        val today = now.toLocalDate()
        var best: Long? = null
        for (d in state.ds) {
            if (!d.subjects.contains(subject)) continue
            if (d.endAt.isBefore(now)) continue
            val days = ChronoUnit.DAYS.between(today, d.date)
            val current = best
            if (current == null || days < current) best = days
        }
        val b = best ?: return 0.0
        return when {
            b <= 3L -> 5.0
            b <= 7L -> 4.5
            b <= 14L -> 4.0
            else -> 0.0
        }
    }

    /** Colles à venir de la matière dans les 7 prochains jours (colle déjà finie exclue). */
    fun collesWithin7(state: State, subject: Subject, now: LocalDateTime): List<Colle> =
        state.colles.filter {
            it.subject == subject &&
                !it.endAt.isBefore(now) &&
                ChronoUnit.DAYS.between(now.toLocalDate(), it.date) <= Params.KHOLLE_WINDOW_DAYS.toLong()
        }

    /** bonus_kholle : +2 en plateau si une colle est dans ≤7 j. */
    fun bonusColle(state: State, subject: Subject, now: LocalDateTime): Double =
        if (collesWithin7(state, subject, now).isNotEmpty()) 2.0 else 0.0

    /** bonus_difficulté : +1,5 si déclarée il y a moins de 7 jours. Deepwork uniquement. */
    fun bonusDifficulte(state: State, subject: Subject, now: LocalDateTime): Double =
        if (state.difficulty.any { it.subject == subject && within7d(it.at, now) }) 1.5 else 0.0

    /** (H4) Matière prioritaire : +1,5 pendant 7 jours, d'après la dernière déclaration. */
    fun bonusPriorite(state: State, subject: Subject, now: LocalDateTime): Double {
        val last = state.priority.filter { within7d(it.at, now) }.maxByOrNull { it.at } ?: return 0.0
        return if (last.subjects.contains(subject)) 1.5 else 0.0
    }

    /** (H3) Percentile du dernier rang connu de la matière (rang / effectif), ou null. */
    fun percentile(state: State, subject: Subject): Double? {
        val r = state.ranks.filter { it.subject == subject && it.size > 0 }.maxByOrNull { it.date }
            ?: return null
        return r.rank.toDouble() / r.size.toDouble()
    }

    /** bonus_rang, version corrigée : mauvais rang = bonus. Sans rang : 0. */
    fun bonusRang(state: State, subject: Subject): Double {
        val p = percentile(state, subject) ?: return 0.0
        return when {
            p <= 0.25 -> -0.5
            p <= 0.5 -> 0.0
            p <= 0.75 -> 0.5
            else -> 1.5
        }
    }

    /** pénalité_récence : par matière (J0 et deepwork, fait ou skippé) : aujourd'hui 2, hier 1, avant-hier 0,5. */
    fun penaliteRecence(state: State, subject: Subject, now: LocalDateTime): Double {
        val today = now.toLocalDate()
        val days = state.sessions
            .filter {
                (it.kind == SessionKind.J0 || it.kind == SessionKind.DEEPWORK) &&
                    it.outcome != Outcome.DECLINED &&
                    it.subject == subject
            }
            .map { ChronoUnit.DAYS.between(it.start.toLocalDate(), today) }
            .filter { it >= 0L }
            .minOrNull() ?: return 0.0
        return when {
            days == 0L -> 2.0
            days == 1L -> 1.0
            days == 2L -> 0.5
            else -> 0.0
        }
    }

    /** Heures de la matière sur 7 jours glissants (J0 + deepwork faits). */
    fun hours7d(state: State, subject: Subject, now: LocalDateTime): Double {
        val minutes = state.sessions
            .filter {
                (it.kind == SessionKind.J0 || it.kind == SessionKind.DEEPWORK) &&
                    it.outcome == Outcome.DONE &&
                    it.subject == subject &&
                    within7d(it.start, now)
            }
            .sumOf { it.minutes }
        return minutes / 60.0
    }

    /**
     * (H2) pénalité_surcharge_hebdo : >1,5 × moyenne des autres : 1,5 · >2 × : 3.
     * Moyenne des autres nulle : 0. Les « autres » sont les matières de deepwork.
     */
    fun penaliteSurcharge(state: State, subject: Subject, now: LocalDateTime): Double {
        val others = Params.DEEPWORK_SUBJECTS.filter { it != subject }
        if (others.isEmpty()) return 0.0
        val mean = others.sumOf { hours7d(state, it, now) } / others.size
        if (mean <= 0.0) return 0.0
        val h = hours7d(state, subject, now)
        return when {
            h > 2.0 * mean -> 3.0
            h > 1.5 * mean -> 1.5
            else -> 0.0
        }
    }

    fun breakdown(state: State, cs: ChapterState, now: LocalDateTime): ScoreBreakdown {
        val s = cs.chapter.subject
        return ScoreBreakdown(
            base = Params.BASE[s] ?: 0.0,
            urgence = Review.urgence(cs.nextReview, now.toLocalDate()),
            ds = bonusDs(state, s, now),
            colle = bonusColle(state, s, now),
            difficulte = bonusDifficulte(state, s, now),
            priorite = bonusPriorite(state, s, now),
            rang = bonusRang(state, s),
            recence = penaliteRecence(state, s, now),
            surcharge = penaliteSurcharge(state, s, now)
        )
    }

    private val FAR_FUTURE: LocalDate = LocalDate.of(9999, 1, 1)

    /**
     * Tous les chapitres vus des matières de deepwork, triés : score décroissant, puis
     * prochaine_revue la plus ancienne, puis ordre de matière, puis nom (déterministe).
     */
    fun candidates(state: State, now: LocalDateTime): List<Candidate> =
        Review.states(state)
            .filter { Params.DEEPWORK_SUBJECTS.contains(it.chapter.subject) }
            .map { Candidate(it, breakdown(state, it, now)) }
            .sortedWith(
                compareByDescending<Candidate> { Math.round(it.score.total * 1_000_000.0) }
                    .thenBy { it.chapter.nextReview ?: FAR_FUTURE }
                    .thenBy { it.chapter.chapter.subject.ordinal }
                    .thenBy { it.chapter.chapter.name }
            )
}

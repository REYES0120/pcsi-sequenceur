package pcsi.engine

import java.time.LocalTime

/**
 * Toutes les constantes au même endroit. Les lignes marquées (Hn) sont des hypothèses
 * que tu n'as pas données telles quelles : à confirmer ou corriger.
 */
object Params {
    // ---- Score (section 7, figé) ----
    /** Seules ces matières entrent dans le deepwork (H6 : Info, Philo, Français n'ont pas de base). */
    val DEEPWORK_SUBJECTS: List<Subject> =
        listOf(Subject.MATHS, Subject.PHYSIQUE, Subject.SI, Subject.CHIMIE, Subject.ANGLAIS)

    val BASE: Map<Subject, Double> = mapOf(
        Subject.MATHS to 3.0,
        Subject.PHYSIQUE to 3.0,
        Subject.SI to 2.5,
        Subject.CHIMIE to 1.5,
        Subject.ANGLAIS to 1.5
    )

    /** (H1) Paliers de révision, comptés depuis la date de première vue : J0, J+3, J+7, J+16. */
    val REVIEW_OFFSETS_DAYS: List<Int> = listOf(0, 3, 7, 16)

    /** Seuil du deepwork : on propose si le meilleur score est strictement supérieur. */
    const val SCORE_THRESHOLD: Double = 0.0

    const val KHOLLE_WINDOW_DAYS: Int = 7
    const val KHOLLE_FORCE_MIN_DEEPWORK_MIN: Int = 150

    // ---- Durées (sections 6, 8, 9, 10, 11) ----
    const val BUFFER_MIN: Int = 5
    const val MIN_SESSION_MIN: Int = 10
    const val DEEPWORK_MICRO_MAX_MIN: Int = 45
    const val DEEPWORK_MACRO_MAX_MIN: Int = 90
    const val J0_MICRO_MAX_MIN: Int = 40
    const val J0_MIN_AVAILABLE_MIN: Int = 30
    const val EXTERNAL_MARGIN_MIN: Int = 45
    const val CLOTURE_MIN: Int = 15
    const val CLOTURE_LEAD_MIN: Int = 30
    const val SPORT_MIN: Int = 40
    const val SPORT_MAX_PER_WEEK: Int = 3
    const val CARDIO_MIN: Int = 35 // (H) milieu de « 30-40 min »
    const val PAUSE_THRESHOLD_MIN: Int = 60
    const val PAUSE_SHORT_MIN: Int = 15
    const val PAUSE_LONG_MIN: Int = 20
    const val PAUSE_SHORT_MAX_CUMUL_MIN: Int = 90 // 90 pile = pause de 15 min

    /** Une proposition non validée expire après 15 min. */
    const val PROPOSAL_EXPIRY_MIN: Int = 15

    // ---- Journée (décisions déjà prises) ----
    val WAKE: LocalTime = LocalTime.of(7, 0)
    val BEDTIME: LocalTime = LocalTime.of(22, 30)
    val LUNCH_START: LocalTime = LocalTime.of(12, 0)
    val LUNCH_END: LocalTime = LocalTime.of(14, 0)
    val DINNER_START: LocalTime = LocalTime.of(19, 0)
    val DINNER_END: LocalTime = LocalTime.of(20, 0)
    val CARDIO_PROPOSAL_TIME: LocalTime = LocalTime.of(10, 0)
}

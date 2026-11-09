package pcsi.engine

import java.time.LocalDate

/** Données statiques importées une fois par semestre (fichier data.json). */
data class StaticData(
    val semester: Int,
    val weekAReference: LocalDate,
    val daysOff: List<DateSpan>,
    val slots: List<Slot>,
    val colles: List<Colle>,
    val ds: List<Ds>
)

/**
 * Résultat d'un import. `data` n'existe que s'il n'y a aucune erreur.
 * Les avertissements n'empêchent pas l'import (clé inconnue, chevauchement, colle un jour de congé...).
 */
data class ImportResult(
    val data: StaticData?,
    val errors: List<String>,
    val warnings: List<String>
) {
    val ok: Boolean get() = data != null && errors.isEmpty()
}

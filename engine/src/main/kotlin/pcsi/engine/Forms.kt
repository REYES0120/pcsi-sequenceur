package pcsi.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

sealed class FormResult {
    data class Ok(val entry: Entry) : FormResult()
    data class Err(val message: String) : FormResult()
}

/**
 * Validation et nettoyage des saisies, côté moteur pour être testées sans Android.
 * Une saisie valide devient une entrée du journal ; sinon un message d'erreur en français.
 */
object Forms {
    const val MAX_NAME: Int = 80
    const val MAX_CLASS_SIZE: Int = 300

    /** Espaces de début et de fin retirés, espaces multiples réduits à un seul. */
    fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /** Seules les matières du deepwork ont des chapitres, des difficultés, des priorités et des rangs. */
    private fun deepworkOnly(subject: Subject): FormResult.Err? =
        if (Params.DEEPWORK_SUBJECTS.contains(subject)) {
            null
        } else {
            FormResult.Err("${Texts.subject(subject)} n'a pas de deepwork : rien à saisir ici.")
        }

    fun chapterSeen(state: State?, subject: Subject, rawName: String, seen: LocalDate, now: LocalDateTime): FormResult {
        deepworkOnly(subject)?.let { return it }
        val name = normalizeName(rawName)
        if (name.isEmpty()) return FormResult.Err("Le nom du chapitre est vide.")
        if (name.length > MAX_NAME) return FormResult.Err("Nom trop long ($MAX_NAME caractères au maximum).")
        if (seen.isAfter(now.toLocalDate())) return FormResult.Err("La date de vue est dans le futur.")
        if (state != null && state.chapters.any { it.subject == subject && it.name.equals(name, ignoreCase = true) }) {
            return FormResult.Err("Chapitre déjà saisi en ${Texts.subject(subject)}.")
        }
        return FormResult.Ok(Entry(now, Payload.ChapterSeen(Chapter(subject, name, seen))))
    }

    fun difficulty(subject: Subject, now: LocalDateTime): FormResult {
        deepworkOnly(subject)?.let { return it }
        return FormResult.Ok(Entry(now, Payload.Difficulty(DifficultyDecl(subject, now))))
    }

    /** Un ensemble vide veut dire « pas de matière forcément prioritaire ». */
    fun priority(subjects: Set<Subject>, now: LocalDateTime): FormResult {
        for (s in subjects) deepworkOnly(s)?.let { return it }
        return FormResult.Ok(Entry(now, Payload.Priority(PriorityDecl(subjects, now))))
    }

    fun rank(subject: Subject, rankRaw: String, sizeRaw: String, date: LocalDate, now: LocalDateTime): FormResult {
        deepworkOnly(subject)?.let { return it }
        val rank = rankRaw.trim().toIntOrNull() ?: return FormResult.Err("Rang : un nombre entier est attendu.")
        val size = sizeRaw.trim().toIntOrNull() ?: return FormResult.Err("Effectif : un nombre entier est attendu.")
        if (rank < 1) return FormResult.Err("Le rang commence à 1.")
        if (size < 1 || size > MAX_CLASS_SIZE) return FormResult.Err("Effectif invalide.")
        if (rank > size) return FormResult.Err("Le rang ne peut pas dépasser l'effectif.")
        if (date.isAfter(now.toLocalDate())) return FormResult.Err("La date du DS est dans le futur.")
        return FormResult.Ok(Entry(now, Payload.RankEntry(Rank(subject, date, rank, size))))
    }

    /** Dernier effectif saisi, pour préremplir le champ. */
    fun lastRankSize(state: State): Int? = state.ranks.maxByOrNull { it.date }?.size

    fun dmAdd(
        subject: Subject,
        due: LocalDate,
        now: LocalDateTime,
        id: String = "dm-" + UUID.randomUUID().toString().take(8)
    ): FormResult {
        if (due.isBefore(now.toLocalDate())) return FormResult.Err("La date de rendu est déjà passée.")
        return FormResult.Ok(Entry(now, Payload.DmAdded(Dm(id, subject, due))))
    }

    /** DM pas encore terminés, le plus proche d'abord. */
    fun openDms(state: State): List<Dm> = state.dms.filter { !state.dmDone.contains(it.id) }.sortedBy { it.due }

    fun dmDone(state: State, dmId: String, now: LocalDateTime): FormResult {
        if (state.dms.none { it.id == dmId }) return FormResult.Err("DM inconnu.")
        if (state.dmDone.contains(dmId)) return FormResult.Err("DM déjà marqué terminé.")
        return FormResult.Ok(Entry(now, Payload.DmDone(dmId)))
    }

    /** Annule une saisie encore valable (jamais une annulation). */
    fun retract(entries: List<Entry>, targetId: String, now: LocalDateTime): FormResult {
        if (StateBuilder.activeEntries(entries).none { it.id == targetId }) {
            return FormResult.Err("Cette saisie n'existe plus ou est déjà annulée.")
        }
        return FormResult.Ok(Entry(now, Payload.Retract(targetId)))
    }
}

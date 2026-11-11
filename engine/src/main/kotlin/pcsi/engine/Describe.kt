package pcsi.engine

import java.time.format.DateTimeFormatter

/** Une ligne lisible par entrée du journal, pour choisir laquelle annuler. */
object Describe {
    private val moment: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    private val day: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun entry(e: Entry, all: List<Entry>): String {
        return when (val p = e.payload) {
            is Payload.Session -> {
                val r = p.record
                val what = Texts.kindLabel(r.kind, r.subject)
                val outcome = when (r.outcome) {
                    Outcome.DONE -> "${r.minutes} min"
                    Outcome.SKIPPED -> "skip"
                    Outcome.DECLINED -> "refusé"
                }
                val forDay = r.forDay
                val note = if (forDay != null && forDay != r.start.toLocalDate()) " (cours du ${forDay.format(day)})" else ""
                "${r.start.format(moment)} · $what — $outcome$note"
            }
            is Payload.ChapterSeen ->
                "Chapitre vu : ${Texts.subject(p.chapter.subject)} — ${p.chapter.name} (le ${p.chapter.seen.format(day)})"
            is Payload.Difficulty ->
                "Difficulté : ${Texts.subject(p.decl.subject)} (${p.decl.at.format(moment)})"
            is Payload.Priority ->
                if (p.decl.subjects.isEmpty()) {
                    "Priorités : aucune"
                } else {
                    "Priorités : " + p.decl.subjects.joinToString(", ") { Texts.subject(it) }
                }
            is Payload.RankEntry ->
                "Rang ${Texts.subject(p.rank.subject)} : ${p.rank.rank}/${p.rank.size} (DS du ${p.rank.date.format(day)})"
            is Payload.DmAdded ->
                "DM ${Texts.subject(p.dm.subject)} — rendu le ${p.dm.due.format(day)}"
            is Payload.DmDone -> {
                val dm = all.map { it.payload }.filterIsInstance<Payload.DmAdded>().firstOrNull { it.dm.id == p.dmId }
                if (dm == null) "DM terminé : ${p.dmId}" else "DM terminé : ${Texts.subject(dm.dm.subject)} (rendu le ${dm.dm.due.format(day)})"
            }
            is Payload.Retract -> "Annulation"
        }
    }

    /** Les saisies encore valables, les plus récentes d'abord. */
    fun recent(entries: List<Entry>, limit: Int): List<Entry> =
        StateBuilder.activeEntries(entries).sortedByDescending { it.at }.take(limit)
}

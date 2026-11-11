package pcsi.engine

/**
 * Assemble l'état du moteur à partir des données statiques et du journal.
 * Aucun plan n'est lu ni écrit : tout se déduit du fait, du déclaré et du saisi.
 */
object StateBuilder {

    /**
     * Les entrées encore valables : sans les Retract, et sans les entrées qu'un Retract annule.
     * Un Retract ne peut pas être annulé lui-même (un Retract qui vise un autre Retract est sans effet).
     */
    fun activeEntries(entries: List<Entry>): List<Entry> {
        val retractEntryIds = HashSet<String>()
        for (e in entries) {
            if (e.payload is Payload.Retract) retractEntryIds.add(e.id)
        }
        val retracted = HashSet<String>()
        for (e in entries) {
            val p = e.payload
            if (p is Payload.Retract && !retractEntryIds.contains(p.targetId)) retracted.add(p.targetId)
        }
        return entries.filter { it.payload !is Payload.Retract && !retracted.contains(it.id) }
    }

    fun build(data: StaticData, entries: List<Entry>): State {
        val sessions = ArrayList<SessionRecord>()
        val chapters = LinkedHashMap<String, Chapter>()
        val difficulty = ArrayList<DifficultyDecl>()
        val priority = ArrayList<PriorityDecl>()
        val ranks = ArrayList<Rank>()
        val dms = LinkedHashMap<String, Dm>()
        val dmDone = LinkedHashSet<String>()

        for (e in activeEntries(entries)) {
            when (val p = e.payload) {
                is Payload.Session -> sessions.add(p.record)
                is Payload.ChapterSeen -> {
                    val key = p.chapter.subject.name + "|" + p.chapter.name.lowercase()
                    if (!chapters.containsKey(key)) chapters[key] = p.chapter
                }
                is Payload.Difficulty -> difficulty.add(p.decl)
                is Payload.Priority -> priority.add(p.decl)
                is Payload.RankEntry -> ranks.add(p.rank)
                is Payload.DmAdded -> dms[p.dm.id] = p.dm
                is Payload.DmDone -> dmDone.add(p.dmId)
                is Payload.Retract -> {}
            }
        }

        return State(
            semester = data.semester,
            weekAReference = data.weekAReference,
            daysOff = data.daysOff,
            slots = data.slots,
            colles = data.colles,
            ds = data.ds,
            chapters = chapters.values.toList(),
            ranks = ranks,
            difficulty = difficulty,
            priority = priority,
            sessions = sessions.sortedBy { it.start },
            dms = dms.values.toList(),
            dmDone = dmDone
        )
    }
}

package pcsi.app

import android.content.Context
import pcsi.engine.ActiveSession
import pcsi.engine.DataStore
import pcsi.engine.Entry
import pcsi.engine.JournalStore
import pcsi.engine.Payload
import pcsi.engine.Persist
import pcsi.engine.Proposal
import pcsi.engine.SessionRecord
import pcsi.engine.State
import pcsi.engine.StateBuilder
import java.io.File
import java.time.LocalDateTime

/** Tout ce que le téléphone stocke : data.json, journal.jsonl, une proposition, une session en cours. */
object Repo {
    private const val PREFS = "pcsi_state"
    private const val KEY_PROPOSAL = "proposal"
    private const val KEY_ACTIVE = "active"

    fun dataStore(ctx: Context): DataStore = DataStore(File(ctx.filesDir, "data.json"))

    fun journal(ctx: Context): JournalStore = JournalStore(File(ctx.filesDir, "journal.jsonl"))

    /** Null tant qu'aucun data.json valide n'a été importé. */
    fun loadState(ctx: Context): State? {
        val result = dataStore(ctx).load() ?: return null
        val data = result.data ?: return null
        return StateBuilder.build(data, journal(ctx).readAll().entries)
    }

    /** Ajoute une saisie (chapitre, difficulté, rang, DM, annulation...) au journal. */
    fun append(ctx: Context, entry: Entry) {
        journal(ctx).append(entry)
    }

    fun record(ctx: Context, record: SessionRecord, now: LocalDateTime) {
        journal(ctx).append(Entry(now, Payload.Session(record)))
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun proposal(ctx: Context): Proposal? {
        val text = prefs(ctx).getString(KEY_PROPOSAL, null) ?: return null
        return Persist.decodeProposal(text)
    }

    fun saveProposal(ctx: Context, p: Proposal?) {
        val e = prefs(ctx).edit()
        if (p == null) e.remove(KEY_PROPOSAL) else e.putString(KEY_PROPOSAL, Persist.encodeProposal(p))
        e.commit()
    }

    fun active(ctx: Context): ActiveSession? {
        val text = prefs(ctx).getString(KEY_ACTIVE, null) ?: return null
        return Persist.decodeActive(text)
    }

    fun saveActive(ctx: Context, s: ActiveSession?) {
        val e = prefs(ctx).edit()
        if (s == null) e.remove(KEY_ACTIVE) else e.putString(KEY_ACTIVE, Persist.encodeActive(s))
        e.commit()
    }
}

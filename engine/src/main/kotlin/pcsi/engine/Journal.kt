package pcsi.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Ce qui a été enregistré : fait, déclaré ou saisi. Rien d'autre n'est mémorisé. */
sealed class Payload {
    data class Session(val record: SessionRecord) : Payload()
    data class ChapterSeen(val chapter: Chapter) : Payload()
    data class Difficulty(val decl: DifficultyDecl) : Payload()
    data class Priority(val decl: PriorityDecl) : Payload()
    data class RankEntry(val rank: Rank) : Payload()
    data class DmAdded(val dm: Dm) : Payload()
    data class DmDone(val dmId: String) : Payload()

    /** Annule une entrée précédente (saisie erronée, session lancée par erreur). */
    data class Retract(val targetId: String) : Payload()
}

/** Une ligne du journal : instant de saisie, contenu, identifiant unique. */
data class Entry(
    val at: LocalDateTime,
    val payload: Payload,
    val id: String = UUID.randomUUID().toString()
)

data class DecodeResult(val entries: List<Entry>, val skipped: List<String>)

private fun JsonObject.s(key: String): String? {
    val e = this[key]
    return if (e is JsonPrimitive && e.isString) e.content else null
}

private fun JsonObject.i(key: String): Int? {
    val e = this[key]
    return if (e is JsonPrimitive && !e.isString) e.intOrNull else null
}

private inline fun <reified T : Enum<T>> enumOf(name: String?): T? =
    if (name == null) null else enumValues<T>().firstOrNull { it.name == name }

private fun parseObj(line: String): JsonObject? =
    try {
        Json.parseToJsonElement(line) as? JsonObject
    } catch (ex: Exception) {
        null
    }

/** Format JSON Lines : une entrée par ligne, ajoutée à la fin du fichier, jamais modifiée. */
object Journal {

    fun encode(e: Entry): String {
        val o = buildJsonObject {
            put("id", e.id)
            put("at", e.at.toString())
            when (val p = e.payload) {
                is Payload.Session -> {
                    put("type", "session")
                    put("kind", p.record.kind.name)
                    put("outcome", p.record.outcome.name)
                    put("start", p.record.start.toString())
                    put("minutes", p.record.minutes)
                    put("subject", p.record.subject?.name)
                    put("chapter", p.record.chapter)
                    put("forDay", p.record.forDay?.toString())
                }
                is Payload.ChapterSeen -> {
                    put("type", "chapter")
                    put("subject", p.chapter.subject.name)
                    put("name", p.chapter.name)
                    put("seen", p.chapter.seen.toString())
                }
                is Payload.Difficulty -> {
                    put("type", "difficulty")
                    put("subject", p.decl.subject.name)
                    put("declaredAt", p.decl.at.toString())
                }
                is Payload.Priority -> {
                    put("type", "priority")
                    put("declaredAt", p.decl.at.toString())
                    putJsonArray("subjects") {
                        for (s in p.decl.subjects) add(s.name)
                    }
                }
                is Payload.RankEntry -> {
                    put("type", "rank")
                    put("subject", p.rank.subject.name)
                    put("date", p.rank.date.toString())
                    put("rank", p.rank.rank)
                    put("size", p.rank.size)
                }
                is Payload.DmAdded -> {
                    put("type", "dm_added")
                    put("dmId", p.dm.id)
                    put("subject", p.dm.subject.name)
                    put("due", p.dm.due.toString())
                }
                is Payload.DmDone -> {
                    put("type", "dm_done")
                    put("dmId", p.dmId)
                }
                is Payload.Retract -> {
                    put("type", "retract")
                    put("target", p.targetId)
                }
            }
        }
        return o.toString()
    }

    /** Null si la ligne est illisible ou incomplète : le journal ne plante jamais pour une ligne. */
    fun decode(line: String): Entry? {
        try {
            val o = parseObj(line) ?: return null
            val id = o.s("id") ?: return null
            val at = LocalDateTime.parse(o.s("at") ?: return null)
            val payload: Payload = when (o.s("type")) {
                "session" -> {
                    val kind = enumOf<SessionKind>(o.s("kind")) ?: return null
                    val outcome = enumOf<Outcome>(o.s("outcome")) ?: return null
                    val start = LocalDateTime.parse(o.s("start") ?: return null)
                    val minutes = o.i("minutes") ?: return null
                    val subjectName = o.s("subject")
                    val subject: Subject? =
                        if (subjectName == null) null else (enumOf<Subject>(subjectName) ?: return null)
                    val forDayRaw = o.s("forDay")
                    val forDay: LocalDate? = if (forDayRaw == null) null else LocalDate.parse(forDayRaw)
                    Payload.Session(SessionRecord(kind, outcome, start, minutes, subject, o.s("chapter"), forDay))
                }
                "chapter" -> {
                    val subject = enumOf<Subject>(o.s("subject")) ?: return null
                    val name = o.s("name") ?: return null
                    val seen = LocalDate.parse(o.s("seen") ?: return null)
                    Payload.ChapterSeen(Chapter(subject, name, seen))
                }
                "difficulty" -> {
                    val subject = enumOf<Subject>(o.s("subject")) ?: return null
                    val declaredAt = LocalDateTime.parse(o.s("declaredAt") ?: return null)
                    Payload.Difficulty(DifficultyDecl(subject, declaredAt))
                }
                "priority" -> {
                    val declaredAt = LocalDateTime.parse(o.s("declaredAt") ?: return null)
                    val arr = o["subjects"] as? JsonArray ?: return null
                    val subjects = LinkedHashSet<Subject>()
                    for (item in arr) {
                        val name = if (item is JsonPrimitive && item.isString) item.content else return null
                        subjects.add(enumOf<Subject>(name) ?: return null)
                    }
                    Payload.Priority(PriorityDecl(subjects, declaredAt))
                }
                "rank" -> {
                    val subject = enumOf<Subject>(o.s("subject")) ?: return null
                    val date = LocalDate.parse(o.s("date") ?: return null)
                    val rank = o.i("rank") ?: return null
                    val size = o.i("size") ?: return null
                    Payload.RankEntry(Rank(subject, date, rank, size))
                }
                "dm_added" -> {
                    val dmId = o.s("dmId") ?: return null
                    val subject = enumOf<Subject>(o.s("subject")) ?: return null
                    val due = LocalDate.parse(o.s("due") ?: return null)
                    Payload.DmAdded(Dm(dmId, subject, due))
                }
                "dm_done" -> Payload.DmDone(o.s("dmId") ?: return null)
                "retract" -> Payload.Retract(o.s("target") ?: return null)
                else -> return null
            }
            return Entry(at, payload, id)
        } catch (ex: Exception) {
            return null
        }
    }

    fun decodeAll(lines: List<String>): DecodeResult {
        val out = ArrayList<Entry>()
        val bad = ArrayList<String>()
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isNotEmpty()) {
                val e = decode(line)
                if (e == null) bad.add("ligne ${i + 1} ignorée") else out.add(e)
            }
        }
        return DecodeResult(out, bad)
    }
}

/** Fichier journal sur le téléphone (ajout uniquement, écriture forcée sur disque). */
class JournalStore(private val file: File) {

    private fun endsWithNewline(): Boolean {
        if (!file.exists() || file.length() == 0L) return true
        val last = RandomAccessFile(file, "r").use { r ->
            r.seek(file.length() - 1)
            r.read()
        }
        return last == '\n'.code
    }

    /** Si une écriture précédente a été coupée net, on referme d'abord sa ligne pour ne pas abîmer la suivante. */
    fun append(entry: Entry) {
        file.parentFile?.mkdirs()
        val prefix = if (endsWithNewline()) "" else "\n"
        FileOutputStream(file, true).use { out ->
            out.write((prefix + Journal.encode(entry) + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
    }

    fun readAll(): DecodeResult {
        if (!file.exists()) return DecodeResult(emptyList(), emptyList())
        return Journal.decodeAll(file.readLines(Charsets.UTF_8))
    }
}

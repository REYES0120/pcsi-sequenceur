package pcsi.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sérialisation de la proposition courante et de la session en cours, pour que le téléphone
 * les retrouve après que le système a arrêté le processus. Ce n'est pas un plan : une seule
 * proposition et une seule session, jamais plus.
 */
object Persist {

    fun encodeActive(s: ActiveSession): String = buildJsonObject {
        put("kind", s.kind.name)
        put("subject", s.subject?.name)
        put("chapter", s.chapter)
        put("forDay", s.forDay?.toString())
        put("startedAt", s.startedAt.toString())
        put("plannedMinutes", s.plannedMinutes)
        put("overrun", s.overrun)
    }.toString()

    fun decodeActive(text: String): ActiveSession? {
        try {
            val o = parseObj(text) ?: return null
            val kind = enumOf<SessionKind>(o.s("kind")) ?: return null
            val subjectName = o.s("subject")
            val subject: Subject? = if (subjectName == null) null else (enumOf<Subject>(subjectName) ?: return null)
            val forDayRaw = o.s("forDay")
            val forDay: LocalDate? = if (forDayRaw == null) null else LocalDate.parse(forDayRaw)
            val startedAt = LocalDateTime.parse(o.s("startedAt") ?: return null)
            val planned = o.i("plannedMinutes") ?: return null
            val overrun = (o["overrun"] as? JsonPrimitive)?.content == "true"
            return ActiveSession(kind, subject, o.s("chapter"), forDay, startedAt, planned, overrun)
        } catch (ex: Exception) {
            return null
        }
    }

    /** Une proposition « rien à proposer » n'est jamais stockée. */
    fun encodeProposal(p: Proposal): String {
        require(p.action !is Action.None) { "Une absence de proposition ne se stocke pas" }
        return buildJsonObject {
            put("id", p.id)
            put("createdAt", p.createdAt.toString())
            when (val a = p.action) {
                is Action.Cloture -> {
                    put("type", "cloture")
                    put("minutes", a.minutes)
                }
                is Action.Pause -> {
                    put("type", "pause")
                    put("minutes", a.minutes)
                }
                is Action.Sport -> {
                    put("type", "sport")
                    put("minutes", a.minutes)
                }
                is Action.Cardio -> {
                    put("type", "cardio")
                    put("minutes", a.minutes)
                }
                is Action.J0Macro -> {
                    put("type", "j0_macro")
                    put("minutes", a.minutesAvailable)
                    put(
                        "pending",
                        buildJsonArray {
                            for (item in a.pending) {
                                add(
                                    buildJsonObject {
                                        put("subject", item.subject.name)
                                        put("forDay", item.forDay.toString())
                                    }
                                )
                            }
                        }
                    )
                }
                is Action.J0Micro -> {
                    put("type", "j0_micro")
                    put("minutes", a.minutes)
                    put("subject", a.item.subject.name)
                    put("forDay", a.item.forDay.toString())
                }
                is Action.Deepwork -> {
                    put("type", "deepwork")
                    put("subject", a.subject.name)
                    put("chapter", a.chapter)
                    put("micro", a.microMinutes)
                    put("macro", a.macroMinutes)
                    put("deepKind", a.kind.name)
                }
                is Action.None -> {}
            }
        }.toString()
    }

    private fun item(o: JsonObject): J0Item? {
        val subject = enumOf<Subject>(o.s("subject")) ?: return null
        val forDay = LocalDate.parse(o.s("forDay") ?: return null)
        return J0Item(subject, forDay)
    }

    fun decodeProposal(text: String): Proposal? {
        try {
            val o = parseObj(text) ?: return null
            val id = o.s("id") ?: return null
            val createdAt = LocalDateTime.parse(o.s("createdAt") ?: return null)
            val action: Action = when (o.s("type")) {
                "cloture" -> Action.Cloture(o.i("minutes") ?: return null)
                "pause" -> Action.Pause(o.i("minutes") ?: return null)
                "sport" -> Action.Sport(o.i("minutes") ?: return null)
                "cardio" -> Action.Cardio(o.i("minutes") ?: return null)
                "j0_macro" -> {
                    val arr = o["pending"] as? JsonArray ?: return null
                    val items = ArrayList<J0Item>()
                    for (e in arr) {
                        val obj = e as? JsonObject ?: return null
                        items.add(item(obj) ?: return null)
                    }
                    Action.J0Macro(items, o.i("minutes") ?: return null)
                }
                "j0_micro" -> Action.J0Micro(item(o) ?: return null, o.i("minutes") ?: return null)
                "deepwork" -> {
                    val subject = enumOf<Subject>(o.s("subject")) ?: return null
                    val kind = enumOf<DeepKind>(o.s("deepKind")) ?: return null
                    Action.Deepwork(subject, o.s("chapter"), o.i("micro") ?: return null, o.i("macro") ?: return null, kind)
                }
                else -> return null
            }
            return Proposal(id, action, createdAt)
        } catch (ex: Exception) {
            return null
        }
    }
}

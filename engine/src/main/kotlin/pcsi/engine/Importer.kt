package pcsi.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

private class Ctx {
    val errors = ArrayList<String>()
    val warnings = ArrayList<String>()
}

private fun join(path: String, key: String): String = if (path.isEmpty()) key else "$path.$key"

/**
 * Lit et valide data.json. Toutes les erreurs sont collectées (pas seulement la première),
 * chacune avec son chemin, par exemple « slots[3].start : heure invalide ».
 */
object Importer {

    private val DAYS: Map<String, DayOfWeek> = mapOf(
        "MON" to DayOfWeek.MONDAY, "LUN" to DayOfWeek.MONDAY,
        "TUE" to DayOfWeek.TUESDAY, "MAR" to DayOfWeek.TUESDAY,
        "WED" to DayOfWeek.WEDNESDAY, "MER" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY, "JEU" to DayOfWeek.THURSDAY,
        "FRI" to DayOfWeek.FRIDAY, "VEN" to DayOfWeek.FRIDAY,
        "SAT" to DayOfWeek.SATURDAY, "SAM" to DayOfWeek.SATURDAY,
        "SUN" to DayOfWeek.SUNDAY, "DIM" to DayOfWeek.SUNDAY
    )

    fun parse(text: String): ImportResult {
        val ctx = Ctx()
        val root: JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (ex: Exception) {
            return ImportResult(null, listOf("JSON invalide : " + (ex.message ?: "erreur de lecture")), emptyList())
        }
        if (root !is JsonObject) {
            return ImportResult(null, listOf("La racine du fichier doit être un objet { ... }"), emptyList())
        }
        checkKeys(ctx, "", root, setOf("version", "semester", "weekA", "daysOff", "slots", "colles", "ds"))

        val version = intField(ctx, root, "", "version", true)
        if (version != null && version != 1) ctx.errors.add("version : $version non prise en charge (1 attendue)")
        val semester = intField(ctx, root, "", "semester", true)
        if (semester != null && semester != 1 && semester != 2) ctx.errors.add("semester : 1 ou 2 attendu")
        val weekA = dateField(ctx, root, "", "weekA", true)
        if (weekA != null && weekA.dayOfWeek != DayOfWeek.MONDAY) {
            ctx.warnings.add("weekA : $weekA n'est pas un lundi ; la semaine A sera la semaine de ce jour")
        }

        val daysOff = ArrayList<DateSpan>()
        objectArray(ctx, root, "", "daysOff", false)?.forEachIndexed { i, o ->
            val p = "daysOff[$i]"
            checkKeys(ctx, p, o, setOf("from", "to", "label"))
            val from = dateField(ctx, o, p, "from", true)
            val to = dateField(ctx, o, p, "to", true)
            if (from != null && to != null) {
                if (to.isBefore(from)) ctx.errors.add("$p : « to » est avant « from »") else daysOff.add(DateSpan(from, to))
            }
        }

        val slots = ArrayList<Slot>()
        objectArray(ctx, root, "", "slots", true)?.forEachIndexed { i, o ->
            val p = "slots[$i]"
            checkKeys(ctx, p, o, setOf("day", "start", "end", "subject", "type", "week", "semester"))
            val dayRaw = text(ctx, o, p, "day", true)
            val day = if (dayRaw == null) null else dayOf(ctx, p, dayRaw)
            val start = timeField(ctx, o, p, "start")
            val end = timeField(ctx, o, p, "end")
            val typeRaw = text(ctx, o, p, "type", false)
            val type = if (typeRaw == null) SlotType.COURS else slotTypeOf(ctx, p, typeRaw)
            val weekRaw = text(ctx, o, p, "week", false)
            val week = if (weekRaw == null) WeekKind.BOTH else weekOf(ctx, p, weekRaw)
            val slotSemester = intField(ctx, o, p, "semester", false) ?: (semester ?: 1)
            val subjectRaw = text(ctx, o, p, "subject", false)
            var subject: Subject? = null
            if (type == SlotType.EPS) {
                if (subjectRaw != null) ctx.warnings.add("${join(p, "subject")} : ignorée pour l'EPS")
            } else if (subjectRaw == null) {
                ctx.errors.add("${join(p, "subject")} : champ manquant (obligatoire sauf pour l'EPS)")
            } else {
                subject = subjectOf(ctx, p, "subject", subjectRaw)
            }
            if (day != null && start != null && end != null && type != null && week != null &&
                (type == SlotType.EPS || subject != null)
            ) {
                if (!start.isBefore(end)) {
                    ctx.errors.add("$p : l'heure de fin doit être après l'heure de début")
                } else {
                    slots.add(Slot(day, start, end, subject, type, week, slotSemester))
                }
            }
        }

        val colles = ArrayList<Colle>()
        objectArray(ctx, root, "", "colles", false)?.forEachIndexed { i, o ->
            val p = "colles[$i]"
            checkKeys(ctx, p, o, setOf("date", "start", "end", "subject", "colleur"))
            val date = dateField(ctx, o, p, "date", true)
            val start = timeField(ctx, o, p, "start")
            val end = timeField(ctx, o, p, "end")
            val subjectRaw = text(ctx, o, p, "subject", true)
            val subject = if (subjectRaw == null) null else subjectOf(ctx, p, "subject", subjectRaw)
            if (date != null && start != null && end != null && subject != null) {
                if (!start.isBefore(end)) {
                    ctx.errors.add("$p : l'heure de fin doit être après l'heure de début")
                } else {
                    colles.add(Colle(date, start, end, subject))
                }
            }
        }

        val dsList = ArrayList<Ds>()
        objectArray(ctx, root, "", "ds", false)?.forEachIndexed { i, o ->
            val p = "ds[$i]"
            checkKeys(ctx, p, o, setOf("date", "start", "end", "subjects"))
            val date = dateField(ctx, o, p, "date", true)
            val start = timeField(ctx, o, p, "start")
            val end = timeField(ctx, o, p, "end")
            val names = stringArray(ctx, o, p, "subjects")
            val subjects = ArrayList<Subject>()
            var subjectsOk = names != null
            if (names != null) {
                if (names.isEmpty()) {
                    ctx.errors.add("${join(p, "subjects")} : au moins une matière attendue")
                    subjectsOk = false
                }
                for (n in names) {
                    val s = subjectOf(ctx, p, "subjects", n)
                    if (s == null) subjectsOk = false else subjects.add(s)
                }
            }
            if (date != null && start != null && end != null && subjectsOk) {
                if (!start.isBefore(end)) {
                    ctx.errors.add("$p : l'heure de fin doit être après l'heure de début")
                } else {
                    dsList.add(Ds(date, start, end, subjects))
                }
            }
        }

        if (semester != null && weekA != null) crossCheck(ctx, semester, weekA, daysOff, slots, colles, dsList)

        if (ctx.errors.isEmpty() && semester != null && weekA != null) {
            return ImportResult(
                StaticData(semester, weekA, daysOff, slots, colles, dsList),
                emptyList(),
                ctx.warnings
            )
        }
        return ImportResult(null, ctx.errors, ctx.warnings)
    }

    // ---------- Contrôles croisés (avertissements) ----------

    private fun overlap(a1: LocalTime, a2: LocalTime, b1: LocalTime, b2: LocalTime): Boolean =
        a1.isBefore(b2) && b1.isBefore(a2)

    private fun weeksOverlap(a: WeekKind, b: WeekKind): Boolean =
        a == WeekKind.BOTH || b == WeekKind.BOTH || a == b

    private fun crossCheck(
        ctx: Ctx,
        semester: Int,
        weekA: LocalDate,
        daysOff: List<DateSpan>,
        slots: List<Slot>,
        colles: List<Colle>,
        ds: List<Ds>
    ) {
        for (i in slots.indices) {
            for (j in (i + 1) until slots.size) {
                val a = slots[i]
                val b = slots[j]
                if (a.semester == b.semester && a.day == b.day && weeksOverlap(a.week, b.week) &&
                    overlap(a.start, a.end, b.start, b.end)
                ) {
                    ctx.warnings.add(
                        "Chevauchement de cours le ${a.day} : ${a.subject ?: "EPS"} ${a.start}-${a.end} et ${b.subject ?: "EPS"} ${b.start}-${b.end}"
                    )
                }
            }
        }
        val tmp = emptyState(weekA).copy(semester = semester, daysOff = daysOff, slots = slots)
        for (c in colles) {
            if (daysOff.any { it.contains(c.date) }) ctx.warnings.add("Colle ${c.subject} du ${c.date} un jour de congé")
            for (s in Constraints.classesOn(tmp, c.date)) {
                if (overlap(c.start, c.end, s.start, s.end)) {
                    ctx.warnings.add("Colle ${c.subject} du ${c.date} ${c.start}-${c.end} pendant un cours (${s.subject ?: "EPS"})")
                }
            }
        }
        for (d in ds) {
            if (daysOff.any { it.contains(d.date) }) ctx.warnings.add("DS du ${d.date} un jour de congé")
            for (c in colles) {
                if (c.date == d.date && overlap(c.start, c.end, d.start, d.end)) {
                    ctx.warnings.add("Colle ${c.subject} et DS se chevauchent le ${d.date}")
                }
            }
        }
    }

    // ---------- Lecture des champs ----------

    private fun checkKeys(ctx: Ctx, path: String, obj: JsonObject, allowed: Set<String>) {
        for (k in obj.keys) {
            if (!allowed.contains(k)) ctx.warnings.add("${join(path, k)} : clé inconnue, ignorée")
        }
    }

    private fun field(obj: JsonObject, key: String): JsonElement? {
        val e = obj[key]
        return if (e == null || e is JsonNull) null else e
    }

    private fun text(ctx: Ctx, obj: JsonObject, path: String, key: String, required: Boolean): String? {
        val e = field(obj, key)
        if (e == null) {
            if (required) ctx.errors.add("${join(path, key)} : champ manquant")
            return null
        }
        if (e !is JsonPrimitive || !e.isString) {
            ctx.errors.add("${join(path, key)} : doit être un texte entre guillemets")
            return null
        }
        return e.content
    }

    private fun intField(ctx: Ctx, obj: JsonObject, path: String, key: String, required: Boolean): Int? {
        val e = field(obj, key)
        if (e == null) {
            if (required) ctx.errors.add("${join(path, key)} : champ manquant")
            return null
        }
        val n = if (e is JsonPrimitive && !e.isString) e.intOrNull else null
        if (n == null) ctx.errors.add("${join(path, key)} : doit être un nombre entier")
        return n
    }

    private fun dateField(ctx: Ctx, obj: JsonObject, path: String, key: String, required: Boolean): LocalDate? {
        val raw = text(ctx, obj, path, key, required) ?: return null
        return try {
            LocalDate.parse(raw)
        } catch (ex: DateTimeParseException) {
            ctx.errors.add("${join(path, key)} : date invalide « $raw » (format AAAA-MM-JJ)")
            null
        }
    }

    private fun timeField(ctx: Ctx, obj: JsonObject, path: String, key: String): LocalTime? {
        val raw = text(ctx, obj, path, key, true) ?: return null
        return try {
            LocalTime.parse(raw)
        } catch (ex: DateTimeParseException) {
            ctx.errors.add("${join(path, key)} : heure invalide « $raw » (format HH:MM, par exemple 08:05)")
            null
        }
    }

    private fun objectArray(ctx: Ctx, obj: JsonObject, path: String, key: String, required: Boolean): List<JsonObject>? {
        val e = field(obj, key)
        if (e == null) {
            if (required) ctx.errors.add("${join(path, key)} : champ manquant")
            return null
        }
        if (e !is JsonArray) {
            ctx.errors.add("${join(path, key)} : doit être une liste [ ... ]")
            return null
        }
        val out = ArrayList<JsonObject>()
        e.forEachIndexed { i, item ->
            if (item is JsonObject) out.add(item) else ctx.errors.add("${join(path, key)}[$i] : doit être un objet { ... }")
        }
        return out
    }

    private fun stringArray(ctx: Ctx, obj: JsonObject, path: String, key: String): List<String>? {
        val e = field(obj, key)
        if (e == null) {
            ctx.errors.add("${join(path, key)} : champ manquant")
            return null
        }
        if (e !is JsonArray) {
            ctx.errors.add("${join(path, key)} : doit être une liste de textes")
            return null
        }
        val out = ArrayList<String>()
        e.forEach { item ->
            if (item is JsonPrimitive && item.isString) out.add(item.content)
            else ctx.errors.add("${join(path, key)} : chaque élément doit être un texte")
        }
        return out
    }

    private fun dayOf(ctx: Ctx, path: String, raw: String): DayOfWeek? {
        val d = DAYS[raw.trim().uppercase().take(3)]
        if (d == null) ctx.errors.add("${join(path, "day")} : « $raw » inconnu (MON, TUE, WED, THU, FRI, SAT, SUN ou LUN, MAR, MER, JEU, VEN, SAM, DIM)")
        return d
    }

    private fun subjectOf(ctx: Ctx, path: String, key: String, raw: String): Subject? {
        val s = Subject.values().firstOrNull { it.name == raw.trim().uppercase() }
        if (s == null) {
            ctx.errors.add("${join(path, key)} : matière « $raw » inconnue (${Subject.values().joinToString(", ") { it.name }})")
        }
        return s
    }

    private fun slotTypeOf(ctx: Ctx, path: String, raw: String): SlotType? {
        val t = SlotType.values().firstOrNull { it.name == raw.trim().uppercase() }
        if (t == null) ctx.errors.add("${join(path, "type")} : « $raw » inconnu (COURS, TD, TP, EPS)")
        return t
    }

    private fun weekOf(ctx: Ctx, path: String, raw: String): WeekKind? {
        val w = WeekKind.values().firstOrNull { it.name == raw.trim().uppercase() }
        if (w == null) ctx.errors.add("${join(path, "week")} : « $raw » inconnu (A, B, BOTH)")
        return w
    }
}

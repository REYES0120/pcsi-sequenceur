package pcsi.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import pcsi.engine.Describe
import pcsi.engine.FormResult
import pcsi.engine.Forms
import pcsi.engine.Params
import pcsi.engine.Subject
import pcsi.engine.Texts
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saisies courtes : chapitre vu, difficulté, matières prioritaires, rang, DM, DM terminé, annulation.
 * Toute la validation est dans le moteur (Forms) ; cet écran ne fait que lire les champs.
 */
class FormsActivity : Activity() {

    private lateinit var content: LinearLayout
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Un bouton qui ouvre un sélecteur de date et garde la valeur choisie. */
    private class DateField(var value: LocalDate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(32, 32, 32, 32)
        scroll.addView(content)
        setContentView(scroll)
        menu()
    }

    // ---------- Briques d'écran ----------

    private fun reset() {
        content.removeAllViews()
    }

    private fun label(text: String, size: Float = 16f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setPadding(0, 12, 0, 12)
        content.addView(t)
        return t
    }

    private fun button(text: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener(View.OnClickListener { onClick() })
        content.addView(b)
    }

    private fun spinner(items: List<String>): Spinner {
        val s = Spinner(this)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        s.adapter = adapter
        content.addView(s)
        return s
    }

    private fun edit(hint: String, numeric: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setSingleLine(true)
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER
        content.addView(e)
        return e
    }

    private fun dateField(initial: LocalDate): DateField {
        val field = DateField(initial)
        val b = Button(this)
        b.text = field.value.format(dateFormat)
        b.setOnClickListener(View.OnClickListener {
            val cur = field.value
            DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { _, y, m, d ->
                    field.value = LocalDate.of(y, m + 1, d)
                    b.text = field.value.format(dateFormat)
                },
                cur.year, cur.monthValue - 1, cur.dayOfMonth
            ).show()
        })
        content.addView(b)
        return field
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    /** Enregistre si la saisie est valide (puis recalcul, car une donnée a changé), sinon montre l'erreur. */
    private fun save(result: FormResult, onError: (String) -> Unit) {
        when (result) {
            is FormResult.Ok -> {
                Repo.append(this, result.entry)
                Sequencer.recalc(this)
                toast("Enregistré.")
                menu()
            }
            is FormResult.Err -> onError(result.message)
        }
    }

    // ---------- Écrans ----------

    private fun menu() {
        reset()
        label("Saisies", 24f)
        button("Chapitre vu") { chapterForm() }
        button("Difficulté") { difficultyForm() }
        button("Matières prioritaires") { priorityForm() }
        button("Rang à un DS") { rankForm() }
        button("Ajouter un DM") { dmForm() }
        button("DM terminé") { dmDoneForm() }
        button("Annuler une saisie") { retractForm() }
        button("Retour") { finish() }
    }

    private fun chapterForm() {
        reset()
        label("Chapitre vu", 22f)
        val subjects = Params.DEEPWORK_SUBJECTS
        val subject = spinner(subjects.map { Texts.subject(it) })
        val name = edit("Nom du chapitre", false)
        label("Date de la première vue", 13f)
        val date = dateField(LocalDate.now())
        val err = label("", 14f)
        button("Enregistrer") {
            val r = Forms.chapterSeen(
                Repo.loadState(this@FormsActivity),
                subjects[subject.selectedItemPosition],
                name.text.toString(),
                date.value,
                LocalDateTime.now()
            )
            save(r) { err.text = it }
        }
        button("Retour") { menu() }
    }

    private fun difficultyForm() {
        reset()
        label("Matière difficile en ce moment (valable 7 jours)", 18f)
        for (s in Params.DEEPWORK_SUBJECTS) {
            button(Texts.subject(s)) {
                save(Forms.difficulty(s, LocalDateTime.now())) { toast(it) }
            }
        }
        button("Retour") { menu() }
    }

    private fun priorityForm() {
        reset()
        label("Matières prioritaires (valable 7 jours)", 18f)
        val subjects = Params.DEEPWORK_SUBJECTS
        val boxes = ArrayList<CheckBox>()
        for (s in subjects) {
            val box = CheckBox(this)
            box.text = Texts.subject(s)
            content.addView(box)
            boxes.add(box)
        }
        val err = label("", 14f)
        button("Enregistrer la sélection") {
            val chosen = HashSet<Subject>()
            for (i in subjects.indices) {
                if (boxes[i].isChecked) chosen.add(subjects[i])
            }
            save(Forms.priority(chosen, LocalDateTime.now())) { err.text = it }
        }
        button("Aucune matière prioritaire") {
            save(Forms.priority(emptySet(), LocalDateTime.now())) { err.text = it }
        }
        button("Retour") { menu() }
    }

    private fun rankForm() {
        reset()
        label("Rang à un DS", 22f)
        val subjects = Params.DEEPWORK_SUBJECTS
        val subject = spinner(subjects.map { Texts.subject(it) })
        val rank = edit("Rang", true)
        val size = edit("Effectif de la classe", true)
        val state = Repo.loadState(this)
        val last = if (state == null) null else Forms.lastRankSize(state)
        if (last != null) size.setText(last.toString())
        label("Date du DS", 13f)
        val date = dateField(LocalDate.now())
        val err = label("", 14f)
        button("Enregistrer") {
            val r = Forms.rank(
                subjects[subject.selectedItemPosition],
                rank.text.toString(),
                size.text.toString(),
                date.value,
                LocalDateTime.now()
            )
            save(r) { err.text = it }
        }
        button("Retour") { menu() }
    }

    private fun dmForm() {
        reset()
        label("Ajouter un DM", 22f)
        val subjects = Subject.values().toList()
        val subject = spinner(subjects.map { Texts.subject(it) })
        label("Date de rendu", 13f)
        val date = dateField(LocalDate.now().plusDays(7))
        val err = label("", 14f)
        button("Enregistrer") {
            val r = Forms.dmAdd(subjects[subject.selectedItemPosition], date.value, LocalDateTime.now())
            save(r) { err.text = it }
        }
        button("Retour") { menu() }
    }

    private fun dmDoneForm() {
        reset()
        label("DM terminé", 22f)
        val state = Repo.loadState(this)
        if (state == null) {
            label("Importe d'abord data.json.")
        } else {
            val open = Forms.openDms(state)
            if (open.isEmpty()) label("Aucun DM en cours.")
            for (dm in open) {
                label("DM ${Texts.subject(dm.subject)} — rendu le ${dm.due.format(dateFormat)}", 16f)
                button("Terminé") {
                    save(Forms.dmDone(state, dm.id, LocalDateTime.now())) { toast(it) }
                }
            }
        }
        button("Retour") { menu() }
    }

    private fun retractForm() {
        reset()
        label("Annuler une saisie", 22f)
        val entries = Repo.journal(this).readAll().entries
        val recent = Describe.recent(entries, 20)
        if (recent.isEmpty()) label("Rien à annuler.")
        for (e in recent) {
            val text = Describe.entry(e, entries)
            label(text, 14f)
            button("Annuler cette saisie") {
                AlertDialog.Builder(this@FormsActivity)
                    .setMessage("Annuler : $text ?")
                    .setPositiveButton("Annuler la saisie", DialogInterface.OnClickListener { _, _ ->
                        save(Forms.retract(entries, e.id, LocalDateTime.now())) { toast(it) }
                    })
                    .setNegativeButton("Non", null)
                    .show()
            }
        }
        button("Retour") { menu() }
    }
}

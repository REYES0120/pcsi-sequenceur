package pcsi.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import pcsi.engine.Action
import pcsi.engine.FinishMode
import pcsi.engine.J0
import pcsi.engine.Lifecycle
import pcsi.engine.Reason
import pcsi.engine.Texts
import java.time.LocalDateTime

/**
 * Écran minimal : la proposition en cours, le choix des matières du J0, « Que faire ? »,
 * l'import de data.json et l'accès aux réglages système. Les formulaires viennent à l'étape suivante.
 */
class MainActivity : Activity() {

    private lateinit var content: LinearLayout
    private var message: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(32, 32, 32, 32)
        scroll.addView(content)
        setContentView(scroll)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Sequencer.recalc(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun label(text: String, size: Float = 16f) {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setPadding(0, 12, 0, 12)
        content.addView(t)
    }

    private fun button(text: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener(View.OnClickListener { onClick() })
        content.addView(b)
    }

    private fun reasonText(r: Reason): String = when (r) {
        Reason.NIGHT -> "Nuit : rien à proposer."
        Reason.MEAL -> "Repas : rien à proposer."
        Reason.CLASS -> "Cours en cours."
        Reason.EXTERNAL -> "Colle ou DS : on attend."
        Reason.TOO_SHORT -> "Trop court pour proposer quelque chose."
        Reason.REST -> "Rien à proposer : repos libre."
    }

    private fun render() {
        content.removeAllViews()
        val now = LocalDateTime.now()
        val state = Repo.loadState(this)
        label("Séquenceur", 24f)
        if (state == null) {
            label("Aucune donnée : importe ton fichier data.json.")
        } else {
            label("Semestre ${state.semester} · ${state.slots.size} créneaux · ${state.colles.size} colles · ${state.ds.size} DS", 13f)
        }

        val active = Repo.active(this)
        val p = Repo.proposal(this)
        if (active != null) {
            val state2 = if (active.overrun) " — débordé" else " — jusqu'à ${active.plannedEnd.toLocalTime()}"
            label(Texts.activeLabel(active) + state2, 20f)
            button("Fini") {
                Sequencer.finish(this@MainActivity, FinishMode.EARLY)
                render()
            }
        } else if (p != null && !Lifecycle.isExpired(p, now)) {
            val a = p.action
            label(Texts.title(a, p.createdAt.toLocalDate()), 20f)
            val body = Texts.body(a, p.createdAt.toLocalDate())
            if (body.isNotEmpty() && a !is Action.J0Macro) label(body, 14f)
            if (a is Action.J0Macro) {
                if (state != null) {
                    for (item in J0.pending(state, now)) {
                        label(Texts.j0Item(item, now.toLocalDate()), 18f)
                        button("Choisir") {
                            val ok = Sequencer.chooseJ0(this@MainActivity, item)
                            if (!ok) toast("Pas assez de temps pour cette matière.")
                            render()
                        }
                        button("Skip (rien à traiter)") {
                            Sequencer.skipJ0(this@MainActivity, item)
                            render()
                        }
                    }
                }
            } else if (a is Action.Sport || a is Action.Cardio) {
                button("Oui") {
                    Sequencer.ready(this@MainActivity, p.id)
                    render()
                }
                button("Non") {
                    Sequencer.decline(this@MainActivity, p.id)
                    render()
                }
            } else {
                button("Prêt") {
                    Sequencer.ready(this@MainActivity, p.id)
                    render()
                }
                button("Skip") {
                    Sequencer.skip(this@MainActivity, p.id)
                    render()
                }
            }
        } else {
            label("Rien à proposer pour l'instant.", 18f)
        }

        button("Que faire ?") {
            val a = Sequencer.recalc(this@MainActivity, true)
            if (a is Action.None) toast(reasonText(a.reason))
            render()
        }
        button("Saisies (chapitre vu, difficulté, rang, DM…)") {
            startActivity(Intent(this@MainActivity, FormsActivity::class.java))
        }
        button("Importer data.json") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, REQ_IMPORT)
        }
        if (message.isNotEmpty()) label(message, 13f)

        label(
            "À régler une fois (HyperOS, Samsung) : notifications autorisées avec bannières ; " +
                "batterie « sans restriction » ; démarrage automatique activé ; application verrouillée " +
                "dans les applis récentes.",
            12f
        )
        button("Réglages de l'application") {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:" + packageName)
            startActivity(i)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val text: String? = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (ex: Exception) {
            null
        }
        if (text == null) {
            message = "Lecture du fichier impossible."
            render()
            return
        }
        val r = Repo.dataStore(this).import(text)
        if (r.ok) {
            val warn = if (r.warnings.isEmpty()) "" else "\nAvertissements :\n" + r.warnings.joinToString("\n")
            message = "Import réussi." + warn
            Sequencer.recalc(this)
        } else {
            message = "Import refusé, l'ancien fichier est conservé :\n" + r.errors.joinToString("\n")
        }
        render()
    }

    companion object {
        private const val REQ_IMPORT = 41
    }
}

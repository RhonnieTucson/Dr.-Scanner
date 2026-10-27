package com.drscanner.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drscanner.app.R

class ResultadoActivity : AppCompatActivity() {

    private lateinit var codigo: String
    private lateinit var simple: String
    private lateinit var pasos: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        codigo           = intent.getStringExtra("codigo")        ?: ""
        val nombre       = intent.getStringExtra("nombre")        ?: ""
        simple           = intent.getStringExtra("simple")        ?: ""
        val explicacion  = intent.getStringExtra("explicacion")   ?: ""
        val sistema      = intent.getStringExtra("sistema")       ?: ""
        val urgencia     = intent.getStringExtra("urgencia")      ?: "info"
        val puedeManejar = intent.getStringExtra("puede_manejar") ?: ""
        pasos            = intent.getStringArrayListExtra("pasos") ?: arrayListOf()
        val fuente       = intent.getStringExtra("fuente")        ?: "local"

        // Barra superior personalizada (el tema no tiene ActionBar)
        findViewById<TextView>(R.id.tvCodigoTitulo).text = codigo
        findViewById<TextView>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnCompartir).setOnClickListener {
            compartir(codigo, simple, pasos)
        }

        // Sistema
        findViewById<TextView>(R.id.tvSistema).text = sistema

        // Badge de urgencia con color correcto
        val tvUrgencia = findViewById<TextView>(R.id.tvUrgencia)
        tvUrgencia.text = urgenciaLabel(urgencia)
        tvUrgencia.setTextColor(urgenciaColor(urgencia))

        // Fuente
        val tvFuente = findViewById<TextView>(R.id.tvFuente)
        when (fuente) {
            "local"    -> tvFuente.text = "● BASE LOCAL · OFFLINE"
            "firebase" -> tvFuente.text = "🔥 COMUNIDAD"
            "ia"       -> tvFuente.text = "✦ CONSULTA IA"
            else       -> tvFuente.text = "● LOCAL"
        }

        // Textos
        findViewById<TextView>(R.id.tvSimple).text       = simple
        findViewById<TextView>(R.id.tvExplicacion).text  = explicacion
        findViewById<TextView>(R.id.tvPuedeManejar).text = puedeManejar
        findViewById<TextView>(R.id.tvSistemaBox).text   = sistema

        // Pasos
        val containerPasos = findViewById<LinearLayout>(R.id.containerPasos)
        pasos.forEachIndexed { index, paso ->
            val pasoView = layoutInflater.inflate(R.layout.item_paso, containerPasos, false)
            pasoView.findViewById<TextView>(R.id.tvNumPaso).text = "${index + 1}"
            pasoView.findViewById<TextView>(R.id.tvTextoPaso).text = paso
            containerPasos.addView(pasoView)
        }
    }

    private fun compartir(codigo: String, simple: String, pasos: List<String>) {
        val pasosTexto = pasos.mapIndexed { i, p -> "${i + 1}. $p" }.joinToString("\n")
        val texto = """
Dr. Scanner — Código $codigo

$simple

¿Qué hacer?
$pasosTexto

Diagnóstico generado por Dr. Scanner
        """.trimIndent()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico OBD2", texto))
        Toast.makeText(this, "Diagnóstico copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    private fun urgenciaLabel(u: String) = when (u.lowercase()) {
        "alto"  -> "⚠ URGENTE"
        "medio" -> "ATENDER PRONTO"
        "bajo"  -> "SIN URGENCIA"
        else    -> "INFORMATIVO"
    }

    private fun urgenciaColor(u: String) = when (u.lowercase()) {
        "alto"  -> ContextCompat.getColor(this, R.color.urgencia_alto)
        "medio" -> ContextCompat.getColor(this, R.color.urgencia_medio)
        "bajo"  -> ContextCompat.getColor(this, R.color.urgencia_bajo)
        else    -> ContextCompat.getColor(this, R.color.cyan)
    }
}

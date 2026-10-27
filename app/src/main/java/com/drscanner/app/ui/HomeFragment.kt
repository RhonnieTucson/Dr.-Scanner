package com.drscanner.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.drscanner.app.R
import com.drscanner.app.db.DatabaseHelper
import com.drscanner.app.model.EscaneoHistorial
import com.drscanner.app.obd.ObdService
import com.drscanner.app.obd.ObdState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var db: DatabaseHelper
    private lateinit var etCodigo: TextInputEditText
    private lateinit var btnScan: Button
    private lateinit var btnScanObd: Button
    private lateinit var tvObdBadge: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = DatabaseHelper.getInstance(requireContext())
        etCodigo    = view.findViewById(R.id.etCodigo)
        btnScan     = view.findViewById(R.id.btnScan)
        btnScanObd  = view.findViewById(R.id.btnScanObd)
        tvObdBadge  = view.findViewById(R.id.tvObdBadge)

        cargarStats(view)
        configurarListeners(view)
        actualizarBadgeObd()
    }

    override fun onResume() {
        super.onResume()
        cargarStats(requireView())
        actualizarBadgeObd()
    }

    private fun cargarStats(view: View) {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { db.estadisticas() }

            fun setCard(id: Int, value: String, label: String) {
                val card = view.findViewById<View>(id)
                card.findViewById<TextView>(R.id.tvStatValue).text = value
                card.findViewById<TextView>(R.id.tvStatLabel).text = label
            }

            setCard(R.id.statCodigos, stats.totalCodigos.toString(), "CÓDIGOS")
            setCard(R.id.statHoy,     stats.hoy.toString(),          "HOY")
            setCard(R.id.statTotal,   stats.totalHistorial.toString(),"TOTAL")
            setCard(R.id.statIA,      stats.codigosIA.toString(),     "IA")
        }
    }

    private fun configurarListeners(view: View) {
        // Botón SCAN manual
        btnScan.setOnClickListener {
            buscar(etCodigo.text.toString())
        }

        // Teclado → tecla Search/Enter
        etCodigo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscar(etCodigo.text.toString())
                true
            } else false
        }

        // Chips de ejemplo
        val chips = listOf("P0300","P0420","P0171","C0035","U0100","B0001")
        val chipIds = listOf(
            R.id.chipP0300, R.id.chipP0420, R.id.chipP0171,
            R.id.chipC0035, R.id.chipU0100, R.id.chipB0001
        )
        chipIds.forEachIndexed { i, id ->
            view.findViewById<Chip>(id).setOnClickListener {
                etCodigo.setText(chips[i])
                buscar(chips[i])
            }
        }

        // Badge OBD2 → abre pantalla de conexión
        tvObdBadge.setOnClickListener {
            startActivity(Intent(requireContext(), ObdConnectActivity::class.java))
        }

        // Botón escanear vía OBD2
        btnScanObd.setOnClickListener {
            escanearOBD2()
        }
    }

    private fun actualizarBadgeObd() {
        val obd = ObdService.getInstance()
        if (obd.state == ObdState.CONECTADO) {
            tvObdBadge.text = "● OBD2"
            tvObdBadge.setTextColor(resources.getColor(R.color.urgencia_bajo, null))
            tvObdBadge.setBackgroundResource(R.drawable.bg_badge_on)
            btnScanObd.visibility = View.VISIBLE
        } else {
            tvObdBadge.text = "SIN OBD2"
            tvObdBadge.setTextColor(resources.getColor(R.color.muted, null))
            tvObdBadge.setBackgroundResource(R.drawable.bg_badge_off)
            btnScanObd.visibility = View.GONE
        }
    }

    // ── Búsqueda manual de código ────────────────────────────
    private fun buscar(codigoRaw: String) {
        val codigo = codigoRaw.trim().uppercase()
        if (codigo.isEmpty()) {
            Toast.makeText(requireContext(), "Ingresa un código", Toast.LENGTH_SHORT).show()
            return
        }

        btnScan.isEnabled = false
        btnScan.text = "..."

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) { db.buscarCodigo(codigo) }

            btnScan.isEnabled = true
            btnScan.text = "SCAN"

            if (resultado != null) {
                // Guardar en historial
                val ahora = Date()
                val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(ahora)
                val hora  = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(ahora)

                withContext(Dispatchers.IO) {
                    db.guardarEscaneo(
                        EscaneoHistorial(
                            codigo   = resultado.codigo,
                            nombre   = resultado.nombre,
                            simple   = resultado.simple,
                            sistema  = resultado.sistema,
                            urgencia = resultado.urgencia,
                            fuente   = resultado.fuente,
                            fecha    = fecha,
                            hora     = hora
                        )
                    )
                }

                // Ir a la pantalla de resultado
                val intent = Intent(requireContext(), ResultadoActivity::class.java).apply {
                    putExtra("codigo",        resultado.codigo)
                    putExtra("nombre",        resultado.nombre)
                    putExtra("simple",        resultado.simple)
                    putExtra("explicacion",   resultado.explicacion)
                    putExtra("sistema",       resultado.sistema)
                    putExtra("urgencia",      resultado.urgencia)
                    putExtra("puede_manejar", resultado.puedeManejarse)
                    putStringArrayListExtra("pasos", ArrayList(resultado.pasos))
                    putExtra("fuente",        resultado.fuente)
                }
                startActivity(intent)

            } else {
                mostrarNoEncontrado(codigo)
            }
        }
    }

    // ── Código no encontrado en la base local ───────────────
    private fun mostrarNoEncontrado(codigo: String) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_no_encontrado, null)
        view.findViewById<TextView>(R.id.tvCodigoNoEncontrado).text =
            "Código \"$codigo\" no encontrado"
        view.findViewById<Button>(R.id.btnConsultarIA).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(),
                "IA próximamente disponible", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnCancelar).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    // ── Escaneo automático por OBD2 Bluetooth ───────────────
    private fun escanearOBD2() {
        val obd = ObdService.getInstance()
        if (obd.state != ObdState.CONECTADO) {
            Toast.makeText(requireContext(), "Conecta el OBD2 primero", Toast.LENGTH_SHORT).show()
            return
        }

        btnScanObd.isEnabled = false
        btnScanObd.text = "ESCANEANDO..."

        lifecycleScope.launch {
            val codigos = withContext(Dispatchers.IO) { obd.leerCodigos() }

            btnScanObd.isEnabled = true
            btnScanObd.text = "ESCANEAR VEHÍCULO VÍA OBD2"

            if (codigos.isEmpty()) {
                Toast.makeText(requireContext(),
                    "✓ Sin códigos de falla", Toast.LENGTH_LONG).show()
            } else {
                // Buscar el primero automáticamente, mostrar lista si hay varios
                if (codigos.size == 1) {
                    buscar(codigos[0])
                } else {
                    mostrarListaCodigos(codigos)
                }
            }
        }
    }

    // ── Lista de códigos encontrados por OBD2 ───────────────
    private fun mostrarListaCodigos(codigos: List<String>) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_codigos_obd, null)

        view.findViewById<TextView>(R.id.tvCantidadCodigos).text =
            "${codigos.size} CÓDIGO(S) ENCONTRADO(S)"

        val container = view.findViewById<ViewGroup>(R.id.containerCodigos)
        codigos.forEach { codigo ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = codigo
                setOnClickListener {
                    dialog.dismiss()
                    buscar(codigo)
                }
            }
            container.addView(chip)
        }

        dialog.setContentView(view)
        dialog.show()
    }
}

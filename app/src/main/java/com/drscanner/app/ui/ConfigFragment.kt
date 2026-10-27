package com.drscanner.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.drscanner.app.R
import com.drscanner.app.db.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigFragment : Fragment() {

    private lateinit var db: DatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_config, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.getInstance(requireContext())

        // Filas estáticas
        setRow(view, R.id.rowVersion,    "Versión",           "1.0")
        setRow(view, R.id.rowFuente,     "Fuente",            "SAE J1979 · ISO 15765")
        setRow(view, R.id.rowCobertura,  "Cobertura",         "P0/P1/C0/B0/U0")

        // Estadísticas dinámicas
        cargarStats(view)

        // Limpiar historial
        view.findViewById<TextView>(R.id.btnLimpiarHistorial).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Limpiar historial")
                .setMessage("¿Deseas eliminar todos los escaneos?")
                .setPositiveButton("Eliminar") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.limpiarHistorial() }
                        Toast.makeText(requireContext(),
                            "Historial eliminado", Toast.LENGTH_SHORT).show()
                        cargarStats(requireView())
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        cargarStats(requireView())
    }

    private fun cargarStats(view: View) {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { db.estadisticas() }
            val container = view.findViewById<LinearLayout>(R.id.containerStats)
            container.removeAllViews()

            val rows = listOf(
                "Códigos en base" to "${stats.totalCodigos}",
                "Códigos IA aprendidos" to "${stats.codigosIA}",
                "Total de escaneos" to "${stats.totalHistorial}"
            )
            rows.forEach { (label, value) ->
                val rowView = layoutInflater.inflate(R.layout.item_config_row, container, false)
                rowView.findViewById<TextView>(R.id.tvConfigLabel).text = label
                rowView.findViewById<TextView>(R.id.tvConfigValue).text = value
                container.addView(rowView)
            }
        }
    }

    private fun setRow(root: View, rowId: Int, label: String, value: String) {
        val row = root.findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.tvConfigLabel).text = label
        row.findViewById<TextView>(R.id.tvConfigValue).text = value
    }
}

package com.drscanner.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drscanner.app.R
import com.drscanner.app.db.DatabaseHelper
import com.drscanner.app.model.Vehiculo
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VehiculosFragment : Fragment() {

    private lateinit var db: DatabaseHelper
    private lateinit var rv: RecyclerView
    private lateinit var layoutVacio: LinearLayout
    private val items = mutableListOf<Vehiculo>()
    private lateinit var adapter: VehiculoAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_vehiculos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = DatabaseHelper.getInstance(requireContext())
        rv = view.findViewById(R.id.rvVehiculos)
        layoutVacio = view.findViewById(R.id.layoutVacioVeh)

        adapter = VehiculoAdapter(items, ::onEliminar)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        view.findViewById<TextView>(R.id.btnAgregar).setOnClickListener {
            mostrarFormularioAgregar()
        }
        view.findViewById<Button?>(R.id.btnAgregarVacioVeh)?.setOnClickListener {
            mostrarFormularioAgregar()
        }

        cargar()
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            val lista = withContext(Dispatchers.IO) { db.obtenerVehiculos() }
            items.clear()
            items.addAll(lista)
            adapter.notifyDataSetChanged()
            actualizarEstado()
        }
    }

    private fun actualizarEstado() {
        if (items.isEmpty()) {
            rv.visibility = View.GONE
            layoutVacio.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            layoutVacio.visibility = View.GONE
        }
    }

    private fun onEliminar(vehiculo: Vehiculo) {
        val id = vehiculo.id
        if (id != null) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.eliminarVehiculo(id) }
                cargar()
            }
        }
    }

    private fun mostrarFormularioAgregar() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.sheet_agregar_vehiculo, null)

        val etNombre = view.findViewById<TextInputEditText>(R.id.etNombreVeh)
        val etMarca  = view.findViewById<TextInputEditText>(R.id.etMarcaVeh)
        val etModelo = view.findViewById<TextInputEditText>(R.id.etModeloVeh)
        val etPlaca  = view.findViewById<TextInputEditText>(R.id.etPlacaVeh)
        val etAnio   = view.findViewById<TextInputEditText>(R.id.etAnioVeh)
        val btnGuardar = view.findViewById<Button>(R.id.btnGuardarVeh)

        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            if (nombre.isEmpty()) {
                etNombre.error = "Requerido"
                return@setOnClickListener
            }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.guardarVehiculo(Vehiculo(
                        nombre = nombre,
                        marca  = etMarca.text.toString().trim(),
                        modelo = etModelo.text.toString().trim(),
                        placa  = etPlaca.text.toString().trim().uppercase(),
                        anio   = etAnio.text.toString().trim().toIntOrNull() ?: 0
                    ))
                }
                dialog.dismiss()
                cargar()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }
}

class VehiculoAdapter(
    private val items: List<Vehiculo>,
    private val onEliminar: (Vehiculo) -> Unit
) : RecyclerView.Adapter<VehiculoAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreVeh)
        val tvMarcaModelo: TextView = view.findViewById(R.id.tvMarcaModelo)
        val tvPlaca: TextView = view.findViewById(R.id.tvPlacaVeh)
        val btnEliminar: TextView = view.findViewById(R.id.btnEliminarVeh)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehiculo, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        holder.tvNombre.text = v.nombre

        val marcaModelo = "${v.marca} ${v.modelo}".trim()
        if (marcaModelo.isNotEmpty()) {
            holder.tvMarcaModelo.text = marcaModelo
            holder.tvMarcaModelo.visibility = View.VISIBLE
        } else {
            holder.tvMarcaModelo.visibility = View.GONE
        }

        if (v.placa.isNotEmpty()) {
            holder.tvPlaca.text = v.placa
            holder.tvPlaca.visibility = View.VISIBLE
        } else {
            holder.tvPlaca.visibility = View.GONE
        }

        holder.btnEliminar.setOnClickListener { onEliminar(v) }
    }
}

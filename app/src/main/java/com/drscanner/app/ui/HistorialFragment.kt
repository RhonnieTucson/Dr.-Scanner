package com.drscanner.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drscanner.app.R
import com.drscanner.app.db.DatabaseHelper
import com.drscanner.app.model.EscaneoHistorial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistorialFragment : Fragment() {

    private lateinit var db: DatabaseHelper
    private lateinit var rv: RecyclerView
    private lateinit var layoutVacio: LinearLayout
    private lateinit var btnLimpiar: TextView
    private val items = mutableListOf<EscaneoHistorial>()
    private lateinit var adapter: HistorialAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_historial, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = DatabaseHelper.getInstance(requireContext())
        rv = view.findViewById(R.id.rvHistorial)
        layoutVacio = view.findViewById(R.id.layoutVacio)
        btnLimpiar = view.findViewById(R.id.btnLimpiar)

        adapter = HistorialAdapter(items)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Swipe to delete
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.adapterPosition
                val item = items[pos]
                val id = item.id
                if (id != null) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.eliminarEscaneo(id) }
                        items.removeAt(pos)
                        adapter.notifyItemRemoved(pos)
                        actualizarEstado()
                    }
                }
            }
        }).attachToRecyclerView(rv)

        btnLimpiar.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Limpiar historial")
                .setMessage("¿Eliminar todos los escaneos?")
                .setPositiveButton("Eliminar") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.limpiarHistorial() }
                        items.clear()
                        adapter.notifyDataSetChanged()
                        actualizarEstado()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        cargar()
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            val historial = withContext(Dispatchers.IO) { db.obtenerHistorial() }
            items.clear()
            items.addAll(historial)
            adapter.notifyDataSetChanged()
            actualizarEstado()
        }
    }

    private fun actualizarEstado() {
        if (items.isEmpty()) {
            rv.visibility = View.GONE
            layoutVacio.visibility = View.VISIBLE
            btnLimpiar.visibility = View.GONE
        } else {
            rv.visibility = View.VISIBLE
            layoutVacio.visibility = View.GONE
            btnLimpiar.visibility = View.VISIBLE
        }
    }
}

class HistorialAdapter(private val items: List<EscaneoHistorial>)
    : RecyclerView.Adapter<HistorialAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val viewBar: View = view.findViewById(R.id.viewUrgBar)
        val tvCodigo: TextView = view.findViewById(R.id.tvCodigo)
        val tvPlaca: TextView = view.findViewById(R.id.tvPlaca)
        val tvSimple: TextView = view.findViewById(R.id.tvSimple)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvHora: TextView = view.findViewById(R.id.tvHora)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historial, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        val color = when (item.urgencia.lowercase()) {
            "alto"  -> ContextCompat.getColor(ctx, R.color.urgencia_alto)
            "medio" -> ContextCompat.getColor(ctx, R.color.urgencia_medio)
            "bajo"  -> ContextCompat.getColor(ctx, R.color.urgencia_bajo)
            else    -> ContextCompat.getColor(ctx, R.color.cyan)
        }

        holder.viewBar.setBackgroundColor(color)
        holder.tvCodigo.text = item.codigo
        holder.tvCodigo.setTextColor(color)
        holder.tvSimple.text = item.simple
        holder.tvFecha.text = item.fecha
        holder.tvHora.text = item.hora

        if (item.vehiculoPlaca.isNotEmpty()) {
            holder.tvPlaca.visibility = View.VISIBLE
            holder.tvPlaca.text = item.vehiculoPlaca
        } else {
            holder.tvPlaca.visibility = View.GONE
        }
    }
}

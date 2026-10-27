package com.drscanner.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drscanner.app.R
import com.drscanner.app.obd.ObdService
import com.drscanner.app.obd.ObdState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObdConnectActivity : AppCompatActivity() {

    private val obd = ObdService.getInstance()
    private lateinit var containerDispositivos: LinearLayout
    private lateinit var layoutSinDispositivos: LinearLayout
    private lateinit var layoutConectado: LinearLayout
    private lateinit var tvConectadoNombre: TextView
    private lateinit var progressObd: ProgressBar

    companion object { private const val BT_PERM_CODE = 101 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obd_connect)

        containerDispositivos  = findViewById(R.id.containerDispositivos)
        layoutSinDispositivos  = findViewById(R.id.layoutSinDispositivos)
        layoutConectado        = findViewById(R.id.layoutConectado)
        tvConectadoNombre      = findViewById(R.id.tvConectadoNombre)
        progressObd            = findViewById(R.id.progressObd)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnRefrescar).setOnClickListener { buscarDispositivos() }
        findViewById<TextView>(R.id.btnDesconectar).setOnClickListener {
            obd.desconectar()
            actualizarEstadoConectado()
            buscarDispositivos()
        }

        actualizarEstadoConectado()
        buscarDispositivos()
    }

    private fun actualizarEstadoConectado() {
        if (obd.state == ObdState.CONECTADO) {
            layoutConectado.visibility = View.VISIBLE
            tvConectadoNombre.text = "✓ Conectado a ${nombreDispositivo()}"
        } else {
            layoutConectado.visibility = View.GONE
        }
    }

    @SuppressLint("MissingPermission")
    private fun nombreDispositivo(): String =
        try { obd.dispositivoConectado?.name ?: "OBD2" } catch (_: Exception) { "OBD2" }

    private fun tienePermisos(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pedirPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                BT_PERM_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION),
                BT_PERM_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BT_PERM_CODE) buscarDispositivos()
    }

    private fun buscarDispositivos() {
        if (!tienePermisos()) { pedirPermisos(); return }
        if (!obd.bluetoothDisponible(this)) {
            Toast.makeText(this, "Activa el Bluetooth en tu teléfono", Toast.LENGTH_LONG).show()
            return
        }

        progressObd.visibility = View.VISIBLE
        containerDispositivos.removeAllViews()
        layoutSinDispositivos.visibility = View.GONE

        lifecycleScope.launch {
            val dispositivos = withContext(Dispatchers.IO) {
                obd.dispositivosVinculados(this@ObdConnectActivity)
            }
            progressObd.visibility = View.GONE

            if (dispositivos.isEmpty()) {
                layoutSinDispositivos.visibility = View.VISIBLE
            } else {
                dispositivos.forEach { device ->
                    agregarItemDispositivo(device)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun agregarItemDispositivo(device: BluetoothDevice) {
        val nombre = try { device.name ?: "Dispositivo desconocido" }
                     catch (_: SecurityException) { device.address }
        val esOBD = nombre.uppercase().let {
            it.contains("OBD") || it.contains("ELM") || it.contains("VLINK")
        }

        val itemView = layoutInflater.inflate(R.layout.item_dispositivo_bt, null)
        itemView.findViewById<TextView>(R.id.tvNombreDisp).text = nombre
        itemView.findViewById<TextView>(R.id.tvDireccionDisp).text = device.address

        val tvTag = itemView.findViewById<TextView>(R.id.tvTagObd)
        tvTag.visibility = if (esOBD) View.VISIBLE else View.GONE

        if (esOBD) {
            itemView.setBackgroundResource(R.drawable.bg_card_cyan)
        }

        itemView.setOnClickListener { conectar(device, nombre) }
        containerDispositivos.addView(itemView)
    }

    private fun conectar(device: BluetoothDevice, nombre: String) {
        Toast.makeText(this, "Conectando con $nombre...", Toast.LENGTH_SHORT).show()
        progressObd.visibility = View.VISIBLE

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { obd.conectar(device) }
            progressObd.visibility = View.GONE

            if (ok) {
                Toast.makeText(this@ObdConnectActivity,
                    "✓ Conectado a $nombre", Toast.LENGTH_LONG).show()
                actualizarEstadoConectado()
                finish()
            } else {
                Toast.makeText(this@ObdConnectActivity,
                    "No se pudo conectar. Verifica que el OBD2 esté encendido.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}

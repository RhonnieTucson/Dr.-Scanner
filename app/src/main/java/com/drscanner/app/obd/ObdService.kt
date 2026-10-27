package com.drscanner.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ObdState { DESCONECTADO, CONECTANDO, CONECTADO, ERROR }

/**
 * Servicio Bluetooth Classic (SPP) para comunicarse con adaptadores
 * ELM327 OBD2 - el tipo más común y económico (plástico azul).
 *
 * Usa el UUID estándar SPP: 00001101-0000-1000-8000-00805F9B34FB
 */
class ObdService private constructor() {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        @Volatile
        private var INSTANCE: ObdService? = null

        fun getInstance(): ObdService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ObdService().also { INSTANCE = it }
            }
    }

    var state: ObdState = ObdState.DESCONECTADO
        private set

    var dispositivoConectado: BluetoothDevice? = null
        private set

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun adapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? android.bluetooth.BluetoothManager
        return manager?.adapter
    }

    @SuppressLint("MissingPermission")
    fun bluetoothDisponible(context: Context): Boolean =
        adapter(context)?.isEnabled == true

    /** Lista los dispositivos ya vinculados (emparejados) en Ajustes Bluetooth del teléfono. */
    @SuppressLint("MissingPermission")
    fun dispositivosVinculados(context: Context): List<BluetoothDevice> {
        val ad = adapter(context) ?: return emptyList()
        return try {
            ad.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Filtra solo los dispositivos que parecen ser adaptadores OBD2/ELM327. */
    @SuppressLint("MissingPermission")
    fun dispositivosOBD(context: Context): List<BluetoothDevice> =
        dispositivosVinculados(context).filter { d ->
            val nombre = (d.name ?: "").uppercase()
            nombre.contains("OBD") || nombre.contains("ELM") ||
                nombre.contains("VLINK") || nombre.contains("OBDII")
        }

    /** Conecta al adaptador OBD2 e inicializa el ELM327. Debe llamarse desde una corrutina. */
    @SuppressLint("MissingPermission")
    suspend fun conectar(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        state = ObdState.CONECTANDO
        try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            input = s.inputStream
            output = s.outputStream
            dispositivoConectado = device
            state = ObdState.CONECTADO

            inicializarElm327()
            true
        } catch (e: IOException) {
            cerrarSilencioso()
            state = ObdState.ERROR
            false
        }
    }

    private suspend fun inicializarElm327() {
        enviarComando("ATZ")
        enviarComando("ATE0")  // eco apagado
        enviarComando("ATL0")  // sin saltos de línea extra
        enviarComando("ATSP0") // protocolo automático
        enviarComando("ATH0")  // sin cabeceras
    }

    /** Envía un comando AT/OBD y espera la respuesta terminada en '>' (prompt del ELM327). */
    suspend fun enviarComando(cmd: String, timeoutMs: Long = 4000): String =
        withContext(Dispatchers.IO) {
            val out = output ?: return@withContext ""
            val inp = input ?: return@withContext ""

            try {
                out.write("$cmd\r".toByteArray())
                out.flush()

                val sb = StringBuilder()
                val buffer = ByteArray(1024)
                val start = System.currentTimeMillis()

                while (System.currentTimeMillis() - start < timeoutMs) {
                    if (inp.available() > 0) {
                        val read = inp.read(buffer)
                        if (read > 0) {
                            sb.append(String(buffer, 0, read))
                            if (sb.contains('>')) break
                        }
                    } else {
                        kotlinx.coroutines.delay(30)
                    }
                }
                sb.toString().replace(">", "").trim()
            } catch (e: IOException) {
                cerrarSilencioso()
                state = ObdState.ERROR
                ""
            }
        }

    /** Lee los códigos de falla almacenados (Modo 03). */
    suspend fun leerCodigos(): List<String> {
        val resp = enviarComando("03")
        return parsearCodigos(resp)
    }

    /** Borra los códigos de falla (Modo 04). */
    suspend fun borrarCodigos(): Boolean {
        val resp = enviarComando("04")
        return resp.contains("44") || resp.lowercase().contains("ok")
    }

    suspend fun leerRPM(): Int? {
        val bytes = extraerBytes(enviarComando("010C"))
        return if (bytes.size >= 2) ((bytes[0] * 256) + bytes[1]) / 4 else null
    }

    suspend fun leerVelocidad(): Int? {
        val bytes = extraerBytes(enviarComando("010D"))
        return bytes.getOrNull(0)
    }

    suspend fun leerTemperaturaMotor(): Int? {
        val bytes = extraerBytes(enviarComando("0105"))
        return bytes.getOrNull(0)?.minus(40)
    }

    suspend fun leerVoltajeBateria(): Double? {
        val resp = enviarComando("ATRV")
        val match = Regex("([\\d.]+)").find(resp)
        return match?.value?.toDoubleOrNull()
    }

    /** Convierte la respuesta cruda del modo 03 en una lista de códigos tipo "P0300". */
    private fun parsearCodigos(respuesta: String): List<String> {
        val limpia = respuesta
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("NO DATA", "")
            .replace("OK", "")
            .trim()

        val bytes = limpia.split(" ")
            .filter { it.isNotEmpty() && it.matches(Regex("^[0-9A-Fa-f]{2}$")) }

        var start = 0
        if (bytes.size >= 2 && bytes[0].uppercase() == "43") start = 2

        val codigos = mutableListOf<String>()
        var i = start
        while (i < bytes.size - 1) {
            try {
                val b1 = bytes[i].toInt(16)
                val b2 = bytes[i + 1].toInt(16)
                if (b1 != 0 || b2 != 0) {
                    val prefijos = listOf("P", "C", "B", "U")
                    val tipo = (b1 and 0xC0) shr 6
                    val n1 = (b1 and 0x30) shr 4
                    val n2 = b1 and 0x0F
                    val n3 = (b2 and 0xF0) shr 4
                    val n4 = b2 and 0x0F
                    val codigo = "${prefijos[tipo]}$n1$n2" +
                        Integer.toHexString(n3).uppercase() +
                        Integer.toHexString(n4).uppercase()
                    if (codigo !in codigos) codigos.add(codigo)
                }
            } catch (_: Exception) {
            }
            i += 2
        }
        return codigos
    }

    /** Extrae los bytes de datos de una respuesta tipo "41 0C 1A F8" saltando modo+PID. */
    private fun extraerBytes(resp: String): List<Int> {
        val partes = resp
            .replace("\r", " ")
            .replace("\n", " ")
            .split(" ")
            .filter { it.matches(Regex("^[0-9A-Fa-f]{2}$")) }
        return if (partes.size > 2) {
            partes.drop(2).map { it.toInt(16) }
        } else emptyList()
    }

    private fun cerrarSilencioso() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
        dispositivoConectado = null
    }

    fun desconectar() {
        cerrarSilencioso()
        state = ObdState.DESCONECTADO
    }
}

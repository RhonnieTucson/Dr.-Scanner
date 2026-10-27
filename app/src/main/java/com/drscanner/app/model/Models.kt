package com.drscanner.app.model

/**
 * Representa un código OBD2 con su explicación en lenguaje simple.
 */
data class CodigoOBD2(
    val codigo: String,
    val nombre: String,
    val simple: String,
    val explicacion: String,
    val sistema: String,
    val urgencia: String,
    val puedeManejarse: String,
    val pasos: List<String>,
    val categoria: String,
    val fuente: String = "local" // local | ia
)

/**
 * Un registro del historial de escaneos del usuario.
 */
data class EscaneoHistorial(
    val id: Long? = null,
    val codigo: String,
    val nombre: String,
    val simple: String,
    val sistema: String,
    val urgencia: String,
    val fuente: String,
    val fecha: String,   // yyyy-MM-dd
    val hora: String,    // HH:mm:ss
    val vehiculoNombre: String = "",
    val vehiculoPlaca: String = "",
    val notas: String = ""
)

/**
 * Un vehículo registrado por el usuario.
 */
data class Vehiculo(
    val id: Long? = null,
    val nombre: String,
    val marca: String = "",
    val modelo: String = "",
    val anio: Int = 0,
    val placa: String = "",
    val color: String = ""
)

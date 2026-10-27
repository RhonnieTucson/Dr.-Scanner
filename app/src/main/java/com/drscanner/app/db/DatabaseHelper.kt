package com.drscanner.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.drscanner.app.model.CodigoOBD2
import com.drscanner.app.model.EscaneoHistorial
import com.drscanner.app.model.Vehiculo
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

/**
 * Maneja la base de datos SQLite de Dr. Scanner.
 *
 * La primera vez que se abre la app, copia `drscanner.db` (con los 1,511
 * códigos pre-cargados) desde los assets a la carpeta de datos de la app.
 * Las siguientes veces simplemente abre ese archivo, conservando el
 * historial y los vehículos guardados por el usuario.
 *
 * Mantiene UNA sola conexión persistente y sincronizada en vez de abrir
 * y cerrar una conexión por cada operación, evitando así
 * `SQLiteDatabaseLockedException` cuando dos corrutinas acceden a la vez
 * (por ejemplo, cargar estadísticas mientras se guarda un escaneo).
 */
class DatabaseHelper private constructor(private val context: Context) {

    companion object {
        private const val DB_NAME = "drscanner.db"

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val dbPath: String by lazy {
        context.getDatabasePath(DB_NAME).absolutePath
    }

    private fun ensureDatabaseCopied() {
        val dbFile = File(dbPath)
        if (dbFile.exists()) return

        dbFile.parentFile?.mkdirs()

        context.assets.open("databases/$DB_NAME").use { input ->
            FileOutputStream(dbFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private val db: SQLiteDatabase by lazy {
        ensureDatabaseCopied()
        SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
    }

    /** Todas las operaciones de base de datos pasan por aquí, sincronizadas. */
    private inline fun <T> conDb(block: (SQLiteDatabase) -> T): T = synchronized(this) {
        block(db)
    }

    // ─────────────────────────────────────────────────────────────
    // BÚSQUEDA DE CÓDIGOS
    // ─────────────────────────────────────────────────────────────

    /** Busca un código exacto, primero en la base principal y luego en los aprendidos por IA. */
    fun buscarCodigo(codigoRaw: String): CodigoOBD2? = conDb { database ->
        val codigo = codigoRaw.trim().uppercase()

        database.rawQuery(
            "SELECT * FROM codigos_obd2 WHERE codigo = ?",
            arrayOf(codigo)
        ).use { c ->
            if (c.moveToFirst()) return@conDb cursorToCodigo(c, "local")
        }

        database.rawQuery(
            "SELECT * FROM codigos_ia WHERE codigo = ?",
            arrayOf(codigo)
        ).use { c ->
            if (c.moveToFirst()) return@conDb cursorToCodigo(c, "ia")
        }

        null
    }

    /** Búsqueda de texto libre en código, nombre, descripción simple y sistema. */
    fun buscarTexto(texto: String, limit: Int = 50): List<CodigoOBD2> = conDb { database ->
        val q = "%$texto%"
        val resultados = mutableListOf<CodigoOBD2>()
        database.rawQuery(
            """SELECT * FROM codigos_obd2
               WHERE codigo LIKE ? OR simple LIKE ? OR nombre LIKE ? OR sistema LIKE ?
               LIMIT ?""",
            arrayOf(q, q, q, q, limit.toString())
        ).use { c ->
            while (c.moveToNext()) resultados.add(cursorToCodigo(c, "local"))
        }
        resultados
    }

    fun buscarPorCategoria(categoria: String): List<CodigoOBD2> = conDb { database ->
        val resultados = mutableListOf<CodigoOBD2>()
        val cursor = if (categoria == "todos") {
            database.rawQuery("SELECT * FROM codigos_obd2 ORDER BY codigo ASC", null)
        } else {
            database.rawQuery(
                "SELECT * FROM codigos_obd2 WHERE categoria = ? ORDER BY codigo ASC",
                arrayOf(categoria)
            )
        }
        cursor.use { c ->
            while (c.moveToNext()) resultados.add(cursorToCodigo(c, "local"))
        }
        resultados
    }

    fun buscarPorUrgencia(urgencia: String): List<CodigoOBD2> = conDb { database ->
        val resultados = mutableListOf<CodigoOBD2>()
        database.rawQuery(
            "SELECT * FROM codigos_obd2 WHERE urgencia = ? ORDER BY codigo ASC",
            arrayOf(urgencia)
        ).use { c ->
            while (c.moveToNext()) resultados.add(cursorToCodigo(c, "local"))
        }
        resultados
    }

    /** Guarda un código nuevo resuelto por IA para futuras consultas offline. */
    fun guardarCodigoIA(codigo: CodigoOBD2) = conDb { database ->
        database.execSQL(
            """INSERT OR REPLACE INTO codigos_ia
               (codigo, nombre, simple, explicacion, sistema, urgencia, puede_manejar, pasos)
               VALUES (?,?,?,?,?,?,?,?)""",
            arrayOf<Any>(
                codigo.codigo, codigo.nombre, codigo.simple, codigo.explicacion,
                codigo.sistema, codigo.urgencia, codigo.puedeManejarse,
                JSONArray(codigo.pasos).toString()
            )
        )
    }

    private fun cursorToCodigo(c: android.database.Cursor, fuente: String): CodigoOBD2 {
        val pasosJson = c.getString(c.getColumnIndexOrThrow("pasos"))
        val pasos = mutableListOf<String>()
        try {
            val arr = JSONArray(pasosJson)
            for (i in 0 until arr.length()) pasos.add(arr.getString(i))
        } catch (_: Exception) {
        }

        val categoriaIdx = c.getColumnIndex("categoria")
        val categoria = if (categoriaIdx >= 0) c.getString(categoriaIdx) ?: "IA" else "IA"

        return CodigoOBD2(
            codigo = c.getString(c.getColumnIndexOrThrow("codigo")),
            nombre = c.getString(c.getColumnIndexOrThrow("nombre")),
            simple = c.getString(c.getColumnIndexOrThrow("simple")),
            explicacion = c.getString(c.getColumnIndexOrThrow("explicacion")),
            sistema = c.getString(c.getColumnIndexOrThrow("sistema")),
            urgencia = c.getString(c.getColumnIndexOrThrow("urgencia")),
            puedeManejarse = c.getString(c.getColumnIndexOrThrow("puede_manejar")),
            pasos = pasos,
            categoria = categoria,
            fuente = fuente
        )
    }

    // ─────────────────────────────────────────────────────────────
    // HISTORIAL
    // ─────────────────────────────────────────────────────────────

    fun guardarEscaneo(e: EscaneoHistorial): Long = conDb { database ->
        execSQLInsert(
            database,
            """INSERT INTO historial
               (codigo, nombre, simple, sistema, urgencia, fuente, fecha, hora, vehiculo_nombre, vehiculo_placa, notas)
               VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any>(
                e.codigo, e.nombre, e.simple, e.sistema, e.urgencia, e.fuente,
                e.fecha, e.hora, e.vehiculoNombre, e.vehiculoPlaca, e.notas
            )
        )
    }

    fun obtenerHistorial(limit: Int = 200): List<EscaneoHistorial> = conDb { database ->
        val resultados = mutableListOf<EscaneoHistorial>()
        database.rawQuery(
            "SELECT * FROM historial ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) resultados.add(cursorToHistorial(c))
        }
        resultados
    }

    fun historialPorVehiculo(placa: String): List<EscaneoHistorial> = conDb { database ->
        val resultados = mutableListOf<EscaneoHistorial>()
        database.rawQuery(
            "SELECT * FROM historial WHERE vehiculo_placa = ? ORDER BY timestamp DESC",
            arrayOf(placa)
        ).use { c ->
            while (c.moveToNext()) resultados.add(cursorToHistorial(c))
        }
        resultados
    }

    fun eliminarEscaneo(id: Long) = conDb { database ->
        database.execSQL("DELETE FROM historial WHERE id = ?", arrayOf<Any>(id))
    }

    fun limpiarHistorial() = conDb { database ->
        database.execSQL("DELETE FROM historial")
    }

    private fun cursorToHistorial(c: android.database.Cursor): EscaneoHistorial {
        fun col(name: String): String {
            val idx = c.getColumnIndex(name)
            return if (idx >= 0) c.getString(idx) ?: "" else ""
        }
        return EscaneoHistorial(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            codigo = col("codigo"),
            nombre = col("nombre"),
            simple = col("simple"),
            sistema = col("sistema"),
            urgencia = col("urgencia"),
            fuente = col("fuente"),
            fecha = col("fecha"),
            hora = col("hora"),
            vehiculoNombre = col("vehiculo_nombre"),
            vehiculoPlaca = col("vehiculo_placa"),
            notas = col("notas")
        )
    }

    // ─────────────────────────────────────────────────────────────
    // VEHÍCULOS
    // ─────────────────────────────────────────────────────────────

    fun guardarVehiculo(v: Vehiculo): Long = conDb { database ->
        execSQLInsert(
            database,
            """INSERT INTO vehiculos (nombre, marca, modelo, anio, placa, color)
               VALUES (?,?,?,?,?,?)""",
            arrayOf<Any>(v.nombre, v.marca, v.modelo, v.anio, v.placa, v.color)
        )
    }

    fun obtenerVehiculos(): List<Vehiculo> = conDb { database ->
        val resultados = mutableListOf<Vehiculo>()
        database.rawQuery(
            "SELECT * FROM vehiculos WHERE activo = 1 ORDER BY created_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                resultados.add(
                    Vehiculo(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        nombre = c.getString(c.getColumnIndexOrThrow("nombre")),
                        marca = c.getString(c.getColumnIndexOrThrow("marca")) ?: "",
                        modelo = c.getString(c.getColumnIndexOrThrow("modelo")) ?: "",
                        anio = c.getInt(c.getColumnIndexOrThrow("anio")),
                        placa = c.getString(c.getColumnIndexOrThrow("placa")) ?: "",
                        color = c.getString(c.getColumnIndexOrThrow("color")) ?: ""
                    )
                )
            }
        }
        resultados
    }

    fun eliminarVehiculo(id: Long) = conDb { database ->
        database.execSQL("UPDATE vehiculos SET activo = 0 WHERE id = ?", arrayOf<Any>(id))
    }

    // ─────────────────────────────────────────────────────────────
    // ESTADÍSTICAS
    // ─────────────────────────────────────────────────────────────

    data class Estadisticas(
        val totalCodigos: Int,
        val codigosIA: Int,
        val totalHistorial: Int,
        val hoy: Int
    )

    fun estadisticas(): Estadisticas = conDb { database ->
        fun count(sql: String): Int {
            database.rawQuery(sql, null).use { c ->
                return if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
        Estadisticas(
            totalCodigos = count("SELECT COUNT(*) FROM codigos_obd2"),
            codigosIA = count("SELECT COUNT(*) FROM codigos_ia"),
            totalHistorial = count("SELECT COUNT(*) FROM historial"),
            hoy = count("SELECT COUNT(*) FROM historial WHERE fecha = date('now')")
        )
    }

    /** Inserta usando un statement compilado y devuelve el rowid generado. */
    private fun execSQLInsert(database: SQLiteDatabase, sql: String, args: Array<Any>): Long {
        val stmt = database.compileStatement(sql)
        for (i in args.indices) {
            when (val a = args[i]) {
                is String -> stmt.bindString(i + 1, a)
                is Long -> stmt.bindLong(i + 1, a)
                is Int -> stmt.bindLong(i + 1, a.toLong())
                else -> stmt.bindString(i + 1, a.toString())
            }
        }
        return stmt.executeInsert()
    }
}

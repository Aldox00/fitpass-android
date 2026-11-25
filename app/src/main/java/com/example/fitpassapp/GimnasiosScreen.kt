package com.example.fitpassapp

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val gymNaranja = Color(0xFFFF5722)
private val gymFondo = Color(0xFF121212)
private val gymCardBg = Color(0xFF1E1E1E)
private val gymSubCardBg = Color(0xFF2A2A2A)
private val gymInputBg = Color(0xFF141416)
private val gymTextoGris = Color(0xFFB0B0B0)
private val gymLabelGris = Color(0xFF9E9E9E)
private val gymRojoError = Color(0xFFFF5252)

private const val LAT_RESPALDO_SUCHIAPA = 16.62123746424151
private const val LON_RESPALDO_SUCHIAPA = -93.1026533291888

private data class OpcionPlanGym(
    val codigoBackend: String,
    val etiquetaVisible: String
)

private val planesMembresiaFitPass = listOf(
    OpcionPlanGym("VISITA", "Pase por día (visita) — $50"),
    OpcionPlanGym("SEMANAL", "Plan semanal — $150"),
    OpcionPlanGym("MENSUAL", "Plan mensual — $450"),
    OpcionPlanGym("ANUAL", "Plan anual — $4,200")
)

internal fun guardarGymApartadoLocal(context: Context, usuarioId: Long, nombreGym: String) {
    context.getSharedPreferences("fitpass_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("gym_apartado_$usuarioId", nombreGym)
        .apply()
}

internal fun obtenerGymApartadoLocal(context: Context, usuarioId: Long): String? =
    context.getSharedPreferences("fitpass_prefs", Context.MODE_PRIVATE)
        .getString("gym_apartado_$usuarioId", null)

@Composable
private fun gymCampoColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorTextColor = Color.White,
    disabledTextColor = Color.Gray,
    cursorColor = gymNaranja,
    errorCursorColor = gymRojoError,
    focusedBorderColor = gymNaranja,
    unfocusedBorderColor = Color(0xFF48484A),
    errorBorderColor = gymRojoError,
    focusedLabelColor = gymNaranja,
    unfocusedLabelColor = gymLabelGris,
    errorLabelColor = gymRojoError,
    focusedContainerColor = gymInputBg,
    unfocusedContainerColor = gymInputBg,
    errorContainerColor = gymInputBg
)

private fun filtrarTextoDireccion(texto: String): String = texto.filter { ch ->
    ch.isLetterOrDigit() || ch == ' ' || ch == ',' || ch == '.' || ch == '#' || ch == '-'
}

private fun validarDireccionSuchiapa(valor: String, minCaracteres: Int = 3): String? {
    val limpia = valor.trim().replace(Regex("\\s+"), " ")
    if (limpia.length < minCaracteres) {
        return "Escribe al menos $minCaracteres caracteres de una referencia o calle"
    }
    if (limpia.count { it.isLetter() } < 2) {
        return "Ingresa letras válidas (no solo símbolos o números)"
    }
    if (Regex("(.)\\1{3,}").containsMatchIn(limpia)) {
        return "Evita repetir el mismo carácter tantas veces seguidas"
    }
    val municipiosBloqueados = listOf(
        "tuxtla", "chiapa de corzo", "villaflores", "villacorzo",
        "villa corzo", "comitan", "san cristobal", "cintalapa", "berriozabal"
    )
    if (municipiosBloqueados.any { limpia.contains(it, ignoreCase = true) }) {
        return "Solo se permiten ubicaciones dentro de Suchiapa, Chiapas"
    }
    return null
}

private fun formatearPuntoDetectado(
    displayName: String?,
    busquedaOriginal: String?
): String {
    val limpio = displayName
        ?.replace(Regex(",?\\s*Laredo Texas", RegexOption.IGNORE_CASE), "")
        ?.replace(Regex("\\s{2,}"), " ")
        ?.trim()
        .orEmpty()

    if (limpio.isNotBlank()) return limpio
    val ref = busquedaOriginal?.trim().orEmpty()
    return if (ref.isNotBlank()) "$ref, Suchiapa, Chiapas, México" else "Suchiapa, Chiapas, México"
}

private fun filtrarCoordenadaDecimal(texto: String): String {
    val limpio = texto.filter { it.isDigit() || it == '.' || it == '-' }
    val sinSignosExtra = limpio.replace("-", "")
    val prefijoMenos = if (limpio.startsWith("-")) "-" else ""
    val puntoIdx = sinSignosExtra.indexOf('.')
    val cuerpo = if (puntoIdx == -1) {
        sinSignosExtra
    } else {
        sinSignosExtra.substring(0, puntoIdx + 1) +
                sinSignosExtra.substring(puntoIdx + 1).replace(".", "")
    }
    return prefijoMenos + cuerpo
}

private fun calcularDistanciaMetros(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val radioTierra = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return radioTierra * c
}

private fun formatearDistancia(metros: Double): String =
    if (metros < 1000.0) "${metros.toInt()} m" else "${String.format("%.1f", metros / 1000.0)} km"

@Composable
fun GimnasiosScreen(
    esAdmin: Boolean = false,
    usuarioId: Long = 2L,
    onIrAPase: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val ciudadFija = "Suchiapa"

    var gimnasios by remember { mutableStateOf<List<GimnasioDto>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var soloCercanos by remember { mutableStateOf(false) }
    var gymApartadoNombre by remember { mutableStateOf(obtenerGymApartadoLocal(context, usuarioId)) }
    var membresiaActivaPagada by remember { mutableStateOf(false) }

    var gymParaReservar by remember { mutableStateOf<GimnasioDto?>(null) }
    var planSeleccionado by remember { mutableStateOf(planesMembresiaFitPass[2]) }
    var reservandoMembresia by remember { mutableStateOf(false) }

    var direccionBuscar by remember { mutableStateOf("") }
    var errorDireccionBuscar by remember { mutableStateOf<String?>(null) }
    var ubicacionReferencia by remember { mutableStateOf<UbicacionResponseDto?>(null) }
    var etiquetaBusquedaActiva by remember { mutableStateOf<String?>(null) }
    var esRespaldoZona by remember { mutableStateOf(false) }
    var buscandoGeo by remember { mutableStateOf(false) }

    var nombreGimnasio by remember { mutableStateOf("") }
    var errorNombreGimnasio by remember { mutableStateOf<String?>(null) }
    var direccionGimnasio by remember { mutableStateOf("") }
    var errorDireccionGimnasio by remember { mutableStateOf<String?>(null) }
    var latitudInput by remember { mutableStateOf("") }
    var errorLatitud by remember { mutableStateOf<String?>(null) }
    var longitudInput by remember { mutableStateOf("") }
    var errorLongitud by remember { mutableStateOf<String?>(null) }
    var guardandoGimnasio by remember { mutableStateOf(false) }

    fun cargarGimnasios(
        forzarCercanos: Boolean = soloCercanos,
        refOverride: UbicacionResponseDto? = ubicacionReferencia
    ) {
        scope.launch {
            cargando = true
            try {
                if (usuarioId > 0L) {
                    try {
                        val resMem = RetrofitClient.apiService.obtenerMembresiaUsuario(usuarioId)
                        val mem = if (resMem.isSuccessful) resMem.body()?.datos else null
                        val pagadaActiva = mem != null &&
                                mem.activa &&
                                mem.estadoPago.equals("PAGADO", ignoreCase = true)
                        membresiaActivaPagada = pagadaActiva
                        if (pagadaActiva) {
                            guardarGymApartadoLocal(context, usuarioId, "")
                            gymApartadoNombre = null
                        }
                    } catch (_: Exception) {
                    }
                }

                val refLat = refOverride?.lat?.toDoubleOrNull()
                val refLon = refOverride?.lon?.toDoubleOrNull()

                val resp = if (forzarCercanos) {
                    RetrofitClient.apiService.obtenerGimnasiosCercanos(
                        ciudad = ciudadFija,
                        latitud = refLat,
                        longitud = refLon
                    )
                } else {
                    RetrofitClient.apiService.obtenerGimnasios(ciudadFija)
                }

                val filtrados = if (resp.isSuccessful && resp.body()?.exito == true) {
                    (resp.body()?.datos ?: emptyList()).filter { g ->
                        val c = g.ciudad?.trim().orEmpty()
                        c.isEmpty() || c.equals("Suchiapa", ignoreCase = true) || c.contains("Suchiapa", ignoreCase = true)
                    }
                } else {
                    emptyList()
                }

                gimnasios = if (forzarCercanos && refLat != null && refLon != null) {
                    filtrados.sortedBy { g ->
                        val gLat = g.latitud
                        val gLon = g.longitud
                        if (gLat != null && gLon != null) {
                            calcularDistanciaMetros(refLat, refLon, gLat, gLon)
                        } else {
                            Double.MAX_VALUE
                        }
                    }
                } else {
                    filtrados
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar gimnasios: ${e.message}", Toast.LENGTH_SHORT).show()
                gimnasios = emptyList()
            } finally {
                cargando = false
            }
        }
    }

    fun confirmarReservaEnGym(gym: GimnasioDto, opcionPlan: OpcionPlanGym) {
        if (usuarioId <= 0L) {
            Toast.makeText(context, "No se encontró tu ID de socio", Toast.LENGTH_SHORT).show()
            return
        }
        val nombreGym = gym.nombre?.trim().orEmpty().ifBlank { "Gimnasio" }
        scope.launch {
            reservandoMembresia = true
            try {
                val resp = RetrofitClient.apiService.renovarMembresia(
                    usuarioId = usuarioId,
                    tipoPase = opcionPlan.codigoBackend
                )
                if (resp.isSuccessful && resp.body()?.exito == true) {
                    guardarGymApartadoLocal(context, usuarioId, nombreGym)
                    gymApartadoNombre = nombreGym
                    membresiaActivaPagada = false
                    gymParaReservar = null
                    Toast.makeText(
                        context,
                        resp.body()?.mensaje?.takeIf { it.isNotBlank() }
                            ?: "Apartado en $nombreGym (${opcionPlan.codigoBackend}). Paga en mostrador con tu ID #$usuarioId.",
                        Toast.LENGTH_LONG
                    ).show()
                    onIrAPase()
                } else {
                    val errorMsg = resp.body()?.mensaje?.takeIf { it.isNotBlank() }
                        ?: resp.errorBody()?.string()?.let { raw ->
                            Regex("\"mensaje\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
                        }
                        ?: "No se pudo apartar la membresía (${resp.code()})"
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al reservar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                reservandoMembresia = false
            }
        }
    }

    fun buscarGimnasiosCercaDeLugar() {
        val error = validarDireccionSuchiapa(direccionBuscar, minCaracteres = 3)
        if (error != null) {
            errorDireccionBuscar = error
            return
        }

        errorDireccionBuscar = null
        focusManager.clearFocus()
        val limpia = direccionBuscar
            .trim()
            .replace(Regex("\\bsuchiapa\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bchiapas\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex(",+"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',')
            .ifBlank { direccionBuscar.trim().replace(Regex("\\s+"), " ") }

        val consultas = listOf(
            "$limpia, Suchiapa, Chiapas, México",
            "$limpia, Suchiapa, Chiapas"
        ).distinct()

        fun esEnSuchiapa(ubi: UbicacionResponseDto): Boolean {
            val lat = ubi.lat?.toDoubleOrNull()
            val lon = ubi.lon?.toDoubleOrNull()
            val texto = ubi.display_name.orEmpty()
            val mencionaSuchiapa = texto.contains("suchiapa", ignoreCase = true) ||
                    texto.contains("chiapas", ignoreCase = true)
            val enRangoSuchiapa = lat != null && lon != null &&
                    lat in 16.50..16.75 && lon in -93.25..-92.95
            return mencionaSuchiapa || enRangoSuchiapa
        }

        scope.launch {
            buscandoGeo = true
            try {
                var encontrado: UbicacionResponseDto? = null
                for (q in consultas) {
                    val resp = RetrofitClient.apiService.buscarUbicacion(q)
                    if (resp.isSuccessful && resp.code() != 204) {
                        encontrado = resp.body().orEmpty().firstOrNull {
                            !it.lat.isNullOrBlank() && !it.lon.isNullOrBlank() && esEnSuchiapa(it)
                        }
                    }
                    if (encontrado != null) break
                }

                if (encontrado != null) {
                    ubicacionReferencia = encontrado
                    etiquetaBusquedaActiva = limpia
                    esRespaldoZona = false
                    soloCercanos = true
                    if (esAdmin) {
                        latitudInput = encontrado.lat.orEmpty()
                        longitudInput = encontrado.lon.orEmpty()
                        errorLatitud = null
                        errorLongitud = null
                    }
                    cargarGimnasios(forzarCercanos = true, refOverride = encontrado)
                } else {
                    val matchLocal = gimnasios.firstOrNull { g ->
                        g.latitud != null && g.longitud != null && (
                                g.nombre?.contains(limpia, ignoreCase = true) == true ||
                                        g.direccion?.contains(limpia, ignoreCase = true) == true
                                )
                    }

                    val ubiFallback = if (matchLocal != null && matchLocal.latitud != null && matchLocal.longitud != null) {
                        UbicacionResponseDto(
                            place_id = null,
                            display_name = "${matchLocal.direccion ?: matchLocal.nombre}, Suchiapa, Chiapas, México",
                            lat = matchLocal.latitud.toString(),
                            lon = matchLocal.longitud.toString(),
                            type = "local",
                            importance = null
                        )
                    } else {
                        UbicacionResponseDto(
                            place_id = null,
                            display_name = "Zona de referencia en Suchiapa, Chiapas, México",
                            lat = LAT_RESPALDO_SUCHIAPA.toString(),
                            lon = LON_RESPALDO_SUCHIAPA.toString(),
                            type = "fallback_suchiapa",
                            importance = null
                        )
                    }

                    ubicacionReferencia = ubiFallback
                    etiquetaBusquedaActiva = limpia
                    esRespaldoZona = matchLocal == null
                    soloCercanos = true
                    cargarGimnasios(forzarCercanos = true, refOverride = ubiFallback)
                    Toast.makeText(
                        context,
                        "No se encontró \"$limpia\" exacto en el mapa; mostrando gimnasios cercanos en Suchiapa.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error de geolocalización: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                buscandoGeo = false
            }
        }
    }

    fun registrarGimnasioSuchiapa() {
        val nombre = nombreGimnasio.trim().replace(Regex("\\s+"), " ")
        val direccion = direccionGimnasio.trim().replace(Regex("\\s+"), " ")
        val lat = latitudInput.trim().toDoubleOrNull()
        val lon = longitudInput.trim().toDoubleOrNull()

        errorNombreGimnasio = when {
            nombre.length < 2 -> "Ingresa el nombre (mín. 2 caracteres)"
            nombre.count { it.isLetter() } < 2 || Regex("(.)\\1{3,}").containsMatchIn(nombre) ->
                "Ingresa un nombre válido para el gimnasio"
            else -> null
        }
        errorDireccionGimnasio = validarDireccionSuchiapa(direccion, minCaracteres = 4)
        errorLatitud = if (lat == null || lat !in 15.8..16.8) "Latitud válida para Suchiapa (ej. 16.62)" else null
        errorLongitud = if (lon == null || lon !in -93.5..-92.7) "Longitud válida para Suchiapa (ej. -93.10)" else null

        if (
            errorNombreGimnasio != null ||
            errorDireccionGimnasio != null ||
            errorLatitud != null ||
            errorLongitud != null ||
            lat == null ||
            lon == null
        ) {
            return
        }

        focusManager.clearFocus()
        scope.launch {
            guardandoGimnasio = true
            try {
                val resp = RetrofitClient.apiService.crearGimnasio(
                    GimnasioRequestDto(
                        nombre = nombre,
                        direccion = direccion,
                        ciudad = ciudadFija,
                        latitud = lat,
                        longitud = lon
                    )
                )
                if (resp.isSuccessful && resp.body()?.exito == true) {
                    Toast.makeText(context, "Gimnasio \"$nombre\" registrado en Suchiapa", Toast.LENGTH_LONG).show()
                    nombreGimnasio = ""
                    direccionGimnasio = ""
                    latitudInput = ""
                    longitudInput = ""
                    soloCercanos = false
                    cargarGimnasios(forzarCercanos = false)
                } else {
                    Toast.makeText(
                        context,
                        resp.body()?.mensaje?.takeIf { it.isNotBlank() } ?: "No se pudo registrar (${resp.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                guardandoGimnasio = false
            }
        }
    }

    LaunchedEffect(Unit) {
        cargarGimnasios()
    }

    gymParaReservar?.let { gym ->
        AlertDialog(
            onDismissRequest = { if (!reservandoMembresia) gymParaReservar = null },
            containerColor = gymCardBg,
            titleContentColor = Color.White,
            textContentColor = gymTextoGris,
            title = {
                Text(
                    "Apartar en ${gym.nombre ?: "Gimnasio"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Elige tu plan para reservarlo e ir a pagar a recepción (ID Socio: #$usuarioId). Al confirmar tu pago se liberará tu QR:",
                        fontSize = 12.sp
                    )
                    planesMembresiaFitPass.forEach { opcion ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = planSeleccionado == opcion,
                                onClick = { if (!reservandoMembresia) planSeleccionado = opcion },
                                colors = RadioButtonDefaults.colors(selectedColor = gymNaranja)
                            )
                            Text(
                                text = opcion.etiquetaVisible,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !reservandoMembresia,
                    onClick = { confirmarReservaEnGym(gym, planSeleccionado) },
                    colors = ButtonDefaults.buttonColors(containerColor = gymNaranja, contentColor = Color.White)
                ) {
                    Text(if (reservandoMembresia) "Apartando..." else "Apartar membresía", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !reservandoMembresia,
                    onClick = { gymParaReservar = null }
                ) {
                    Text("Cancelar", color = gymTextoGris, fontSize = 12.sp)
                }
            }
        )
    }

    val refLatActual = ubicacionReferencia?.lat?.toDoubleOrNull()
    val refLonActual = ubicacionReferencia?.lon?.toDoubleOrNull()
    val tieneReferenciaActiva = soloCercanos && refLatActual != null && refLonActual != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gymFondo)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Gimnasios • Suchiapa", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Elige una sede para apartar tu membresía y pagar en mostrador", fontSize = 11.sp, color = gymTextoGris)
            }
            Button(
                onClick = { cargarGimnasios() },
                colors = ButtonDefaults.buttonColors(containerColor = gymCardBg, contentColor = gymNaranja),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Actualizar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!membresiaActivaPagada && !gymApartadoNombre.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2E1E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sede apartada: $gymApartadoNombre", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                        Text("ID Socio #$usuarioId • Paga en mostrador para activar tu QR", fontSize = 11.sp, color = gymTextoGris)
                    }
                    TextButton(onClick = onIrAPase) {
                        Text("Ver QR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gymNaranja)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !soloCercanos,
                onClick = {
                    soloCercanos = false
                    ubicacionReferencia = null
                    etiquetaBusquedaActiva = null
                    esRespaldoZona = false
                    cargarGimnasios(forzarCercanos = false, refOverride = null)
                },
                label = { Text("Todos (Suchiapa)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = gymCardBg,
                    labelColor = Color.White,
                    selectedContainerColor = gymNaranja,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = soloCercanos,
                onClick = {
                    soloCercanos = true
                    cargarGimnasios(forzarCercanos = true)
                },
                label = { Text("Cercanos", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = gymCardBg,
                    labelColor = Color.White,
                    selectedContainerColor = gymNaranja,
                    selectedLabelColor = Color.White
                )
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = gymCardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿Dónde te encuentras en Suchiapa?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                Text("Escribe cualquier referencia, calle o lugar de Suchiapa para calcular cercanía:", fontSize = 11.sp, color = gymTextoGris)
                OutlinedTextField(
                    value = direccionBuscar,
                    onValueChange = {
                        direccionBuscar = filtrarTextoDireccion(it)
                        errorDireccionBuscar = null
                    },
                    label = { Text("Punto de referencia en Suchiapa", fontSize = 12.sp) },
                    placeholder = { Text("Escribe tu ubicación o referencia aquí", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                    isError = errorDireccionBuscar != null,
                    supportingText = {
                        errorDireccionBuscar?.let { Text(it, color = gymRojoError, fontSize = 11.sp) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (!buscandoGeo) buscarGimnasiosCercaDeLugar() }),
                    singleLine = true,
                    enabled = !buscandoGeo,
                    colors = gymCampoColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { buscarGimnasiosCercaDeLugar() },
                    enabled = !buscandoGeo,
                    colors = ButtonDefaults.buttonColors(containerColor = gymNaranja, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text(if (buscandoGeo) "Buscando gimnasios cercanos..." else "Buscar gimnasios cercanos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (ubicacionReferencia != null) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = gymSubCardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (esRespaldoZona) "Referencia aproximada en Suchiapa:" else "Punto detectado:",
                                    fontSize = 10.sp,
                                    color = if (esRespaldoZona) Color(0xFFFF9800) else Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatearPuntoDetectado(
                                        displayName = ubicacionReferencia?.display_name,
                                        busquedaOriginal = etiquetaBusquedaActiva
                                    ),
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 2
                                )
                            }
                            TextButton(
                                onClick = {
                                    ubicacionReferencia = null
                                    etiquetaBusquedaActiva = null
                                    esRespaldoZona = false
                                    direccionBuscar = ""
                                    soloCercanos = false
                                    cargarGimnasios(forzarCercanos = false, refOverride = null)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Limpiar", fontSize = 11.sp, color = gymRojoError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (soloCercanos) {
            val textoOrden = if (tieneReferenciaActiva && !etiquetaBusquedaActiva.isNullOrBlank()) {
                "Ordenados del más cercano al más lejano respecto a: $etiquetaBusquedaActiva"
            } else {
                "Vista de gimnasios cercanos en Suchiapa (escribe una referencia arriba para calcular distancia)"
            }
            Text(
                text = textoOrden,
                fontSize = 11.sp,
                color = Color(0xFFFF9800),
                fontWeight = FontWeight.Bold
            )
        }

        if (esAdmin) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = gymCardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Registrar Gimnasio en Suchiapa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    OutlinedTextField(
                        value = nombreGimnasio,
                        onValueChange = {
                            nombreGimnasio = filtrarTextoDireccion(it)
                            errorNombreGimnasio = null
                        },
                        label = { Text("Nombre del gimnasio", fontSize = 12.sp) },
                        isError = errorNombreGimnasio != null,
                        supportingText = {
                            errorNombreGimnasio?.let { Text(it, color = gymRojoError, fontSize = 11.sp) }
                        },
                        singleLine = true,
                        enabled = !guardandoGimnasio,
                        colors = gymCampoColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = direccionGimnasio,
                        onValueChange = {
                            direccionGimnasio = filtrarTextoDireccion(it)
                            errorDireccionGimnasio = null
                        },
                        label = { Text("Dirección en Suchiapa", fontSize = 12.sp) },
                        isError = errorDireccionGimnasio != null,
                        supportingText = {
                            errorDireccionGimnasio?.let { Text(it, color = gymRojoError, fontSize = 11.sp) }
                        },
                        singleLine = true,
                        enabled = !guardandoGimnasio,
                        colors = gymCampoColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitudInput,
                            onValueChange = {
                                latitudInput = filtrarCoordenadaDecimal(it)
                                errorLatitud = null
                            },
                            label = { Text("Latitud", fontSize = 12.sp) },
                            isError = errorLatitud != null,
                            supportingText = {
                                errorLatitud?.let { Text(it, color = gymRojoError, fontSize = 11.sp) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true,
                            enabled = !guardandoGimnasio,
                            colors = gymCampoColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = longitudInput,
                            onValueChange = {
                                longitudInput = filtrarCoordenadaDecimal(it)
                                errorLongitud = null
                            },
                            label = { Text("Longitud", fontSize = 12.sp) },
                            isError = errorLongitud != null,
                            supportingText = {
                                errorLongitud?.let { Text(it, color = gymRojoError, fontSize = 11.sp) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (!guardandoGimnasio) registrarGimnasioSuchiapa() }),
                            singleLine = true,
                            enabled = !guardandoGimnasio,
                            colors = gymCampoColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { registrarGimnasioSuchiapa() },
                        enabled = !guardandoGimnasio,
                        colors = ButtonDefaults.buttonColors(containerColor = gymNaranja, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text(if (guardandoGimnasio) "Guardando..." else "Guardar Gimnasio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (cargando) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = gymNaranja)
            }
        } else if (gimnasios.isEmpty()) {
            Text("No hay gimnasios registrados en Suchiapa todavía.", fontSize = 12.sp, color = gymTextoGris)
        } else {
            gimnasios.forEachIndexed { index, gym ->
                val distanciaTexto = if (tieneReferenciaActiva && gym.latitud != null && gym.longitud != null) {
                    val metros = calcularDistanciaMetros(refLatActual, refLonActual, gym.latitud, gym.longitud)
                    " • A ${formatearDistancia(metros)}"
                } else {
                    ""
                }
                val prefijoRanking = if (tieneReferenciaActiva) "#${index + 1} " else ""
                val esEstaSedeApartada = !membresiaActivaPagada &&
                        !gymApartadoNombre.isNullOrBlank() &&
                        gym.nombre?.equals(gymApartadoNombre, ignoreCase = true) == true

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = gymCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "$prefijoRanking${gym.nombre ?: "Gimnasio"}$distanciaTexto",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text("${gym.direccion ?: "Sin dirección"} • ${gym.ciudad ?: "Suchiapa"}", fontSize = 12.sp, color = gymTextoGris)
                        if (gym.latitud != null && gym.longitud != null) {
                            Text("Coords: ${gym.latitud}, ${gym.longitud}", fontSize = 11.sp, color = Color(0xFF81C784))
                        } else {
                            Text("Sin coordenadas registradas para calcular distancia", fontSize = 11.sp, color = Color(0xFFFF9800))
                        }
                        Button(
                            onClick = { gymParaReservar = gym },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (esEstaSedeApartada) Color(0xFF2E7D32) else gymNaranja,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 2.dp)
                        ) {
                            Text(
                                text = if (esEstaSedeApartada) "Apartado aquí • Cambiar plan" else "Seleccionar y apartar membresía",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
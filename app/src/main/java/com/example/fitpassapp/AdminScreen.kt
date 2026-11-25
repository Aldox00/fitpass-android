package com.example.fitpassapp

import android.util.Log
import android.util.Patterns
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private val naranjaFitPass = Color(0xFFFF5722)
private val fondoOscuro = Color(0xFF121212)
private val fondoCard = Color(0xFF1E1E1E)
private val fondoSubCard = Color(0xFF2A2A2A)
private val fondoInput = Color(0xFF141416)
private val textoSecundario = Color(0xFFB0B0B0)
private val labelSecundario = Color(0xFF9E9E9E)
private val rojoError = Color(0xFFFF5252)

private data class GrupoApartadoSocio(
    val usuarioId: Long,
    val nombreUsuario: String,
    val items: List<ApartadoResponseDto>,
    val totalGeneral: Double
)

@Composable
private fun campoOscuroColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorTextColor = Color.White,
    disabledTextColor = Color.Gray,
    cursorColor = naranjaFitPass,
    errorCursorColor = rojoError,
    focusedBorderColor = naranjaFitPass,
    unfocusedBorderColor = Color(0xFF48484A),
    errorBorderColor = rojoError,
    focusedLabelColor = naranjaFitPass,
    unfocusedLabelColor = labelSecundario,
    errorLabelColor = rojoError,
    focusedContainerColor = fondoInput,
    unfocusedContainerColor = fondoInput,
    errorContainerColor = fondoInput
)

private fun resolverPrecioMembresia(tipoPase: String?, precioBackend: Double?): Double {
    if (precioBackend != null && precioBackend > 0.0) return precioBackend
    return when (tipoPase?.trim()?.lowercase()) {
        "pase por día (visita)", "pase por dia (visita)", "pase por día", "pase por dia", "visita" -> 50.0
        "plan semanal", "semanal" -> 150.0
        "plan mensual", "mensual", "membresía mensual", "membresia mensual" -> 450.0
        "plan anual", "anual" -> 4200.0
        else -> 0.0
    }
}

private fun formatearMonto(monto: Double): String =
    if (monto % 1.0 == 0.0) "$${monto.toInt()}" else "$${String.format("%.2f", monto)}"

private fun filtrarCuerpoCorreo(texto: String): String = texto.filter { ch ->
    ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
            ch == '@' || ch == '.' || ch == '_' || ch == '-' || ch == '+'
}

private fun filtrarNombreProducto(texto: String): String = texto.filter { ch ->
    ch.isLetter() || ch == ' '
}

private fun validarNombreProducto(nombreRaw: String): String? {
    val limpio = nombreRaw.trim().replace(Regex("\\s+"), " ")
    return when {
        limpio.length < 2 -> "Ingresa el nombre (mín. 2 letras)"
        limpio.length > 60 -> "Máximo 60 caracteres"
        Regex("(.)\\1{2,}", RegexOption.IGNORE_CASE).containsMatchIn(limpio) ->
            "Evita repetir la misma letra tantas veces seguidas"
        else -> null
    }
}

private fun filtrarPrecioDecimal(texto: String): String {
    val limpio = texto.filter { it.isDigit() || it == '.' }
    val idx = limpio.indexOf('.')
    if (idx == -1) return limpio
    return limpio.substring(0, idx + 1) + limpio.substring(idx + 1).replace(".", "").take(2)
}

private fun esCorreoValido(correo: String): Boolean {
    val limpio = correo.trim()
    if (limpio.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(limpio).matches()) return false
    val partes = limpio.split("@")
    if (partes.size != 2 || partes[0].isEmpty() || !partes[1].contains(".")) return false
    val ext = partes[1].substringAfterLast(".")
    return ext.length >= 2 && ext.all { it in 'a'..'z' || it in 'A'..'Z' }
}

@Composable
fun AdminScreen(
    onCerrarSesion: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var pestanaAdmin by remember { mutableStateOf(0) }
    var productos by remember { mutableStateOf<List<ProductoDto>>(emptyList()) }
    var apartadosActivos by remember { mutableStateOf<List<ApartadoResponseDto>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var cobrandoSocioId by remember { mutableStateOf<Long?>(null) }
    var filtroIdSocioApartado by remember { mutableStateOf("") }

    var nombreProductoInput by remember { mutableStateOf("") }
    var errorNombreProducto by remember { mutableStateOf<String?>(null) }
    var precioProductoInput by remember { mutableStateOf("") }
    var errorPrecioProducto by remember { mutableStateOf<String?>(null) }
    var stockProductoInput by remember { mutableStateOf("") }
    var errorStockProducto by remember { mutableStateOf<String?>(null) }
    var creandoProducto by remember { mutableStateOf(false) }

    var productoEditando by remember { mutableStateOf<ProductoDto?>(null) }
    var editNombreInput by remember { mutableStateOf("") }
    var editPrecioInput by remember { mutableStateOf("") }
    var editStockInput by remember { mutableStateOf("") }
    var guardandoEdicion by remember { mutableStateOf(false) }

    var productoAEliminar by remember { mutableStateOf<ProductoDto?>(null) }
    var eliminandoProducto by remember { mutableStateOf(false) }

    var idSocioInput by remember { mutableStateOf("") }
    var errorIdSocio by remember { mutableStateOf<String?>(null) }
    var socioBuscado by remember { mutableStateOf<String?>(null) }
    var membresiaEncontrada by remember { mutableStateOf<MembresiaDto?>(null) }
    var buscandoSocio by remember { mutableStateOf(false) }
    var confirmandoPago by remember { mutableStateOf(false) }

    var correoAdminInput by remember { mutableStateOf("") }
    var errorCorreoAdmin by remember { mutableStateOf<String?>(null) }
    var asignandoAdmin by remember { mutableStateOf(false) }

    fun cargarDatosAdmin() {
        scope.launch {
            cargando = true
            try {
                val response = RetrofitClient.apiService.obtenerProductos()
                val body = if (response.isSuccessful) response.body()?.string().orEmpty() else ""
                productos = if (body.isNotBlank()) {
                    try {
                        val apiType = object : TypeToken<ApiResponseDto<List<ProductoDto>>>() {}.type
                        val parsed: ApiResponseDto<List<ProductoDto>>? = Gson().fromJson(body, apiType)
                        if (parsed != null && parsed.exito && parsed.datos != null) {
                            parsed.datos
                        } else {
                            val listType = object : TypeToken<List<ProductoDto>>() {}.type
                            Gson().fromJson<List<ProductoDto>>(body, listType) ?: emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e("AdminScreen", "Error al parsear JSON: ${e.message}")
                        emptyList()
                    }
                } else emptyList()

                val respApartados = RetrofitClient.apiService.obtenerApartadosPendientes()
                apartadosActivos = if (respApartados.isSuccessful && respApartados.body()?.exito == true) {
                    respApartados.body()?.datos ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.e("AdminScreen", "Error de red: ${e.message}")
                productos = emptyList()
                apartadosActivos = emptyList()
            } finally {
                cargando = false
            }
        }
    }

    fun agregarProductoNuevo() {
        val nombreLimpio = nombreProductoInput.trim().replace(Regex("\\s+"), " ")
        val precioNum = precioProductoInput.trim().toDoubleOrNull()
        val stockNum = stockProductoInput.trim().toIntOrNull()

        errorNombreProducto = validarNombreProducto(nombreLimpio)
        errorPrecioProducto = if (precioNum == null || precioNum <= 0.0) "Precio mayor a 0" else null
        errorStockProducto = if (stockNum == null || stockNum < 0) "Stock entero >= 0" else null

        if (errorNombreProducto != null || errorPrecioProducto != null || errorStockProducto != null) return
        if (precioNum == null || stockNum == null) return

        focusManager.clearFocus()
        scope.launch {
            creandoProducto = true
            try {
                val resp = RetrofitClient.apiService.crearProducto(
                    ProductoRequestDto(nombreLimpio, precioNum, stockNum)
                )
                if (resp.isSuccessful && resp.body()?.exito == true) {
                    Toast.makeText(context, "Producto \"$nombreLimpio\" agregado", Toast.LENGTH_LONG).show()
                    nombreProductoInput = ""
                    precioProductoInput = ""
                    stockProductoInput = ""
                    cargarDatosAdmin()
                } else {
                    Toast.makeText(
                        context,
                        resp.body()?.mensaje?.takeIf { it.isNotBlank() } ?: "Error (${resp.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al agregar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                creandoProducto = false
            }
        }
    }

    fun abrirEditorProducto(prod: ProductoDto) {
        productoEditando = prod
        editNombreInput = filtrarNombreProducto(prod.nombre.orEmpty())
        val precioVal = prod.precio ?: 0.0
        editPrecioInput = if (precioVal % 1.0 == 0.0) precioVal.toInt().toString() else precioVal.toString()
        editStockInput = (prod.stock ?: 0).toString()
    }

    fun guardarEdicionProducto() {
        val prod = productoEditando ?: return
        val id = prod.id ?: return
        val nombreLimpio = editNombreInput.trim().replace(Regex("\\s+"), " ")
        val precioNum = editPrecioInput.trim().toDoubleOrNull()
        val stockNum = editStockInput.trim().toIntOrNull()

        val errNombre = validarNombreProducto(nombreLimpio)
        if (errNombre != null) {
            Toast.makeText(context, errNombre, Toast.LENGTH_SHORT).show()
            return
        }
        if (precioNum == null || precioNum <= 0.0) {
            Toast.makeText(context, "Ingresa un precio mayor a 0", Toast.LENGTH_SHORT).show()
            return
        }
        if (stockNum == null || stockNum < 0) {
            Toast.makeText(context, "El stock debe ser 0 o mayor", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            guardandoEdicion = true
            try {
                val resp = RetrofitClient.apiService.actualizarProducto(
                    productoId = id,
                    request = ProductoRequestDto(nombreLimpio, precioNum, stockNum)
                )
                if (resp.isSuccessful && resp.body()?.exito == true) {
                    Toast.makeText(context, "Producto actualizado", Toast.LENGTH_SHORT).show()
                    productoEditando = null
                    cargarDatosAdmin()
                } else {
                    Toast.makeText(
                        context,
                        resp.body()?.mensaje?.takeIf { it.isNotBlank() } ?: "No se pudo actualizar (${resp.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                guardandoEdicion = false
            }
        }
    }

    fun eliminarProductoSeleccionado() {
        val prod = productoAEliminar ?: return
        val id = prod.id ?: return
        scope.launch {
            eliminandoProducto = true
            try {
                val resp = RetrofitClient.apiService.eliminarProducto(id)
                if (resp.isSuccessful && resp.body()?.exito != false) {
                    Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
                    productoAEliminar = null
                    cargarDatosAdmin()
                } else {
                    Toast.makeText(
                        context,
                        resp.body()?.mensaje?.takeIf { it.isNotBlank() }
                            ?: "No se pudo eliminar (${resp.code()}). Verifica si tiene apartados activos.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                eliminandoProducto = false
            }
        }
    }

    fun cobrarGrupoSocio(grupo: GrupoApartadoSocio) {
        if (grupo.usuarioId <= 0L) return
        scope.launch {
            cobrandoSocioId = grupo.usuarioId
            try {
                val resp = RetrofitClient.apiService.cobrarApartadosSocio(grupo.usuarioId)
                if (resp.isSuccessful && resp.body()?.exito == true) {
                    Toast.makeText(
                        context,
                        "Cobro completado de Socio #${grupo.usuarioId} (${formatearMonto(grupo.totalGeneral)})",
                        Toast.LENGTH_LONG
                    ).show()
                    cargarDatosAdmin()
                } else {
                    Toast.makeText(
                        context,
                        resp.body()?.mensaje?.takeIf { it.isNotBlank() } ?: "No se pudo completar el cobro",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cobrar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                cobrandoSocioId = null
            }
        }
    }

    fun buscarSocioPorId() {
        val limpio = idSocioInput.trim()
        val usuarioId = limpio.toLongOrNull()
        if (limpio.isEmpty() || usuarioId == null || usuarioId <= 0L) {
            errorIdSocio = "Ingresa un ID numérico válido mayor a 0"
            socioBuscado = null
            membresiaEncontrada = null
            return
        }
        errorIdSocio = null
        focusManager.clearFocus()
        scope.launch {
            buscandoSocio = true
            try {
                val resp = RetrofitClient.apiService.obtenerMembresiaUsuario(usuarioId)
                socioBuscado = limpio
                membresiaEncontrada = if (resp.isSuccessful) resp.body()?.datos else null
                Toast.makeText(
                    context,
                    if (membresiaEncontrada != null) "Membresía localizada"
                    else resp.body()?.mensaje ?: "Socio sin membresía registrada",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                socioBuscado = limpio
                membresiaEncontrada = null
                Toast.makeText(context, "Error al consultar: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                buscandoSocio = false
            }
        }
    }

    fun otorgarPermisosAdmin() {
        val correo = filtrarCuerpoCorreo(correoAdminInput).trim().lowercase()
        correoAdminInput = correo
        if (!esCorreoValido(correo)) {
            errorCorreoAdmin = "Ingresa un correo válido (ej. usuario@dominio.com)"
            return
        }
        errorCorreoAdmin = null
        focusManager.clearFocus()
        scope.launch {
            asignandoAdmin = true
            try {
                val resp = RetrofitClient.apiService.promoverAdmin(correo)
                if (resp.isSuccessful && resp.body()?.exito == true) {
                    Toast.makeText(context, "Admin concedido a $correo", Toast.LENGTH_LONG).show()
                    correoAdminInput = ""
                } else {
                    errorCorreoAdmin = resp.body()?.mensaje ?: "No se pudo otorgar permisos"
                }
            } catch (e: Exception) {
                errorCorreoAdmin = "Error al asignar rol: ${e.message}"
                Toast.makeText(context, errorCorreoAdmin, Toast.LENGTH_LONG).show()
            } finally {
                asignandoAdmin = false
            }
        }
    }

    LaunchedEffect(Unit) {
        cargarDatosAdmin()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondoOscuro)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Panel de Administración", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Control total de recepción y operaciones FitPass", fontSize = 11.sp, color = textoSecundario)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { cargarDatosAdmin() },
                    colors = ButtonDefaults.buttonColors(containerColor = fondoCard, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("Actualizar", fontSize = 11.sp) }

                Button(
                    onClick = onCerrarSesion,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("Salir", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tienda", "Membresías", "Usuarios").forEachIndexed { index, titulo ->
                Button(
                    onClick = { pestanaAdmin = index },
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pestanaAdmin == index) naranjaFitPass else fondoCard,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (pestanaAdmin) {
            0 -> {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = fondoCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Agregar Producto al Inventario", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                        Text("Registra un nuevo producto disponible en la tienda:", fontSize = 12.sp, color = textoSecundario)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = nombreProductoInput,
                            onValueChange = {
                                nombreProductoInput = filtrarNombreProducto(it)
                                errorNombreProducto = null
                            },
                            label = { Text("Nombre del producto", fontSize = 12.sp) },
                            placeholder = { Text("Ej. Pre entreno", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                            isError = errorNombreProducto != null,
                            supportingText = { errorNombreProducto?.let { Text(it, color = rojoError, fontSize = 11.sp) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !creandoProducto,
                            colors = campoOscuroColors()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = precioProductoInput,
                                onValueChange = { precioProductoInput = filtrarPrecioDecimal(it); errorPrecioProducto = null },
                                label = { Text("Precio ($)", fontSize = 12.sp) },
                                placeholder = { Text("350", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                                isError = errorPrecioProducto != null,
                                supportingText = { errorPrecioProducto?.let { Text(it, color = rojoError, fontSize = 11.sp) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !creandoProducto,
                                colors = campoOscuroColors()
                            )

                            OutlinedTextField(
                                value = stockProductoInput,
                                onValueChange = { stockProductoInput = it.filter { c -> c.isDigit() }; errorStockProducto = null },
                                label = { Text("Stock", fontSize = 12.sp) },
                                placeholder = { Text("10", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                                isError = errorStockProducto != null,
                                supportingText = { errorStockProducto?.let { Text(it, color = rojoError, fontSize = 11.sp) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (!creandoProducto) agregarProductoNuevo() }),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !creandoProducto,
                                colors = campoOscuroColors()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { agregarProductoNuevo() },
                            enabled = !creandoProducto,
                            colors = ButtonDefaults.buttonColors(containerColor = naranjaFitPass, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (creandoProducto) "Guardando producto..." else "Guardar Producto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = fondoCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Apartados Activos de Socios", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                        Text("Identifica al socio por su ID para cobrar sus productos:", fontSize = 12.sp, color = textoSecundario)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = filtroIdSocioApartado,
                            onValueChange = { filtroIdSocioApartado = it.filter { c -> c.isDigit() } },
                            label = { Text("Filtrar por ID de socio", fontSize = 11.sp) },
                            placeholder = { Text("Ej. 1", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = campoOscuroColors()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val filtro = filtroIdSocioApartado.trim()
                        val grupos = apartadosActivos
                            .groupBy { it.usuarioId ?: 0L }
                            .map { (socioId, items) ->
                                val nombreSocio = items.firstNotNullOfOrNull { it.nombreUsuario?.takeIf { n -> n.isNotBlank() } } ?: "Socio"
                                val total = items.sumOf { (it.precioUnitario ?: 0.0) * (it.cantidad ?: 1) }
                                GrupoApartadoSocio(socioId, nombreSocio, items, total)
                            }
                            .filter { filtro.isEmpty() || it.usuarioId.toString() == filtro }

                        if (grupos.isEmpty()) {
                            Text(
                                text = if (filtro.isNotEmpty()) "No hay apartados para #$filtro" else "No hay apartados pendientes por cobrar",
                                fontSize = 12.sp,
                                color = labelSecundario,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            )
                        } else {
                            grupos.forEach { grupo ->
                                val procesando = cobrandoSocioId == grupo.usuarioId
                                val titulo = if (grupo.usuarioId > 0L) "ID Socio #${grupo.usuarioId} • ${grupo.nombreUsuario}" else grupo.nombreUsuario

                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = fondoSubCard), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784), modifier = Modifier.weight(1f))
                                            Text("Total: ${formatearMonto(grupo.totalGeneral)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = naranjaFitPass)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        grupo.items.forEach { item ->
                                            val cant = item.cantidad ?: 1
                                            val sub = (item.precioUnitario ?: 0.0) * cant
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("• ${item.nombreProducto ?: "Producto"} (x$cant)", fontSize = 12.sp, color = Color.White)
                                                Text(formatearMonto(sub), fontSize = 12.sp, color = textoSecundario)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { cobrarGrupoSocio(grupo) },
                                            enabled = cobrandoSocioId == null,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(34.dp)
                                        ) {
                                            Text(
                                                text = if (procesando) "Cobrando pedido..." else "Cobrar y Entregar (${formatearMonto(grupo.totalGeneral)})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Inventario General de Productos", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(10.dp))

                if (cargando) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = naranjaFitPass)
                    }
                } else if (productos.isEmpty()) {
                    Text("No hay productos registrados en el sistema.", fontSize = 13.sp, color = textoSecundario)
                } else {
                    productos.forEach { producto ->
                        val stock = producto.stock ?: 0
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = fondoCard), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(producto.nombre ?: "Sin nombre", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Precio: ${formatearMonto(producto.precio ?: 0.0)}", fontSize = 11.sp, color = textoSecundario)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                                        Text("Stock", fontSize = 10.sp, color = textoSecundario)
                                        Text("$stock", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (stock > 0) Color(0xFF4CAF50) else rojoError)
                                    }
                                    TextButton(
                                        onClick = { abrirEditorProducto(producto) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = naranjaFitPass)
                                    }
                                    if (producto.id != null) {
                                        TextButton(
                                            onClick = { productoAEliminar = producto },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Eliminar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = rojoError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = fondoCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Confirmación de Membresías", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = naranjaFitPass)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ingresa el ID numérico del socio para validar su plan y cobrar en recepción:", fontSize = 12.sp, color = textoSecundario, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = idSocioInput,
                            onValueChange = { idSocioInput = it.filter { c -> c.isDigit() }; errorIdSocio = null },
                            label = { Text("ID numérico del socio", fontSize = 12.sp) },
                            placeholder = { Text("Ej. 1", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                            isError = errorIdSocio != null,
                            supportingText = { errorIdSocio?.let { Text(it, color = rojoError, fontSize = 11.sp) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (!buscandoSocio) buscarSocioPorId() }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !buscandoSocio && !confirmandoPago,
                            colors = campoOscuroColors()
                        )

                        Button(
                            onClick = { buscarSocioPorId() },
                            enabled = !buscandoSocio && !confirmandoPago && idSocioInput.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(if (buscandoSocio) "Buscando..." else "Buscar Socio", fontSize = 12.sp, fontWeight = FontWeight.Bold) }

                        if (socioBuscado != null) {
                            val mem = membresiaEncontrada
                            val yaTieneActiva = mem != null &&
                                    mem.activa &&
                                    mem.estadoPago.equals("PAGADO", ignoreCase = true)
                            val monto = resolverPrecioMembresia(mem?.tipoPase, mem?.precio)

                            Spacer(modifier = Modifier.height(16.dp))
                            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = fondoSubCard), modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Socio: #$socioBuscado", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                    if (yaTieneActiva) {
                                        Text(
                                            text = "Este usuario con ID #$socioBuscado ya tiene su membresía activa.",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81C784)
                                        )
                                        Text(
                                            text = "Plan: ${mem?.tipoPase ?: "Membresía"} (${mem?.estadoPago ?: "PAGADO"})",
                                            fontSize = 12.sp,
                                            color = textoSecundario
                                        )
                                        if (!mem?.fechaFin.isNullOrBlank()) {
                                            Text(
                                                text = "Vigente hasta: ${mem?.fechaFin}",
                                                fontSize = 12.sp,
                                                color = textoSecundario
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Plan: ${mem?.tipoPase ?: "Plan Mensual"} (${mem?.estadoPago ?: "Pendiente"})",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF9800)
                                        )
                                        Text(
                                            text = if (monto > 0.0) "A pagar en recepción: ${formatearMonto(monto)}" else "A pagar: Consultar administración",
                                            fontSize = 13.sp,
                                            color = Color(0xFF81C784),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                val idNum = socioBuscado?.toLongOrNull() ?: return@Button
                                                scope.launch {
                                                    confirmandoPago = true
                                                    try {
                                                        val resp = mem?.id?.let {
                                                            RetrofitClient.apiService.confirmarPago(it)
                                                        } ?: RetrofitClient.apiService.confirmarPagoPorUsuarioId(idNum)
                                                        if (resp.isSuccessful && resp.body()?.exito == true) {
                                                            Toast.makeText(context, "¡Membresía activada para #$socioBuscado!", Toast.LENGTH_LONG).show()
                                                            membresiaEncontrada = resp.body()?.datos ?: mem?.copy(
                                                                activa = true,
                                                                estadoPago = "PAGADO"
                                                            )
                                                        } else {
                                                            val msgError = resp.body()?.mensaje?.takeIf { it.isNotBlank() }
                                                                ?: resp.errorBody()?.string()?.let { raw ->
                                                                    Regex("\"mensaje\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
                                                                }
                                                                ?: "No se pudo confirmar"
                                                            Toast.makeText(context, msgError, Toast.LENGTH_LONG).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        confirmandoPago = false
                                                    }
                                                }
                                            },
                                            enabled = !confirmandoPago && !buscandoSocio,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) { Text(if (confirmandoPago) "Confirmando..." else "Confirmar Pago de Membresía", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = fondoCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gestión de Accesos y Roles", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Asigna permisos de Administrador a otros usuarios o empleados.", fontSize = 12.sp, color = textoSecundario, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = correoAdminInput,
                            onValueChange = { correoAdminInput = filtrarCuerpoCorreo(it); errorCorreoAdmin = null },
                            label = { Text("Correo del usuario", fontSize = 12.sp) },
                            placeholder = { Text("ejemplo@fitpass.com", color = Color(0xFF6E6E73), fontSize = 12.sp) },
                            isError = errorCorreoAdmin != null,
                            supportingText = { errorCorreoAdmin?.let { Text(it, color = rojoError, fontSize = 11.sp) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (!asignandoAdmin) otorgarPermisosAdmin() }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !asignandoAdmin,
                            colors = campoOscuroColors()
                        )

                        Button(
                            onClick = { otorgarPermisosAdmin() },
                            enabled = !asignandoAdmin,
                            colors = ButtonDefaults.buttonColors(containerColor = naranjaFitPass, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(if (asignandoAdmin) "Asignando..." else "Otorgar Permisos de Admin", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (productoAEliminar != null) {
            AlertDialog(
                onDismissRequest = { if (!eliminandoProducto) productoAEliminar = null },
                containerColor = fondoCard,
                text = {
                    Text(
                        "¿Seguro que desea eliminar?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { eliminarProductoSeleccionado() },
                        enabled = !eliminandoProducto,
                        colors = ButtonDefaults.buttonColors(containerColor = rojoError, contentColor = Color.White)
                    ) {
                        Text(if (eliminandoProducto) "..." else "Sí", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!eliminandoProducto) productoAEliminar = null },
                        enabled = !eliminandoProducto
                    ) {
                        Text("No", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textoSecundario)
                    }
                }
            )
        }

        if (productoEditando != null) {
            AlertDialog(
                onDismissRequest = { if (!guardandoEdicion) productoEditando = null },
                containerColor = fondoCard,
                title = {
                    Text("Editar Producto", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editNombreInput,
                            onValueChange = { editNombreInput = filtrarNombreProducto(it) },
                            label = { Text("Nombre", fontSize = 12.sp) },
                            singleLine = true,
                            enabled = !guardandoEdicion,
                            modifier = Modifier.fillMaxWidth(),
                            colors = campoOscuroColors()
                        )
                        OutlinedTextField(
                            value = editPrecioInput,
                            onValueChange = { editPrecioInput = filtrarPrecioDecimal(it) },
                            label = { Text("Precio ($)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !guardandoEdicion,
                            modifier = Modifier.fillMaxWidth(),
                            colors = campoOscuroColors()
                        )
                        OutlinedTextField(
                            value = editStockInput,
                            onValueChange = { editStockInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Stock", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !guardandoEdicion,
                            modifier = Modifier.fillMaxWidth(),
                            colors = campoOscuroColors()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { guardarEdicionProducto() },
                        enabled = !guardandoEdicion,
                        colors = ButtonDefaults.buttonColors(containerColor = naranjaFitPass, contentColor = Color.White)
                    ) {
                        Text(if (guardandoEdicion) "Guardando..." else "Guardar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!guardandoEdicion) productoEditando = null },
                        enabled = !guardandoEdicion
                    ) {
                        Text("Cancelar", fontSize = 12.sp, color = textoSecundario)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
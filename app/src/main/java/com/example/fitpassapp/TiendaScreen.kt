package com.example.fitpassapp

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private fun limpiarNombreProd(s: String) =
    s.filter { it.isLetterOrDigit() || it in " -(). /" }.trimStart()

private fun limpiarPrecioProd(s: String): String {
    val f = s.filter { it.isDigit() || it == '.' }
    val i = f.indexOf('.')
    if (i == -1) return f
    val sinExtras = f.take(i + 1) + f.drop(i + 1).replace(".", "")
    return if (sinExtras.length - i - 1 > 2) sinExtras.take(i + 3) else sinExtras
}

private fun validarProd(nombreRaw: String, precioStr: String, stockStr: String): String? {
    val n = nombreRaw.trim().replace(Regex("\\s+"), " ")
    val p = precioStr.toDoubleOrNull()
    val st = stockStr.toIntOrNull()
    return when {
        n.length < 2 || n.count { it.isLetter() } < 2 -> "Nombre inválido (mín. 2 letras)"
        Regex("(.)\\1{3,}").containsMatchIn(n) -> "Evita repetir símbolos o letras seguidas"
        p == null || p <= 0.0 -> "El precio debe ser mayor a 0"
        st == null || st < 0 -> "El stock no puede ser negativo"
        else -> null
    }
}

@Composable
fun TiendaScreen(
    usuarioId: Long = 0L,
    nombreUsuario: String = "Socio",
    esAdmin: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var productos by remember { mutableStateOf<List<ProductoDto>>(emptyList()) }
    var apartadosUsuario by remember { mutableStateOf<List<ApartadoResponseDto>>(emptyList()) }
    var cantidadesLocales by remember { mutableStateOf(mapOf<Long, Int>()) }
    var cargando by remember { mutableStateOf(true) }
    var enviandoProductoId by remember { mutableStateOf<Long?>(null) }
    var cancelandoApartadoId by remember { mutableStateOf<Long?>(null) }

    var nomNuevo by remember { mutableStateOf("") }
    var preNuevo by remember { mutableStateOf("") }
    var stkNuevo by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var prodEliminar by remember { mutableStateOf<ProductoDto?>(null) }
    var eliminando by remember { mutableStateOf(false) }

    val naranja = Color(0xFFFF5722)
    val fondo = Color(0xFF121212)
    val card = Color(0xFF1E1E1E)
    val gris = Color(0xFFB0B0B0)
    val rojo = Color(0xFFFF5252)

    fun recargar() {
        scope.launch {
            cargando = true
            try {
                val respP = RetrofitClient.apiService.obtenerProductos()
                val body = if (respP.isSuccessful) respP.body()?.string().orEmpty() else ""
                productos = if (body.isNotBlank()) {
                    try {
                        val parsed: ApiResponseDto<List<ProductoDto>>? =
                            Gson().fromJson(body, object : TypeToken<ApiResponseDto<List<ProductoDto>>>() {}.type)
                        if (parsed?.exito == true && parsed.datos != null) parsed.datos
                        else Gson().fromJson(body, object : TypeToken<List<ProductoDto>>() {}.type) ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("TiendaScreen", "Parse: ${e.message}")
                        emptyList()
                    }
                } else emptyList()

                apartadosUsuario = if (usuarioId > 0L) {
                    val respA = RetrofitClient.apiService.obtenerApartadosPendientes(usuarioId)
                    if (respA.isSuccessful && respA.body()?.exito == true) respA.body()?.datos ?: emptyList() else emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.e("TiendaScreen", "Red: ${e.message}")
                productos = emptyList()
                apartadosUsuario = emptyList()
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(usuarioId) { recargar() }

    prodEliminar?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!eliminando) prodEliminar = null },
            containerColor = card,
            titleContentColor = Color.White,
            textContentColor = gris,
            title = { Text("Eliminar producto", fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar \"${target.nombre ?: "producto"}\" porque ya no se venderá?") },
            confirmButton = {
                TextButton(
                    enabled = !eliminando && target.id != null,
                    onClick = {
                        val id = target.id ?: return@TextButton
                        scope.launch {
                            eliminando = true
                            try {
                                val r = RetrofitClient.apiService.eliminarProducto(id)
                                if (r.isSuccessful && r.body()?.exito != false) {
                                    Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
                                    prodEliminar = null
                                    recargar()
                                } else {
                                    Toast.makeText(context, r.body()?.mensaje?.ifBlank { null } ?: "Error (${r.code()})", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally { eliminando = false }
                        }
                    }
                ) { Text(if (eliminando) "..." else "Eliminar", color = rojo, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !eliminando, onClick = { prodEliminar = null }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(fondo).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tienda FitPass", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Suplementación y accesorios para tu entrenamiento", fontSize = 11.sp, color = gris)
            }
            Button(
                onClick = { recargar() },
                colors = ButtonDefaults.buttonColors(containerColor = card, contentColor = naranja),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) { Text("Recargar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = naranja) }
        }

        Spacer(Modifier.height(16.dp))

        if (esAdmin) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agregar producto", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    OutlinedTextField(
                        value = nomNuevo, onValueChange = { nomNuevo = limpiarNombreProd(it) },
                        label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = preNuevo, onValueChange = { preNuevo = limpiarPrecioProd(it) },
                            label = { Text("Precio ($)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stkNuevo, onValueChange = { stkNuevo = it.filter(Char::isDigit) },
                            label = { Text("Stock") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        enabled = !guardando,
                        onClick = {
                            val err = validarProd(nomNuevo, preNuevo, stkNuevo)
                            if (err != null) { Toast.makeText(context, err, Toast.LENGTH_SHORT).show(); return@Button }
                            scope.launch {
                                guardando = true
                                try {
                                    val r = RetrofitClient.apiService.crearProducto(
                                        ProductoRequestDto(nomNuevo.trim().replace(Regex("\\s+"), " "), preNuevo.toDouble(), stkNuevo.toInt())
                                    )
                                    if (r.isSuccessful && r.body()?.exito == true) {
                                        nomNuevo = ""; preNuevo = ""; stkNuevo = ""
                                        Toast.makeText(context, "Producto guardado", Toast.LENGTH_SHORT).show()
                                        recargar()
                                    } else {
                                        Toast.makeText(context, r.body()?.mensaje?.ifBlank { null } ?: "Error (${r.code()})", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally { guardando = false }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = naranja, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) { Text(if (guardando) "Guardando..." else "Guardar Producto", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (apartadosUsuario.isNotEmpty()) {
            val total = apartadosUsuario.sumOf { (it.precioUnitario ?: 0.0) * (it.cantidad ?: 1) }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2E1E)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Mis Productos Apartados", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        if (usuarioId > 0L) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF2E7D32)) {
                                Text("ID Socio: #$usuarioId", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(8.dp, 3.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    apartadosUsuario.forEach { ap ->
                        val apId = ap.id ?: 0L
                        val cant = ap.cantidad ?: 1
                        val sub = (ap.precioUnitario ?: 0.0) * cant
                        val nom = ap.nombreProducto ?: "Producto"
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("• $nom (x$cant)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Subtotal: $$sub", fontSize = 11.sp, color = naranja)
                            }
                            TextButton(
                                enabled = cancelandoApartadoId == null && apId > 0L,
                                onClick = {
                                    scope.launch {
                                        cancelandoApartadoId = apId
                                        try {
                                            val r = RetrofitClient.apiService.cancelarApartado(apId)
                                            if (r.isSuccessful && r.body()?.exito == true) {
                                                Toast.makeText(context, "Cancelado: $nom", Toast.LENGTH_SHORT).show()
                                                recargar()
                                            }
                                        } finally { cancelandoApartadoId = null }
                                    }
                                }
                            ) { Text(if (cancelandoApartadoId == apId) "..." else "Cancelar", fontSize = 11.sp, color = rojo, fontWeight = FontWeight.Bold) }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF334B33), modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Total a pagar en recepción:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$$total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (cargando && productos.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(260.dp), Alignment.Center) { CircularProgressIndicator(color = naranja) }
        } else if (productos.isEmpty()) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = card), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tienda Vacía", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    Spacer(Modifier.height(8.dp))
                    Text("Aún no hay productos disponibles.", fontSize = 13.sp, color = gris, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                productos.chunked(2).forEach { fila ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                        fila.forEach { prod ->
                            val pid = prod.id ?: 0L
                            val ap = apartadosUsuario.firstOrNull { it.productoId == pid }
                            val apartado = ap != null
                            val maxSt = prod.stock ?: 0
                            val cant = if (apartado) ap.cantidad ?: 1 else (cantidadesLocales[pid] ?: 1).coerceIn(1, maxSt.coerceAtLeast(1))

                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = card), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(prod.nombre ?: "Sin nombre", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.height(38.dp))
                                    Text("Stock: $maxSt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (maxSt > 0) Color(0xFF4CAF50) else rojo)
                                    Text("$${prod.precio ?: 0.0}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = naranja)
                                    Spacer(Modifier.height(6.dp))
                                    if (!apartado && maxSt > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(onClick = { if (cant > 1) cantidadesLocales = cantidadesLocales + (pid to cant - 1) }, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) { Text("-") }
                                            Spacer(Modifier.width(10.dp))
                                            Text("$cant", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(Modifier.width(10.dp))
                                            OutlinedButton(onClick = { if (cant < maxSt) cantidadesLocales = cantidadesLocales + (pid to cant + 1) }, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Button(
                                        enabled = !apartado && maxSt > 0 && enviandoProductoId == null && pid > 0L,
                                        onClick = {
                                            if (usuarioId <= 0L) { Toast.makeText(context, "Sin ID de socio", Toast.LENGTH_SHORT).show(); return@Button }
                                            scope.launch {
                                                enviandoProductoId = pid
                                                try {
                                                    val r = RetrofitClient.apiService.crearApartado(ApartadoRequestDto(usuarioId, nombreUsuario, pid, cant))
                                                    if (r.isSuccessful && r.body()?.exito == true) {
                                                        Toast.makeText(context, "Apartado guardado", Toast.LENGTH_SHORT).show()
                                                        recargar()
                                                    } else Toast.makeText(context, r.body()?.mensaje ?: "No se pudo apartar", Toast.LENGTH_SHORT).show()
                                                } finally { enviandoProductoId = null }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (apartado) Color(0xFF2E7D32) else naranja, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(34.dp)
                                    ) {
                                        Text(
                                            when {
                                                enviandoProductoId == pid -> "Apartando..."
                                                apartado -> "Apartado ($cant)"
                                                maxSt > 0 -> "Apartar"
                                                else -> "Agotado"
                                            },
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (esAdmin && pid > 0L) {
                                        TextButton(onClick = { prodEliminar = prod }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(26.dp)) {
                                            Text("Eliminar", fontSize = 11.sp, color = rojo, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
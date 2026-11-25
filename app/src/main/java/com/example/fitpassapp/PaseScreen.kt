package com.example.fitpassapp

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PaseScreen(
    usuarioId: Long = 2L,
    onIrAGimnasios: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var membresia by remember { mutableStateOf<MembresiaDto?>(null) }
    var modo by remember { mutableStateOf("CARGANDO") }
    var codigoQr by remember { mutableStateOf<String?>(null) }
    var mensajeEstado by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(true) }

    val gymApartado = obtenerGymApartadoLocal(context, usuarioId)

    fun cargarEstadoPase(mostrarSpinner: Boolean = false) {
        scope.launch {
            if (mostrarSpinner) {
                cargando = true
            }
            try {
                val resMembresia = RetrofitClient.apiService.obtenerMembresiaUsuario(usuarioId)
                val bodyMembresia = resMembresia.body()
                if (resMembresia.isSuccessful && bodyMembresia?.exito == true) {
                    membresia = bodyMembresia.datos
                }

                val resEstado = RetrofitClient.apiService.obtenerEstadoQr(usuarioId)
                val bodyEstado = resEstado.body()
                if (resEstado.isSuccessful && bodyEstado?.exito == true) {
                    val datos = bodyEstado.datos
                    modo = datos?.modo ?: "BLOQUEADO"
                    codigoQr = datos?.codigoQr
                    mensajeEstado = datos?.mensaje ?: ""
                } else {
                    modo = "BLOQUEADO"
                    mensajeEstado = bodyEstado?.mensaje ?: "No se pudo consultar el estado del pase."
                }
            } catch (_: Exception) {
                if (mostrarSpinner || modo == "CARGANDO") {
                    modo = "BLOQUEADO"
                    mensajeEstado = "Error de conexión al consultar tu pase."
                }
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(usuarioId) {
        cargarEstadoPase(mostrarSpinner = true)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        cargarEstadoPase(mostrarSpinner = false)
    }

    LaunchedEffect(usuarioId, modo) {
        if (modo == "ENTRADA" || modo == "SALIDA" || modo == "BLOQUEADO") {
            while (isActive) {
                delay(3000L)
                cargarEstadoPase(mostrarSpinner = false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "FitPass Digital",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "ID de Socio: #$usuarioId • Acceso seguro e intransferible",
            fontSize = 12.sp,
            color = Color.Gray
        )
        if (!gymApartado.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sede elegida: $gymApartado",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        if (cargando) {
            CircularProgressIndicator(color = Color(0xFFFF5722))
        } else if ((modo == "ENTRADA" || modo == "SALIDA") && !codigoQr.isNullOrBlank()) {
            val badgeColor = if (modo == "ENTRADA") Color(0xFF4CAF50) else Color(0xFFFF9800)
            val badgeTexto = if (modo == "ENTRADA") "QR DE ENTRADA" else "QR DE SALIDA"

            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = badgeTexto,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val qrCodificado = Uri.encode(codigoQr)
            val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=$qrCodificado"

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.size(260.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    AsyncImage(
                        model = qrCodeUrl,
                        contentDescription = "Código QR de Acceso",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = mensajeEstado.ifBlank { "Muestra este código en el gimnasio" },
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { cargarEstadoPase(mostrarSpinner = true) }) {
                Text("Actualizar estado", color = Color.White, fontSize = 12.sp)
            }
        } else if (modo == "EN_SESION") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🏋️ EN ENTRENAMIENTO",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = mensajeEstado.ifBlank {
                            "Tu entrada ya fue registrada. El QR de salida se activará al cumplirse las 2 horas."
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cargarEstadoPase(mostrarSpinner = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                    ) {
                        Text("Actualizar estado", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val esPendientePago = membresia?.estadoPago.equals("PENDIENTE", ignoreCase = true)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (esPendientePago) "⏳ PENDIENTE A PAGAR" else "⚠️ ACCESO BLOQUEADO",
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (esPendientePago) {
                            val sedeTexto = if (!gymApartado.isNullOrBlank()) " en $gymApartado" else ""
                            "Tienes una membresía (${membresia?.tipoPase ?: "PLAN"}) apartada$sedeTexto. Acude a recepción con tu ID #$usuarioId a realizar tu pago de $${membresia?.precio ?: 0.0} para liberar tu Pase QR."
                        } else {
                            mensajeEstado.ifBlank {
                                "Aún no tienes una membresía activa. Selecciona un gimnasio para apartar tu plan y pagar en recepción."
                            }
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!esPendientePago) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onIrAGimnasios,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Elegir gimnasio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
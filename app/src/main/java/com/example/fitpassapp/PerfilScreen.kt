package com.example.fitpassapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class PlanOpcion(
    val codigo: String,
    val nombre: String,
    val precio: String
)

@Composable
fun PerfilScreen(
    usuarioId: Long,
    nombreUsuario: String = "Aldo Arroyo",
    onCerrarSesion: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var membresia by remember { mutableStateOf<MembresiaDto?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var procesandoRenovacion by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var mostrarDialogoPlanes by remember { mutableStateOf(false) }

    val planesDisponibles = listOf(
        PlanOpcion("VISITA", "Pase por Día (Visita)", "$50"),
        PlanOpcion("SEMANAL", "Plan Semanal", "$150"),
        PlanOpcion("MENSUAL", "Plan Mensual", "$450"),
        PlanOpcion("ANUAL", "Plan Anual", "$4,200")
    )

    fun cargarMembresia() {
        scope.launch {
            cargando = true
            try {
                val response = RetrofitClient.apiService.obtenerMembresiaUsuario(usuarioId)
                if (response.isSuccessful && response.body()?.exito == true) {
                    membresia = response.body()?.datos
                    mensajeError = null
                } else {
                    mensajeError = response.body()?.mensaje ?: "Sin membresía activa"
                }
            } catch (e: Exception) {
                mensajeError = "Error de conexión con Backend"
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(usuarioId) {
        cargarMembresia()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.size(90.dp),
            shape = CircleShape,
            color = Color(0xFFFF5722)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (nombreUsuario.isNotBlank()) nombreUsuario.take(1).uppercase() else "A",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = nombreUsuario,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "ID Usuario: #$usuarioId",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "TU MEMBRESÍA",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5722),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (cargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = Color(0xFFFF5722)
                    )
                } else {
                    val m = membresia
                    if (m != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PLAN ${m.tipoPase}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Surface(
                                color = if (m.activa && m.estadoPago == "PAGADO") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (m.activa && m.estadoPago == "PAGADO") "ACTIVA" else "PENDIENTE",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = Color(0xFF2C2C2C)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Fecha Inicio", fontSize = 12.sp, color = Color.Gray)
                                Text(m.fechaInicio, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            Column {
                                Text("Vencimiento", fontSize = 12.sp, color = Color.Gray)
                                Text(m.fechaFin, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            Column {
                                Text("Costo", fontSize = 12.sp, color = Color.Gray)
                                Text("$${m.precio}", fontSize = 14.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = mensajeError ?: "Sin membresía activa registrada",
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (procesandoRenovacion) {
            CircularProgressIndicator(color = Color(0xFFFF5722))
        } else {
            Button(
                onClick = { mostrarDialogoPlanes = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("RENOVAR MEMBRESÍA", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = {
                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                onCerrarSesion()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336))
        ) {
            Text("CERRAR SESIÓN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    if (mostrarDialogoPlanes) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoPlanes = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Selecciona tu Plan",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Elige el tipo de membresía que deseas activar:",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    planesDisponibles.forEach { plan ->
                        Card(
                            onClick = {
                                mostrarDialogoPlanes = false
                                scope.launch {
                                    procesandoRenovacion = true
                                    try {
                                        val response = RetrofitClient.apiService.renovarMembresia(
                                            usuarioId = usuarioId,
                                            tipoPase = plan.codigo
                                        )
                                        if (response.isSuccessful && response.body()?.exito == true) {
                                            Toast.makeText(
                                                context,
                                                "¡Plan ${plan.codigo} solicitado! Paga en recepción.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            cargarMembresia()
                                        } else {
                                            Toast.makeText(context, "Error al renovar plan", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error de red", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        procesandoRenovacion = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = plan.nombre,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = plan.precio,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mostrarDialogoPlanes = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }
}
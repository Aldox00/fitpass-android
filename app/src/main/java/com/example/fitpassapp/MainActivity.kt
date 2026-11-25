package com.example.fitpassapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var usuarioLogueado by remember { mutableStateOf<UsuarioResponseDto?>(null) }

                val cerrarSesionLimpio: () -> Unit = {
                    usuarioLogueado = null
                }

                if (usuarioLogueado == null) {
                    LoginScreen(onLoginExitoso = { usuario ->
                        usuarioLogueado = usuario
                    })
                } else {
                    val rolUsuario = usuarioLogueado?.rol?.uppercase() ?: "SOCIO"

                    if (rolUsuario == "ADMIN") {
                        AdminScreen(
                            onCerrarSesion = cerrarSesionLimpio
                        )
                    } else {
                        MainAppStructure(
                            usuario = usuarioLogueado!!,
                            onCerrarSesion = cerrarSesionLimpio
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppStructure(
    usuario: UsuarioResponseDto,
    onCerrarSesion: () -> Unit
) {
    var pestanaSeleccionada by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = pestanaSeleccionada == 0,
                    onClick = { pestanaSeleccionada = 0 },
                    label = { Text("Mi Pase", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) },
                    icon = { Text("🎫", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = pestanaSeleccionada == 1,
                    onClick = { pestanaSeleccionada = 1 },
                    label = { Text("Tienda", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) },
                    icon = { Text("🏋️", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = pestanaSeleccionada == 2,
                    onClick = { pestanaSeleccionada = 2 },
                    label = { Text("Gimnasios", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) },
                    icon = { Text("📍", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = pestanaSeleccionada == 3,
                    onClick = { pestanaSeleccionada = 3 },
                    label = { Text("Perfil", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) },
                    icon = { Text("👤", fontSize = 18.sp) }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFF121212)
        ) {
            when (pestanaSeleccionada) {
                0 -> PaseScreen(
                    usuarioId = usuario.id,
                    onIrAGimnasios = { pestanaSeleccionada = 2 }
                )
                1 -> TiendaScreen(
                    usuarioId = usuario.id,
                    nombreUsuario = usuario.nombre
                )
                2 -> GimnasiosScreen(
                    esAdmin = false,
                    usuarioId = usuario.id,
                    onIrAPase = { pestanaSeleccionada = 0 }
                )
                3 -> PerfilScreen(
                    usuarioId = usuario.id,
                    nombreUsuario = usuario.nombre,
                    onCerrarSesion = onCerrarSesion
                )
            }
        }
    }
}
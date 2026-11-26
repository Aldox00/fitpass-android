package com.example.fitpassapp

import android.accounts.AccountManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LogoGoogleOficial(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 3.8f * density
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
        val arcSize = Size(diameter, diameter)

        drawArc(Color(0xFFEA4335), 200f, 110f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))
        drawArc(Color(0xFFFBBC05), 120f, 85f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))
        drawArc(Color(0xFF34A853), 35f, 90f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))
        drawArc(Color(0xFF4285F4), -20f, 60f, false, topLeft, arcSize, style = Stroke(width = strokeWidth))

        val cy = size.height / 2
        val cx = size.width / 2
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx - (2f * density), cy),
            end = Offset(size.width - (strokeWidth / 2), cy),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun IconoPestanitaOjo(visible: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        if (visible) {
            val path = Path().apply {
                moveTo(0f, h * 0.5f)
                cubicTo(w * 0.25f, h * 0.15f, w * 0.75f, h * 0.15f, w, h * 0.5f)
                cubicTo(w * 0.75f, h * 0.85f, w * 0.25f, h * 0.85f, 0f, h * 0.5f)
            }
            drawPath(path = path, color = Color(0xFFFF5722), style = Stroke(width = 4f))
            drawCircle(color = Color(0xFFFF5722), radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.5f))
        } else {
            val path = Path().apply {
                moveTo(0f, h * 0.5f)
                cubicTo(w * 0.25f, h * 0.2f, w * 0.75f, h * 0.2f, w, h * 0.5f)
                cubicTo(w * 0.75f, h * 0.80f, w * 0.25f, h * 0.80f, 0f, h * 0.5f)
            }
            drawPath(path = path, color = Color.Gray, style = Stroke(width = 3.5f))
            drawCircle(color = Color.Gray, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
            drawLine(
                color = Color(0xFFFF5722),
                start = Offset(w * 0.15f, h * 0.15f),
                end = Offset(w * 0.85f, h * 0.85f),
                strokeWidth = 4.5f
            )
        }
    }
}

@Composable
fun LoginScreen(onLoginExitoso: (UsuarioResponseDto) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        TokenManager.init(context)
    }

    var esModoRegistro by remember { mutableStateOf(false) }
    var esModoRecuperacion by remember { mutableStateOf(false) }
    var pasoRecuperacion by remember { mutableStateOf(1) } // 1: Pedir correo, 2: Ingresar código y nueva pass

    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var codigoOtp by remember { mutableStateOf("") }
    var nuevaPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var nuevaPasswordVisible by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }

    fun esEmailValido(correo: String): Boolean {
        val emailRegex = "^[A-Za-z0-9.]+@[A-Za-z0-9.]+\\.[A-Za-z]{2,6}\$"
        return correo.matches(emailRegex.toRegex())
    }

    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                scope.launch {
                    cargando = true
                    try {
                        val res = RetrofitClient.apiService.loginConGoogle(
                            GoogleLoginRequestDto(idToken = selectedEmail)
                        )
                        if (res.isSuccessful && res.body()?.exito == true) {
                            val usuario = res.body()?.datos
                            if (usuario != null) {
                                TokenManager.guardarToken(context, usuario.token)
                                Toast.makeText(context, "Bienvenido $selectedEmail", Toast.LENGTH_SHORT).show()
                                onLoginExitoso(usuario)
                            }
                        } else {
                            Toast.makeText(context, "Error al autenticar en el servidor", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, "Error de red con el backend", Toast.LENGTH_SHORT).show()
                    } finally {
                        cargando = false
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FitPass",
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5722)
        )
        Text(
            text = when {
                esModoRecuperacion -> if (pasoRecuperacion == 1) "Recuperar Contraseña" else "Nuevo Código y Password"
                esModoRegistro -> "Crea tu cuenta de Usuario"
                else -> "Acceso a Gimnasios"
            },
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (esModoRecuperacion) {
            if (pasoRecuperacion == 1) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { input ->
                        val filtrado = input.filter { char ->
                            char.isLetterOrDigit() || char == '@' || char == '.'
                        }
                        if (filtrado.length <= 80) email = filtrado
                    },
                    label = { Text("Ingresa tu correo registrado", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = Color(0xFF2C2C2C)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (cargando) {
                    CircularProgressIndicator(color = Color(0xFFFF5722))
                } else {
                    Button(
                        onClick = {
                            val mailClean = email.trim()
                            if (mailClean.isBlank() || !esEmailValido(mailClean)) {
                                Toast.makeText(context, "⚠️ Ingresa un correo válido", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                cargando = true
                                try {
                                    val res = RetrofitClient.apiService.solicitarRecuperacion(mapOf("email" to mailClean))
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "¡Código enviado a tu correo!", Toast.LENGTH_LONG).show()
                                        pasoRecuperacion = 2
                                    } else {
                                        Toast.makeText(context, "El correo no está registrado", Toast.LENGTH_LONG).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                                } finally {
                                    cargando = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ENVIAR CÓDIGO", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OutlinedTextField(
                    value = codigoOtp,
                    onValueChange = { input ->
                        val filtrado = input.filter { it.isDigit() }
                        if (filtrado.length <= 6) codigoOtp = filtrado
                    },
                    label = { Text("Código de 6 dígitos (Solo números)", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = Color(0xFF2C2C2C)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nuevaPassword,
                    onValueChange = { input ->
                        if (input.length <= 20) nuevaPassword = input
                    },
                    label = { Text("Nueva contraseña (6 - 20 caracteres)", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = if (nuevaPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { nuevaPasswordVisible = !nuevaPasswordVisible }) {
                            IconoPestanitaOjo(visible = nuevaPasswordVisible)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = Color(0xFF2C2C2C)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (cargando) {
                    CircularProgressIndicator(color = Color(0xFFFF5722))
                } else {
                    Button(
                        onClick = {
                            if (codigoOtp.length < 6 || nuevaPassword.trim().length < 6) {
                                Toast.makeText(context, "⚠️ El código debe ser de 6 dígitos y la pass mín. de 6", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            scope.launch {
                                cargando = true
                                try {
                                    val res = RetrofitClient.apiService.restablecerPassword(
                                        mapOf("email" to email.trim(), "codigo" to codigoOtp.trim(), "nuevaPassword" to nuevaPassword.trim())
                                    )
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "¡Contraseña actualizada con éxito!", Toast.LENGTH_LONG).show()
                                        esModoRecuperacion = false
                                        pasoRecuperacion = 1
                                        password = ""
                                        codigoOtp = ""
                                        nuevaPassword = ""
                                    } else {
                                        Toast.makeText(context, "Código incorrecto o expirado", Toast.LENGTH_LONG).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                                } finally {
                                    cargando = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CAMBIAR CONTRASEÑA", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Volver al inicio de sesión",
                color = Color(0xFFFF9800),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    esModoRecuperacion = false
                    pasoRecuperacion = 1
                }
            )

        } else {
            if (esModoRegistro) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { input ->
                        val filtrado = input.filter { it.isLetter() || it.isWhitespace() }
                        if (filtrado.length <= 50) nombre = filtrado
                    },
                    label = { Text("Nombre Completo ", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5722),
                        unfocusedBorderColor = Color(0xFF2C2C2C)
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { input ->
                    val filtrado = input.filter { char ->
                        char.isLetterOrDigit() || char == '@' || char == '.'
                    }
                    if (filtrado.length <= 80) email = filtrado
                },
                label = { Text("Correo electrónico", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF5722),
                    unfocusedBorderColor = Color(0xFF2C2C2C)
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { input ->
                    if (input.length <= 20) password = input
                },
                label = { Text("Contraseña (6 - 20 caracteres)", color = Color.Gray) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        IconoPestanitaOjo(visible = passwordVisible)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF5722),
                    unfocusedBorderColor = Color(0xFF2C2C2C)
                )
            )

            if (!esModoRegistro) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        text = "¿Olvidaste tu contraseña?",
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            esModoRecuperacion = true
                            pasoRecuperacion = 1
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (cargando) {
                CircularProgressIndicator(color = Color(0xFFFF5722))
            } else {
                Button(
                    onClick = {
                        val mailClean = email.trim()
                        val passClean = password.trim()
                        val nomClean = nombre.trim()

                        if (mailClean.isBlank() || passClean.isBlank() || (esModoRegistro && nomClean.isBlank())) {
                            Toast.makeText(context, "⚠️ Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (!esEmailValido(mailClean)) {
                            Toast.makeText(context, "⚠️ Formato inválido. Ejemplo: usuario@gmail.com", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        if (passClean.length < 6) {
                            Toast.makeText(context, "⚠️ La contraseña requiere al menos 6 caracteres", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        scope.launch {
                            cargando = true
                            try {
                                if (esModoRegistro) {
                                    val res = RetrofitClient.apiService.registrar(
                                        RegisterRequestDto(nomClean, mailClean, passClean)
                                    )
                                    if (res.isSuccessful && res.body()?.exito == true) {
                                        Toast.makeText(context, "¡Usuario registrado! Ya puedes iniciar sesión", Toast.LENGTH_LONG).show()
                                        esModoRegistro = false
                                    } else {
                                        Toast.makeText(context, res.body()?.mensaje ?: "Error al registrar", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    val res = RetrofitClient.apiService.login(
                                        LoginRequestDto(mailClean, passClean)
                                    )
                                    if (res.isSuccessful && res.body()?.exito == true) {
                                        val usuario = res.body()?.datos
                                        if (usuario != null) {
                                            TokenManager.guardarToken(context, usuario.token)
                                            onLoginExitoso(usuario)
                                        }
                                    } else {
                                        Toast.makeText(context, res.body()?.mensaje ?: "Credenciales inválidas", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Error de conexión con el servidor", Toast.LENGTH_LONG).show()
                            } finally {
                                cargando = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (esModoRegistro) "REGISTRARME" else "INICIAR SESIÓN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = AccountManager.newChooseAccountIntent(
                                null, null, arrayOf("com.google"), true, null, null, null, null
                            )
                            googleAccountPickerLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir el selector de cuentas", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3C3C3C))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        LogoGoogleOficial()
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Continuar con Google", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (esModoRegistro) "¿Ya tienes cuenta? Inicia sesión aquí" else "¿No tienes cuenta? Regístrate aquí",
                color = Color(0xFFFF9800),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    esModoRegistro = !esModoRegistro
                }
            )
        }
    }
}
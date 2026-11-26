package com.example.fitpassapp

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class CheckInRequestDto(
    val usuarioId: Long,
    val gimnasioId: Long
)

data class LoginRequestDto(
    val email: String,
    val password: String
)

data class RegisterRequestDto(
    val nombre: String,
    val email: String,
    val password: String
)

data class GoogleLoginRequestDto(
    val idToken: String
)

data class UsuarioResponseDto(
    val id: Long,
    val nombre: String,
    val email: String,
    val rol: String?,
    val token: String? = null
)

data class ApiResponseDto<T>(
    val exito: Boolean,
    val mensaje: String,
    val datos: T?
)

data class EstadoQrDto(
    val modo: String? = null,
    val codigoQr: String? = null,
    val mensaje: String? = null,
    val minutosRestantes: Long? = null
)

data class MembresiaDto(
    val id: Long,
    val tipoPase: String,
    val fechaInicio: String,
    val fechaFin: String,
    val activa: Boolean,
    val precio: Double,
    val estadoPago: String?
)

data class ProductoDto(
    val id: Long?,
    val nombre: String?,
    val precio: Double?,
    val stock: Int?
)

data class ProductoRequestDto(
    val nombre: String,
    val precio: Double,
    val stock: Int
)

data class ApartadoRequestDto(
    val usuarioId: Long,
    val nombreUsuario: String?,
    val productoId: Long,
    val cantidad: Int
)

data class ApartadoResponseDto(
    val id: Long?,
    val usuarioId: Long?,
    val nombreUsuario: String?,
    val productoId: Long?,
    val nombreProducto: String?,
    val cantidad: Int?,
    val precioUnitario: Double?,
    val estado: String?
)

data class GimnasioDto(
    val id: Long?,
    val nombre: String?,
    val direccion: String?,
    val ciudad: String?,
    val latitud: Double?,
    val longitud: Double?,
    val activo: Boolean? = null
)

data class GimnasioRequestDto(
    val nombre: String,
    val direccion: String,
    val ciudad: String = "Suchiapa",
    val latitud: Double,
    val longitud: Double
)

data class UbicacionResponseDto(
    val place_id: Long?,
    val display_name: String?,
    val lat: String?,
    val lon: String?,
    val type: String?,
    val importance: Double?
)

interface FitPassApiService {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<ApiResponseDto<UsuarioResponseDto>>

    @POST("api/auth/register")
    suspend fun registrar(
        @Body request: RegisterRequestDto
    ): Response<ApiResponseDto<UsuarioResponseDto>>

    @POST("api/auth/google")
    suspend fun loginConGoogle(
        @Body request: GoogleLoginRequestDto
    ): Response<ApiResponseDto<UsuarioResponseDto>>

    @POST("api/auth/forgot-password")
    suspend fun solicitarRecuperacion(
        @Body body: Map<String, String>
    ): Response<ApiResponseDto<Void>>

    @POST("api/auth/reset-password")
    suspend fun restablecerPassword(
        @Body body: Map<String, String>
    ): Response<ApiResponseDto<Void>>

    @POST("api/v1/pases/checkin")
    suspend fun registrarCheckIn(
        @Body request: CheckInRequestDto
    ): Response<ApiResponseDto<Any>>

    @POST("api/v1/pases/checkout/{usuarioId}")
    suspend fun registrarCheckOut(
        @Path("usuarioId") usuarioId: Long
    ): Response<ApiResponseDto<Any>>

    @GET("api/v1/pases/estado-qr/{usuarioId}")
    suspend fun obtenerEstadoQr(
        @Path("usuarioId") usuarioId: Long
    ): Response<ApiResponseDto<EstadoQrDto>>

    @GET("api/v1/membresias/usuario/{usuarioId}")
    suspend fun obtenerMembresiaUsuario(
        @Path("usuarioId") usuarioId: Long
    ): Response<ApiResponseDto<MembresiaDto>>

    @GET("api/v1/productos")
    suspend fun obtenerProductos(): Response<okhttp3.ResponseBody>

    @POST("api/v1/productos")
    suspend fun crearProducto(
        @Body request: ProductoRequestDto
    ): Response<ApiResponseDto<ProductoDto>>

    @PUT("api/v1/productos/{id}")
    suspend fun actualizarProducto(
        @Path("id") productoId: Long,
        @Body request: ProductoRequestDto
    ): Response<ApiResponseDto<ProductoDto>>

    @DELETE("api/v1/productos/{id}")
    suspend fun eliminarProducto(
        @Path("id") productoId: Long
    ): Response<ApiResponseDto<Void>>

    @PUT("api/v1/productos/{id}/descontar-stock")
    suspend fun descontarStockProducto(
        @Path("id") productoId: Long,
        @Query("cantidad") cantidad: Int
    ): Response<ApiResponseDto<ProductoDto>>

    @GET("api/v1/apartados")
    suspend fun obtenerApartadosPendientes(
        @Query("usuarioId") usuarioId: Long? = null
    ): Response<ApiResponseDto<List<ApartadoResponseDto>>>

    @POST("api/v1/apartados")
    suspend fun crearApartado(
        @Body request: ApartadoRequestDto
    ): Response<ApiResponseDto<ApartadoResponseDto>>

    @DELETE("api/v1/apartados/{id}")
    suspend fun cancelarApartado(
        @Path("id") apartadoId: Long
    ): Response<ApiResponseDto<Void>>

    @PUT("api/v1/apartados/cobrar-socio/{usuarioId}")
    suspend fun cobrarApartadosSocio(
        @Path("usuarioId") usuarioId: Long
    ): Response<ApiResponseDto<List<ApartadoResponseDto>>>

    @GET("api/v1/gimnasios")
    suspend fun obtenerGimnasios(
        @Query("ciudad") ciudad: String? = "Suchiapa"
    ): Response<ApiResponseDto<List<GimnasioDto>>>

    @GET("api/v1/gimnasios/cercanos")
    suspend fun obtenerGimnasiosCercanos(
        @Query("ciudad") ciudad: String? = "Suchiapa",
        @Query("latitud") latitud: Double? = null,
        @Query("longitud") longitud: Double? = null
    ): Response<ApiResponseDto<List<GimnasioDto>>>

    @POST("api/v1/gimnasios")
    suspend fun crearGimnasio(
        @Body request: GimnasioRequestDto
    ): Response<ApiResponseDto<GimnasioDto>>

    @GET("api/v1/geolocalizacion/buscar")
    suspend fun buscarUbicacion(
        @Query("direccion") direccion: String
    ): Response<List<UbicacionResponseDto>>

    @GET("api/v1/geolocalizacion/reversa")
    suspend fun geocodificacionReversa(
        @Query("latitud") latitud: Double,
        @Query("longitud") longitud: Double
    ): Response<UbicacionResponseDto>

    @GET("api/v1/geolocalizacion/gimnasios")
    suspend fun buscarGimnasiosGeolocalizacion(
        @Query("ciudad") ciudad: String = "Suchiapa"
    ): Response<List<UbicacionResponseDto>>

    @POST("api/v1/membresias/asignar")
    suspend fun renovarMembresia(
        @Query("usuarioId") usuarioId: Long,
        @Query("tipoPase") tipoPase: String
    ): Response<ApiResponseDto<MembresiaDto>>

    @PUT("api/v1/membresias/confirmar-pago/{membresiaId}")
    suspend fun confirmarPago(
        @Path("membresiaId") membresiaId: Long
    ): Response<ApiResponseDto<MembresiaDto>>

    @PUT("api/v1/membresias/confirmar-pago-por-correo")
    suspend fun confirmarPagoPorCorreo(
        @Query("email") email: String
    ): Response<ApiResponseDto<MembresiaDto>>

    @PUT("api/v1/membresias/confirmar-pago-usuario/{usuarioId}")
    suspend fun confirmarPagoPorUsuarioId(
        @Path("usuarioId") usuarioId: Long
    ): Response<ApiResponseDto<MembresiaDto>>

    @PUT("api/v1/usuarios/hacer-admin")
    suspend fun promoverAdmin(
        @Query("email") email: String
    ): Response<ApiResponseDto<UsuarioResponseDto>>
}

object TokenManager {
    private const val PREF_NAME = "fitpass_session_prefs"
    private const val KEY_JWT_TOKEN = "jwt_token"

    @Volatile
    private var cachedToken: String? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        cachedToken = prefs.getString(KEY_JWT_TOKEN, null)
    }

    fun guardarToken(context: Context, token: String?) {
        cachedToken = token
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    fun obtenerToken(context: Context? = null): String? {
        if (!cachedToken.isNullOrBlank()) return cachedToken
        if (context != null) {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            cachedToken = prefs.getString(KEY_JWT_TOKEN, null)
        }
        return cachedToken
    }

    fun limpiarToken(context: Context? = null) {
        cachedToken = null
        if (context != null) {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_JWT_TOKEN).apply()
        }
    }
}

private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // No adjuntamos Bearer en rutas públicas de auth
        if (path.startsWith("/api/auth/")) {
            return chain.proceed(originalRequest)
        }

        val token = TokenManager.obtenerToken()
        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://fitpass-api-fc99.onrender.com/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .build()
    }

    val apiService: FitPassApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FitPassApiService::class.java)
    }
}
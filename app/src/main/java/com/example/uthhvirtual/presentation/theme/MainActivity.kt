package com.example.uthhvirtual.presentation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.uthhvirtual.R
import com.example.uthhvirtual.presentation.theme.ApiServices
import com.example.uthhvirtual.presentation.theme.UthhVirtualTheme
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.internal.NoOpContinuation.context
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    var URL_BASE = "https://robe.host8b.me/WebServices/"
    var CHANNEL_ID = "new_order_channel"
    var NOTIFICATION_ID = 1

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(URL_BASE)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val apiServices by lazy {
        retrofit.create(ApiServices::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        createNotificationChannel()

        setContent {
            UthhVirtualTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(onLoginClicked = { username, password ->
                            /*if (verifyCredentials(username, password, roles)) {
                                navController.navigate("home")
                            }*/
                            lifecycleScope.launch {
                                try {
                                    val request = LoginRequest(username, password)
                                    val response: LoginResponse = apiServices.login(request)
                                    if (response.done) {
                                        // Si el login es exitoso, guarda el token para futuras solicitudes
                                        // Puedes guardar el token en SharedPreferences o un almacenamiento seguro
                                        Log.d("LOGIN_SUCCESS", "Token recibido: ${response.message}")
                                        navController.navigate("home")
                                    } else {
                                        // Maneja el error de login
                                        response.message?.let { it1 -> Log.e("LOGIN_ERROR", it1) }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // Maneja errores de red o excepciones
                                    Log.e("LOGIN_EXCEPTION", e.message.toString())
                                }
                            }

                        })
                    }
                    composable("home") {
                        val listaPedidos = remember { mutableStateListOf<ListModel>() }
                        val message by remember { mutableStateOf("Bienvenido") }

                        LaunchedEffect(Unit) {
                            val retrofit = Retrofit.Builder()
                                .baseUrl(URL_BASE)
                                .addConverterFactory(GsonConverterFactory.create())
                                .build()

                            val service = retrofit.create(ApiServices::class.java)
                            var lastProcessedNotificationId = -1  // Inicializar con un valor que no exista en la API
                            var lastUpdatedDate: Date? = null

                            while (true) {
                                try {
                                    val response = service.getList()
                                    val newNotifications = response.filter { it.intClvNotification > lastProcessedNotificationId }

                                    if (newNotifications.isNotEmpty()) {
                                        newNotifications.forEach { pedido ->
                                            Log.d("API_RESPONSE", pedido.toString())

                                            val metadata = parseMetadata(pedido.metadata)
                                            if (metadata != null) {
                                                Log.d("PARSED_METADATA", "Asignatura: ${metadata.asignatura}, Fecha de entrega: ${metadata.fecha_entrega}")

                                                // Verificar si la fecha de entrega es mañana
                                                val fechaEntrega = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(metadata.fecha_entrega)
                                                val tomorrow = Calendar.getInstance().apply {
                                                    add(Calendar.DAY_OF_YEAR, 1)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }.time

/*
                                                if (fechaEntrega != null && fechaEntrega.before(tomorrow)) {
                                                    Log.d("ENTREGA_MAÑANA", "Se entrega mañana esta actividad: ${metadata.asignatura}")
                                                    sendNotificationRecordatorioTareas(pedido)
                                                }*/

                                                if (metadata.calificacion != null && metadata.calificacion.toIntOrNull() != 0) {
                                                    // Verificar si la fecha de actualización es la más reciente
                                                    val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(pedido.fechaActualizacion)
                                                    if (lastUpdatedDate == null || currentDate.after(lastUpdatedDate)) {
                                                        sendCalificationNotification(pedido, metadata.calificacion)
                                                        lastUpdatedDate = currentDate
                                                    }
                                                }

                                            }
                                            metadata?.let { it1 -> sendDocentAddNotification(it1.docente, metadata.asignatura) }
                                            listaPedidos.add(pedido)
                                            sendNotification(pedido)
                                        }

                                        lastProcessedNotificationId = newNotifications.maxByOrNull { it.intClvNotification }?.intClvNotification ?: lastProcessedNotificationId
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                delay(10000)
                            }
                        }

                        HomeScreen(message = message, listaPedidos, navController)
                    }

                    composable("details/{metadata}") { backStackEntry ->

                        val metadataString = backStackEntry.arguments?.getString("metadata")
                        Log.d("PARSED_METADATA_STRING", "${metadataString}")

                        val jsonString = metadataString
                            ?.replace("\\\"", "\"") // Reemplazar \" por "
                            ?.replace("^\"|\"$".toRegex(), "")
                        Log.d("JSONLIMPIO", "${jsonString}")

                        val metadata2 = jsonString?.let { parseMetadata(it.toString()) }

                        Log.d("PARSED_METADATA_CONVERT", "Asignatura: ${jsonString}")

                        DetailsScreen(navController, metadata2)

                    }
                }
            }
        }
    }

    fun parseMetadata(metadataString: String): Metadata? {
        return try {
            Gson().fromJson(metadataString, Metadata::class.java)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            null
        }
    }

    var requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Log.e("Permission", "Notification permission not granted")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendCalificationNotification(pedido: ListModel, calificacion: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.qualification)
                .setContentTitle("Tarea calificada")
                .setContentText("Tarea: ${pedido.vchmensaje}, Calificación: $calificacion")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID + 1, builder.build()) // Use a different ID for calification notifications
            }
        } else {
            Log.e("Notification", "Permiso de notificaciones no concedido")
        }
    }

    private fun sendDocentAddNotification(Docente: String, Materia: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            // Carga el logo de la empresa como un Bitmap
            val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.descarga)

            // Configura las opciones de escala para manejar imágenes grandes
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(resources, R.drawable.descarga, options)

            // Calcula el factor de escala adecuado para la imagen
            options.inSampleSize = calculateInSampleSize(options, 800, 400)  // Ajusta el tamaño según lo necesario
            options.inJustDecodeBounds = false

            // Carga la imagen escalada
            val bigPicture = BitmapFactory.decodeResource(resources, R.drawable.descarga, options)

            if (largeIcon == null) {
                Log.e("Notification", "Error loading large icon")
            }

            if (bigPicture == null) {
                Log.e("Notification", "Error loading big picture")
            }

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.add_class)
                .setLargeIcon(largeIcon)
                .setContentTitle("Agregado a una Nueva Materia")
                .setContentText("El Docente: $Docente, Te agregó a: $Materia")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    )

            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID + 1, builder.build())
            }
        } else {
            Log.e("Notification", "Permiso de notificaciones no concedido")
        }
    }

    // Método para calcular el tamaño de muestra adecuado para la imagen
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }


    private fun sendNotificationRecordatorioTareas(pedido: ListModel) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.new_activity)
                .setContentTitle("Recordatorio se entrega mañana")
                .setContentText(pedido.vchmensaje)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID + 1, builder.build()) // Use a different ID for calification notifications
            }
        } else {
            Log.e("Notification", "Permiso de notificaciones no concedido")
        }
    }

    private fun verifyCredentials(username: String, password: String): Boolean {
        //return username == "" && password == ""
        return username.isNotBlank() && password.isNotBlank()

    }

    private fun sendNotification(pedido: ListModel) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.new_activity_class)
                .setContentTitle("Nueva actividad")
                .setContentText(pedido.vchmensaje)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } else {
            Log.e("Notification", "Permiso de notificaciones no concedido")
        }
    }
}


/*
@Composable
fun LoginScreen(onLoginClicked: (String, String) -> Unit) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Inicio de Sesión",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val username = remember { mutableStateOf("") }
            val password = remember { mutableStateOf("") }

            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username.value,
                    onValueChange = { username.value = it },
                    label = { Text("Usuario", style = TextStyle(color = MaterialTheme.colors.primary, fontSize = 12.sp)) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = { Text("Contraseña", style = TextStyle(color = MaterialTheme.colors.primary, fontSize = 12.sp)) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { onLoginClicked(username.value, password.value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Iniciar Sesión", fontSize = 16.sp)
            }
        }
    }
}
*/

@Composable
fun LoginScreen(onLoginClicked: (String, String) -> Unit) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.descarga),
                contentDescription = "Logo de la Empresa",
                modifier = Modifier
                    .size(30.dp) // adjust the size as needed
                    .padding(bottom = 0.dp)
            )
            Text(
                text = "Inicio de Sesión",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val username = remember { mutableStateOf("") }
            val password = remember { mutableStateOf("") }

            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username.value,
                    onValueChange = { username.value = it },
                    label = { Text("Usuario", style = TextStyle(color = MaterialTheme.colors.primary, fontSize = 12.sp)) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = { Text("Contraseña", style = TextStyle(color = MaterialTheme.colors.primary, fontSize = 12.sp)) },
                    textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { onLoginClicked(username.value, password.value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Iniciar Sesión", fontSize = 16.sp)
            }
        }
    }
}


@Composable
fun HomeScreen(message: String, listaPedidos: List<ListModel>, navController: NavController) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = message,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp),
                color = Color.Black
            )
            Image(
                painter = painterResource(id = R.drawable.descarga),
                contentDescription = "Logo de la Empresa",
                modifier = Modifier
                    .size(30.dp) // adjust the size as needed
                    .padding(bottom = 0.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listaPedidos) { pedido ->
                    // Elemento de lista para cada pedido
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val metadataString = Gson().toJson(pedido.metadata)
                                Log.e("METADATA_JSON", metadataString)
                                navController.navigate("details/${metadataString}")
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Descripción: ${pedido.vchmensaje}",
                                style = MaterialTheme.typography.body1,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsScreen(navController: NavController, metadata: Metadata?) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Detalles de la actividad",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 15.dp)
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    metadata?.let { metadata ->
                        DetailItem(label = "Asignatura", value = metadata.asignatura)
                        DetailItem(label = "Fecha de\nEntrega", value = metadata.fecha_entrega)
                        DetailItem(label = "Puntuación", value = metadata.puntuacion)
                        DetailItem(label = "Calificación", value = metadata.calificacion)
                    } ?: run {
                        Text(
                            text = "No hay detalles disponibles.",
                            style = MaterialTheme.typography.body2,
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Regresar")
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface
        )
    }
}
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun LoginScreenPreview() {
    UthhVirtualTheme {
        LoginScreen { _, _ -> }
    }
}

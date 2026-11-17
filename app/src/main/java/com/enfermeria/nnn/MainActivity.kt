package com.enfermeria.nnn

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

// --- MODELOS DE DATOS ---
data class NocItem(val codigo: String, val etiqueta: String, val indicadores: List<String>)
data class NicItem(val codigo: String, val etiqueta: String, val actividades: List<String>)
data class DiagnosticoNNN(
    val codigo: String,
    val tipo: String,
    val dominio: String,
    val clase: String,
    val etiqueta: String,
    val definicion: String,
    val factores_relacionados: List<String>,
    val caracteristicas_definitorias: List<String>,
    val noc: List<NocItem>,
    val nic: List<NicItem>,
    val caso_voz: String,
    val palabras_clave_voz: List<String>
)

// --- PALETA DE COLORES VIBRANTE PARA ESTUDIANTES ---
val TealPrimary = Color(0xFF00897B)
val TealDark = Color(0xFF004D40)
val CoralReal = Color(0xFFFF5252)
val AmberRiesgo = Color(0xFFFFB300)
val PurpleQuiz = Color(0xFF7C4DFF)
val BgSoft = Color(0xFFF4F7F9)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val listaDiagnosticos = cargarDatosDesdeJson()

        setContent {
            MaterialTheme {
                AppPrincipal(
                    diagnosticos = listaDiagnosticos,
                    hablar = { texto -> hablarTexto(texto) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "PE")
        }
    }

    private fun hablarTexto(texto: String) {
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "tutor_id")
    }

    private fun cargarDatosDesdeJson(): List<DiagnosticoNNN> {
        return try {
            val json = assets.open("nnn_data.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<DiagnosticoNNN>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPrincipal(diagnosticos: List<DiagnosticoNNN>, hablar: (String) -> Unit) {
    var seccionActual by remember { mutableIntStateOf(0) }
    var diagnosticoSeleccionado by remember { mutableStateOf<DiagnosticoNNN?>(null) }

    Scaffold(
        containerColor = BgSoft,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 12.dp
            ) {
                NavigationBarItem(
                    selected = seccionActual == 0,
                    onClick = { seccionActual = 0; diagnosticoSeleccionado = null },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Explorar NNN", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = seccionActual == 1,
                    onClick = { seccionActual = 1; diagnosticoSeleccionado = null },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("Tutor por Voz", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = seccionActual == 2,
                    onClick = { seccionActual = 2; diagnosticoSeleccionado = null },
                    icon = { Icon(Icons.Default.Style, contentDescription = null) },
                    label = { Text("Flashcards", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (diagnosticoSeleccionado != null) {
                PantallaDetalleInteractiva(
                    diag = diagnosticoSeleccionado!!,
                    volver = { diagnosticoSeleccionado = null },
                    hablar = hablar
                )
            } else {
                when (seccionActual) {
                    0 -> PantallaExplorar(diagnosticos) { diagnosticoSeleccionado = it }
                    1 -> PantallaTutorVoz(diagnosticos, hablar)
                    2 -> PantallaFlashcards(diagnosticos)
                }
            }
        }
    }
}

// --- PANTALLA 1: EXPLORADOR COLORIDO Y DINÁMICO ---
@Composable
fun PantallaExplorar(
    diagnosticos: List<DiagnosticoNNN>,
    alSeleccionar: (DiagnosticoNNN) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var filtroTipo by remember { mutableStateOf("TODOS") }

    val filtrados = diagnosticos.filter { diag ->
        val coincideTipo = filtroTipo == "TODOS" || diag.tipo == filtroTipo
        val coincideTexto = busqueda.isBlank() ||
            diag.etiqueta.contains(busqueda, true) ||
            diag.codigo.contains(busqueda, true) ||
            diag.caracteristicas_definitorias.any { it.contains(busqueda, true) }
        coincideTipo && coincideTexto
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Cabecera con Degradado Clínico Moderno
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(TealDark, TealPrimary)),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "Guía Interactiva NANDA • NOC • NIC",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "¿Qué paciente valoramos hoy?",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    placeholder = { Text("Buscar síntoma, código o etiqueta (ej. fiebre)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Chips de Filtro Rápido con Animación
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("TODOS" to "✨ Todos", "REAL" to "🔴 Diagnósticos Reales", "RIESGO" to "🟡 De Riesgo")) { (clave, titulo) ->
                val seleccionado = filtroTipo == clave
                val bgColor by animateColorAsState(if (seleccionado) TealPrimary else Color.White, label = "")
                val txtColor = if (seleccionado) Color.White else Color.DarkGray
                Surface(
                    shape = RoundedCornerShape(50),
                    color = bgColor,
                    shadowElevation = 3.dp,
                    modifier = Modifier.clickable { filtroTipo = clave }
                ) {
                    Text(
                        text = titulo,
                        color = txtColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Tarjetas de Diagnóstico
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtrados) { diag ->
                val colorBorde = if (diag.tipo == "REAL") CoralReal else AmberRiesgo
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alSeleccionar(diag) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = colorBorde.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "NANDA ${diag.codigo} • ${diag.tipo}",
                                    color = colorBorde,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(diag.etiqueta, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                        Text(diag.dominio, fontSize = 12.sp, color = TealPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(diag.definicion, fontSize = 13.sp, color = Color.DarkGray, maxLines = 2)
                    }
                }
            }
        }
    }
}

// --- PANTALLA 2: ENLACE INTERACTIVO NNN + GENERADOR PAE ---
@Composable
fun PantallaDetalleInteractiva(
    diag: DiagnosticoNNN,
    volver: () -> Unit,
    hablar: (String) -> Unit
) {
    val context = LocalContext.current
    var tabActual by remember { mutableIntStateOf(0) }
    val factoresSeleccionados = remember { mutableStateListOf<String>() }
    val caracteristicasSeleccionadas = remember { mutableStateListOf<String>() }
    val actividadesSeleccionadas = remember { mutableStateListOf<String>() }
    var puntajeInicial by remember { mutableFloatStateOf(2f) }
    var puntajeMeta by remember { mutableFloatStateOf(4f) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra Superior
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(TealDark)
                .padding(16.dp)
        ) {
            IconButton(onClick = volver) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Código ${diag.codigo}", color = Color(0xFF80CBC4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(diag.etiqueta, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = { hablar("${diag.etiqueta}. ${diag.definicion}") }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Escuchar", tint = Color.White)
            }
        }

        // Pestañas Dinámicas NANDA - NOC - NIC
        TabRow(
            selectedTabIndex = tabActual,
            containerColor = Color.White,
            contentColor = TealPrimary
        ) {
            Tab(selected = tabActual == 0, onClick = { tabActual = 0 }, text = { Text("1. NANDA", fontWeight = FontWeight.Bold) })
            Tab(selected = tabActual == 1, onClick = { tabActual = 1 }, text = { Text("2. NOC (Meta)", fontWeight = FontWeight.Bold) })
            Tab(selected = tabActual == 2, onClick = { tabActual = 2 }, text = { Text("3. NIC (Acción)", fontWeight = FontWeight.Bold) })
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tabActual == 0) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📖 Definición Clínica", fontWeight = FontWeight.Bold, color = TealDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(diag.definicion, fontSize = 14.sp)
                        }
                    }
                }
                item {
                    Text("👆 Toca los Factores Relacionados (r/c) de tu paciente:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                items(diag.factores_relacionados) { factor ->
                    ChipSeleccionable(
                        texto = factor,
                        seleccionado = factoresSeleccionados.contains(factor),
                        colorActivo = CoralReal
                    ) {
                        if (factoresSeleccionados.contains(factor)) factoresSeleccionados.remove(factor)
                        else factoresSeleccionados.add(factor)
                    }
                }
                if (diag.caracteristicas_definitorias.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("👆 Toca las Características Definitorias (m/p):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    items(diag.caracteristicas_definitorias) { caract ->
                        ChipSeleccionable(
                            texto = caract,
                            seleccionado = caracteristicasSeleccionadas.contains(caract),
                            colorActivo = TealPrimary
                        ) {
                            if (caracteristicasSeleccionadas.contains(caract)) caracteristicasSeleccionadas.remove(caract)
                            else caracteristicasSeleccionadas.add(caract)
                        }
                    }
                }
            } else if (tabActual == 1) {
                items(diag.noc) { noc ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎯 NOC ${noc.codigo}: ${noc.etiqueta}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TealDark)
                            Spacer(modifier = Modifier.height(8.dp))
                            noc.indicadores.forEach { ind ->
                                Text("• $ind", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Escala Likert Inicial: ${puntajeInicial.toInt()} (Comprometido)", fontWeight = FontWeight.Bold)
                            Slider(value = puntajeInicial, onValueChange = { puntajeInicial = it }, valueRange = 1f..5f, steps = 3)
                            Text("Puntuación Diana (Meta): ${puntajeMeta.toInt()} (Mejoría esperada)", fontWeight = FontWeight.Bold, color = TealPrimary)
                            Slider(value = puntajeMeta, onValueChange = { puntajeMeta = it }, valueRange = 1f..5f, steps = 3)
                        }
                    }
                }
            } else {
                items(diag.nic) { nic ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🩺 NIC ${nic.codigo}: ${nic.etiqueta}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = PurpleQuiz)
                            Spacer(modifier = Modifier.height(8.dp))
                            nic.actividades.forEach { act ->
                                ChipSeleccionable(
                                    texto = act,
                                    seleccionado = actividadesSeleccionadas.contains(act),
                                    colorActivo = PurpleQuiz
                                ) {
                                    if (actividadesSeleccionadas.contains(act)) actividadesSeleccionadas.remove(act)
                                    else actividadesSeleccionadas.add(act)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }

        // Tarjeta Flotante Inferior: Generador Automático de PAE
        Surface(color = Color.White, shadowElevation = 16.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                val rcTexto = if (factoresSeleccionados.isEmpty()) "[elige factores r/c]" else factoresSeleccionados.joinToString(", ")
                val mpTexto = if (diag.tipo == "RIESGO") "" else if (caracteristicasSeleccionadas.isEmpty()) " m/p [elige signos m/p]" else " m/p ${caracteristicasSeleccionadas.joinToString(", ")}"
                val paeRedactado = "(${diag.codigo}) ${diag.etiqueta} r/c $rcTexto$mpTexto.\nMeta NOC: Mantener en ${puntajeInicial.toInt()} y aumentar a ${puntajeMeta.toInt()}.\nIntervenciones NIC: ${actividadesSeleccionadas.joinToString("; ")}."

                Text("📝 Tu PAE en tiempo real:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TealPrimary)
                Text(paeRedactado, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PAE", paeRedactado))
                        Toast.makeText(context, "¡PAE copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copiar PAE para mi informe", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChipSeleccionable(texto: String, seleccionado: Boolean, colorActivo: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (seleccionado) colorActivo.copy(alpha = 0.14f) else Color.White, label = "")
    val borde by animateColorAsState(if (seleccionado) colorActivo else Color.LightGray, label = "")
    val escala by animateFloatAsState(if (seleccionado) 1.02f else 1f, label = "")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(escala)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, borde, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Icon(
            imageVector = if (seleccionado) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (seleccionado) colorActivo else Color.Gray
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(texto, fontSize = 14.sp, fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal)
    }
}

// --- PANTALLA 3: TUTOR CLÍNICO POR VOZ (SIMULADOR INTERACTIVO) ---
@Composable
fun PantallaTutorVoz(diagnosticos: List<DiagnosticoNNN>, hablar: (String) -> Unit) {
    val context = LocalContext.current
    var indiceCaso by remember { mutableIntStateOf(0) }
    val casoActual = diagnosticos.getOrNull(indiceCaso) ?: return

    var escuchando by remember { mutableStateOf(false) }
    var respuestaEstudiante by remember { mutableStateOf("") }
    var feedbackTutor by remember { mutableStateOf("Presiona el micrófono y dime qué diagnóstico NANDA, NOC y NIC aplicarías.") }
    var colorFeedback by remember { mutableStateOf(TealPrimary) }

    val permisoAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) Toast.makeText(context, "Activa el permiso de micrófono para practicar por voz", Toast.LENGTH_LONG).show()
    }

    // Animación de pulso para el botón de micrófono
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val pulso by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (escuchando) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulso"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🏥 CASO CLÍNICO #${indiceCaso + 1}", color = PurpleQuiz, fontWeight = FontWeight.ExtraBold)
                    TextButton(onClick = { hablar(casoActual.caso_voz) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Escuchar Caso")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(casoActual.caso_voz, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón de Micrófono Dinámico
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .scale(pulso)
                .clip(CircleShape)
                .background(if (escuchando) CoralReal else PurpleQuiz)
                .clickable {
                    permisoAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    iniciarReconocimientoVoz(
                        context = context,
                        alEmpezar = { escuchando = true },
                        alTerminar = { textoReconocido ->
                            escuchando = false
                            respuestaEstudiante = textoReconocido
                            val aciertos = casoActual.palabras_clave_voz.filter { clave ->
                                textoReconocido.lowercase().contains(clave.lowercase())
                            }
                            if (aciertos.isNotEmpty()) {
                                colorFeedback = TealPrimary
                                feedbackTutor = "¡Excelente razonamiento clínico! Detectaste correctamente (${casoActual.codigo}) ${casoActual.etiqueta}. Palabras clave identificadas: ${aciertos.joinToString(", ")}."
                                hablar("Excelente, acertaste el diagnóstico ${casoActual.etiqueta}.")
                            } else {
                                colorFeedback = AmberRiesgo
                                feedbackTutor = "Pista clínica: Revisa los signos del paciente. Pertenece al ${casoActual.dominio}. Intenta mencionar la etiqueta NANDA o la intervención principal."
                                hablar("Casi. Te doy una pista: revisa el ${casoActual.dominio}.")
                            }
                        }
                    )
                }
        ) {
            Icon(
                imageVector = if (escuchando) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = "Hablar",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            if (escuchando) "🎙️ Escuchando tu diagnóstico..." else "Toca el micrófono y habla",
            fontWeight = FontWeight.Bold,
            color = if (escuchando) CoralReal else Color.DarkGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (respuestaEstudiante.isNotBlank()) {
            Text("Dijiste: \"$respuestaEstudiante\"", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = colorFeedback.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = feedbackTutor,
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                indiceCaso = (indiceCaso + 1) % diagnosticos.size
                respuestaEstudiante = ""
                feedbackTutor = "Presiona el micrófono y dime qué diagnóstico NANDA, NOC y NIC aplicarías."
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Siguiente Caso Clínico ➡️", fontWeight = FontWeight.Bold)
        }
    }
}

fun iniciarReconocimientoVoz(
    context: Context,
    alEmpezar: () -> Unit,
    alTerminar: (String) -> Unit
) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
    }
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { alEmpezar() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            alTerminar("No se escuchó claramente, intenta de nuevo.")
            recognizer.destroy()
        }
        override fun onResults(results: Bundle?) {
            val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            alTerminar(texto)
            recognizer.destroy()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    recognizer.startListening(intent)
}

// --- PANTALLA 4: FLASHCARDS INTERACTIVAS DE ESTUDIO ---
@Composable
fun PantallaFlashcards(diagnosticos: List<DiagnosticoNNN>) {
    var indice by remember { mutableIntStateOf(0) }
    var mostrarRespuesta by remember { mutableStateOf(false) }
    val actual = diagnosticos.getOrNull(indice) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧠 MODO REPASO RÁPIDO", fontWeight = FontWeight.ExtraBold, color = PurpleQuiz, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = if (mostrarRespuesta) TealDark else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clickable { mostrarRespuesta = !mostrarRespuesta }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!mostrarRespuesta) {
                    Text("¿Qué diagnóstico NANDA corresponde a esta definición?", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(actual.definicion, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("👆 Toca la tarjeta para girar y ver la respuesta", color = TealPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                } else {
                    Text("NANDA ${actual.codigo}", color = AmberRiesgo, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(actual.etiqueta, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("NOC principal: ${actual.noc.firstOrNull()?.etiqueta ?: "-"}", color = Color.White.copy(alpha = 0.9f))
                    Text("NIC principal: ${actual.nic.firstOrNull()?.etiqueta ?: "-"}", color = Color.White.copy(alpha = 0.9f))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                mostrarRespuesta = false
                indice = (indice + 1) % diagnosticos.size
            },
            colors = ButtonDefaults.buttonColors(containerColor = PurpleQuiz),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Siguiente Tarjeta 🔄", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

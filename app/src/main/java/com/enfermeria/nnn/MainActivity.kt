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
import java.util.Locale

// --- MODELOS DE DATOS OFICIALES ---
data class DominioNandaInfo(val id: Int, val nombre: String, val clases: List<String>)
data class NandaTax(val titulo: String, val resumen: String, val dominios: List<DominioNandaInfo>)
data class NocNicTax(val titulo: String, val resumen: String, val dominios: List<String>)
data class TaxonomiaOficial(val nanda: NandaTax, val noc: NocNicTax, val nic: NocNicTax)
data class TerminoGlosario(val sigla: String, val termino: String, val definicion: String)

data class NocItem(
    val codigo: String,
    val dominio_noc: String,
    val clase_noc: String,
    val etiqueta: String,
    val indicadores: List<String>
)
data class NicItem(
    val codigo: String,
    val dominio_nic: String,
    val clase_nic: String,
    val etiqueta: String,
    val actividades: List<String>
)
data class DiagnosticoNNN(
    val codigo: String,
    val tipo: String,
    val dominio: String,
    val clase: String,
    val etiqueta: String,
    val definicion: String,
    val nota_edicion: String,
    val puntos_clave: String,
    val factores_relacionados: List<String>,
    val caracteristicas_definitorias: List<String>,
    val noc: List<NocItem>,
    val nic: List<NicItem>,
    val caso_voz: String,
    val palabras_clave_voz: List<String>
)
data class BaseDatosNNN(
    val taxonomia: TaxonomiaOficial,
    val terminologia: List<TerminoGlosario>,
    val diagnosticos: List<DiagnosticoNNN>
)

// --- PALETA CLÍNICA ---
val TealPrimary = Color(0xFF00897B)
val TealDark = Color(0xFF004D40)
val CoralReal = Color(0xFFE53935)
val AmberRiesgo = Color(0xFFF59E0B)
val PurpleQuiz = Color(0xFF6D28D9)
val BgSoft = Color(0xFFF1F5F9)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val baseDatos = cargarDatosDesdeJson()

        setContent {
            MaterialTheme {
                AppPrincipal(
                    baseDatos = baseDatos,
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

    private fun cargarDatosDesdeJson(): BaseDatosNNN {
        return try {
            val json = assets.open("nnn_data.json").bufferedReader().use { it.readText() }
            Gson().fromJson(json, BaseDatosNNN::class.java)
        } catch (e: Exception) {
            BaseDatosNNN(
                taxonomia = TaxonomiaOficial(
                    NandaTax("NANDA-I 2024-2026", "13 Dominios y 48 Clases", emptyList()),
                    NocNicTax("NOC", "7 Dominios y 36 Clases", emptyList()),
                    NocNicTax("NIC", "7 Dominios y 30 Clases", emptyList())
                ),
                terminologia = emptyList(),
                diagnosticos = emptyList()
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun AppPrincipal(baseDatos: BaseDatosNNN, hablar: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("progreso_nnn", Context.MODE_PRIVATE) }
    val dominados = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("dominados", emptySet()) ?: emptySet())
        }
    }

    fun alternarDominado(codigo: String) {
        if (dominados.contains(codigo)) dominados.remove(codigo) else dominados.add(codigo)
        prefs.edit().putStringSet("dominados", dominados.toSet()).apply()
    }

    var seccionActual by remember { mutableIntStateOf(0) }
    var diagnosticoSeleccionado by remember { mutableStateOf<DiagnosticoNNN?>(null) }
    var mostrarTerminologia by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgSoft,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 12.dp) {
                NavigationBarItem(
                    selected = seccionActual == 0 && !mostrarTerminologia,
                    onClick = { seccionActual = 0; diagnosticoSeleccionado = null; mostrarTerminologia = false },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Explorar NNN", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = seccionActual == 1 && !mostrarTerminologia,
                    onClick = { seccionActual = 1; diagnosticoSeleccionado = null; mostrarTerminologia = false },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("Tutor por Voz", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = seccionActual == 2 && !mostrarTerminologia,
                    onClick = { seccionActual = 2; diagnosticoSeleccionado = null; mostrarTerminologia = false },
                    icon = { Icon(Icons.Default.Style, contentDescription = null) },
                    label = { Text("Estudio", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                mostrarTerminologia -> {
                    PantallaTerminologia(
                        terminologia = baseDatos.terminologia,
                        taxonomia = baseDatos.taxonomia,
                        volver = { mostrarTerminologia = false }
                    )
                }
                diagnosticoSeleccionado != null -> {
                    PantallaDetalleInteractiva(
                        diag = diagnosticoSeleccionado!!,
                        esDominado = dominados.contains(diagnosticoSeleccionado!!.codigo),
                        onToggleDominado = { alternarDominado(diagnosticoSeleccionado!!.codigo) },
                        volver = { diagnosticoSeleccionado = null },
                        hablar = hablar
                    )
                }
                seccionActual == 0 -> {
                    PantallaExplorar(
                        baseDatos = baseDatos,
                        dominados = dominados,
                        abrirTerminologia = { mostrarTerminologia = true },
                        alSeleccionar = { diagnosticoSeleccionado = it }
                    )
                }
                seccionActual == 1 -> {
                    PantallaTutorVoz(baseDatos.diagnosticos, hablar)
                }
                else -> {
                    PantallaFlashcards(
                        diagnosticos = baseDatos.diagnosticos,
                        dominados = dominados,
                        abrirTerminologia = { mostrarTerminologia = true }
                    )
                }
            }
        }
    }
}

// --- PANTALLA 1: EXPLORADOR JERÁRQUICO + EVALUACIÓN POR VOZ DE SÍNTOMAS ---
@Composable
fun PantallaExplorar(
    baseDatos: BaseDatosNNN,
    dominados: List<String>,
    abrirTerminologia: () -> Unit,
    alSeleccionar: (DiagnosticoNNN) -> Unit
) {
    val context = LocalContext.current
    val diagnosticos = baseDatos.diagnosticos
    var busqueda by remember { mutableStateOf("") }
    var escuchandoBusqueda by remember { mutableStateOf(false) }
    var filtroTipo by remember { mutableStateOf("TODOS") }
    var dominioSeleccionado by remember { mutableStateOf<DominioNandaInfo?>(null) }
    var claseSeleccionada by remember { mutableStateOf("TODAS") }
    var modoEvaluacionVoz by remember { mutableStateOf(false) }

    val permisoMicBusqueda = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) Toast.makeText(context, "Activa el micrófono para dictar síntomas", Toast.LENGTH_SHORT).show()
    }

    // Coincidencia inteligente por palabras individuales cuando dictan varios síntomas
    val palabrasConsulta = remember(busqueda) {
        busqueda.lowercase()
            .replace(Regex("[^a-záéíóúñ0-9 ]"), " ")
            .split(" ")
            .filter { it.length >= 4 && it !in listOf("paciente", "tiene", "presenta", "con", "para", "como", "esta", "sobre") }
    }

    val filtrados = diagnosticos.filter { diag ->
        val coincideTipo = when (filtroTipo) {
            "TODOS" -> true
            "DOMINADOS" -> dominados.contains(diag.codigo)
            else -> diag.tipo == filtroTipo
        }
        val coincideDominio = dominioSeleccionado == null || diag.dominio == dominioSeleccionado!!.nombre
        val coincideClase = claseSeleccionada == "TODAS" || diag.clase == claseSeleccionada

        val coincideTexto = if (busqueda.isBlank()) {
            true
        } else {
            val textoCompleto = "${diag.codigo} ${diag.etiqueta} ${diag.dominio} ${diag.clase} ${diag.definicion} " +
                "${diag.factores_relacionados.joinToString(" ")} ${diag.caracteristicas_definitorias.joinToString(" ")} " +
                diag.palabras_clave_voz.joinToString(" ")
            textoCompleto.contains(busqueda.trim(), true) ||
                (palabrasConsulta.isNotEmpty() && palabrasConsulta.any { pal -> textoCompleto.lowercase().contains(pal) })
        }
        coincideTipo && coincideDominio && coincideClase && coincideTexto
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Cabecera
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(TealDark, TealPrimary)),
                    shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "NANDA-I 2024-2026 • NOC • NIC",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { abrirTerminologia() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Terminología", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "¿Qué paciente valoramos hoy?",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it; if (it.isBlank()) modoEvaluacionVoz = false },
                    placeholder = {
                        Text(
                            if (escuchandoBusqueda) "🎙️ Dicta los signos y síntomas..." else "Dicta síntomas (ej. fiebre y piel caliente)...",
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (busqueda.isNotEmpty()) {
                                IconButton(onClick = { busqueda = ""; modoEvaluacionVoz = false }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.Gray)
                                }
                            }
                            IconButton(
                                onClick = {
                                    permisoMicBusqueda.launch(Manifest.permission.RECORD_AUDIO)
                                    iniciarReconocimientoVoz(
                                        context = context,
                                        alEmpezar = { escuchandoBusqueda = true },
                                        alTerminar = { texto ->
                                            escuchandoBusqueda = false
                                            if (!texto.startsWith("No se escuchó")) {
                                                busqueda = texto
                                                modoEvaluacionVoz = true
                                            } else {
                                                Toast.makeText(context, texto, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (escuchandoBusqueda) Icons.Default.GraphicEq else Icons.Default.Mic,
                                    contentDescription = "Dictar síntomas",
                                    tint = if (escuchandoBusqueda) CoralReal else TealPrimary
                                )
                            }
                        }
                    },
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

        // Filtros por Tipo y Progreso
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tipos = listOf(
                "TODOS" to "✨ Todos (${diagnosticos.size})",
                "REAL" to "🔴 Reales",
                "RIESGO" to "🟡 Riesgo",
                "PROMOCION" to "🟢 Promoción",
                "DOMINADOS" to "✅ Dominados (${dominados.size})"
            )
            items(tipos) { (clave, titulo) ->
                val sel = filtroTipo == clave
                val bg by animateColorAsState(if (sel) TealPrimary else Color.White, label = "")
                Surface(
                    shape = RoundedCornerShape(50),
                    color = bg,
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable { filtroTipo = clave }
                ) {
                    Text(
                        text = titulo,
                        color = if (sel) Color.White else Color.DarkGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Nivel 1: Carrusel de los 13 Dominios NANDA-I
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val sel = dominioSeleccionado == null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) TealDark else Color(0xFFE0F2F1),
                    modifier = Modifier.clickable { dominioSeleccionado = null; claseSeleccionada = "TODAS" }
                ) {
                    Text(
                        "📚 13 Dominios",
                        color = if (sel) Color.White else TealDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            items(baseDatos.taxonomia.nanda.dominios) { dom ->
                val sel = dominioSeleccionado?.id == dom.id
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) TealDark else Color(0xFFE0F2F1),
                    modifier = Modifier.clickable {
                        dominioSeleccionado = dom
                        claseSeleccionada = "TODAS"
                    }
                ) {
                    Text(
                        "Dominio ${dom.id}",
                        color = if (sel) Color.White else TealDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Nivel 2: Clases del Dominio seleccionado (48 clases en total en NANDA-I)
        if (dominioSeleccionado != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    text = "${dominioSeleccionado!!.nombre} (${dominioSeleccionado!!.clases.size} clases):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TealDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        val sel = claseSeleccionada == "TODAS"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) PurpleQuiz else Color.White,
                            modifier = Modifier.clickable { claseSeleccionada = "TODAS" }
                        ) {
                            Text(
                                "Todas las clases",
                                color = if (sel) Color.White else PurpleQuiz,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    items(dominioSeleccionado!!.clases) { cls ->
                        val sel = claseSeleccionada == cls
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) PurpleQuiz else Color.White,
                            modifier = Modifier.clickable { claseSeleccionada = cls }
                        ) {
                            Text(
                                cls,
                                color = if (sel) Color.White else PurpleQuiz,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Guía de Evaluación Clínica cuando el estudiante dicta síntomas por voz
        if (modoEvaluacionVoz && busqueda.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE9FE)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "🩺 Guía de Valoración Clínica para: \"$busqueda\"",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = PurpleQuiz
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (filtrados.isNotEmpty()) {
                        val top = filtrados.first()
                        Text(
                            "1. NANDA sugerido: (${top.codigo}) ${top.etiqueta} [${top.dominio}].\n" +
                            "2. Meta NOC a evaluar: ${top.noc.firstOrNull()?.etiqueta ?: "-"} (Escala 1 a 5).\n" +
                            "3. Acción NIC prioritaria: ${top.nic.firstOrNull()?.etiqueta ?: "-"}.\n" +
                            "Toca una tarjeta abajo para armar el PAE completo.",
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B)
                        )
                    } else {
                        Text(
                            "No se halló coincidencia directa. Recuerda dictar respuestas humanas o signos clínicos (ej. 'fiebre', 'dolor agudo', 'disnea', 'edema', 'estreñimiento') en lugar de diagnósticos médicos.",
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }

        // Listado de Diagnósticos
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtrados.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Sin diagnósticos en esta selección", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Prueba seleccionando 'Todas las clases' o dicta síntomas observables del paciente.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(filtrados) { diag ->
                val colorBorde = when (diag.tipo) {
                    "REAL" -> CoralReal
                    "RIESGO" -> AmberRiesgo
                    else -> TealPrimary
                }
                val yaDominado = dominados.contains(diag.codigo)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alSeleccionar(diag) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(color = colorBorde.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "NANDA ${diag.codigo} • ${diag.tipo}",
                                    color = colorBorde,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (yaDominado) {
                                    Text("✅ Dominado", fontSize = 11.sp, color = TealPrimary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(diag.etiqueta, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                        Text("${diag.dominio} • ${diag.clase}", fontSize = 11.sp, color = TealPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(diag.definicion, fontSize = 13.sp, color = Color.DarkGray, maxLines = 2)
                    }
                }
            }
        }
    }
}

// --- PANTALLA 2: ENLACE INTERACTIVO NANDA-NOC-NIC + GENERADOR PAE CON ALERTAS ---
@Composable
fun PantallaDetalleInteractiva(
    diag: DiagnosticoNNN,
    esDominado: Boolean,
    onToggleDominado: () -> Unit,
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
        // Cabecera
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(TealDark)
                .padding(14.dp)
        ) {
            IconButton(onClick = volver) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${diag.codigo} • ${diag.clase}", color = Color(0xFF80CBC4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(diag.etiqueta, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = onToggleDominado) {
                Icon(
                    imageVector = if (esDominado) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Marcar dominado",
                    tint = if (esDominado) Color(0xFF69F0AE) else Color.White
                )
            }
            IconButton(onClick = { hablar("${diag.etiqueta}. ${diag.definicion}") }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Escuchar", tint = Color.White)
            }
        }

        // Pestañas NANDA - NOC - NIC
        TabRow(selectedTabIndex = tabActual, containerColor = Color.White, contentColor = TealPrimary) {
            Tab(selected = tabActual == 0, onClick = { tabActual = 0 }, text = { Text("1. NANDA", fontWeight = FontWeight.Bold) })
            Tab(selected = tabActual == 1, onClick = { tabActual = 1 }, text = { Text("2. NOC", fontWeight = FontWeight.Bold) })
            Tab(selected = tabActual == 2, onClick = { tabActual = 2 }, text = { Text("3. NIC", fontWeight = FontWeight.Bold) })
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tabActual == 0) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("📖 Definición Oficial (NANDA-I 2024-2026)", fontWeight = FontWeight.Bold, color = TealDark, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(diag.definicion, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(color = Color(0xFFF0FDF4), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "📌 ${diag.nota_edicion}",
                                    fontSize = 12.sp,
                                    color = TealDark,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "💡 Clave de estudio: ${diag.puntos_clave}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF92400E),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        if (diag.tipo == "PROMOCION") "👆 Motivación / Disposición:" else "👆 Selecciona Factores Relacionados / de Riesgo (r/c):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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
                if (diag.tipo == "RIESGO") {
                    item {
                        Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                "⚠️ Alerta académica PAE: Este es un diagnóstico de RIESGO. No lleva características definitorias ('manifestado por') porque el problema aún no está presente.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else if (diag.caracteristicas_definitorias.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("👆 Selecciona Características Definitorias (m/p):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            Text("${noc.dominio_noc} • ${noc.clase_noc}", fontSize = 11.sp, color = TealPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🎯 NOC ${noc.codigo}: ${noc.etiqueta}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TealDark)
                            Spacer(modifier = Modifier.height(8.dp))
                            noc.indicadores.forEach { ind ->
                                Text("• $ind", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Escala Likert Inicial: ${puntajeInicial.toInt()} / 5", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Slider(value = puntajeInicial, onValueChange = { puntajeInicial = it }, valueRange = 1f..5f, steps = 3)
                            Text("Puntuación Diana (Meta): ${puntajeMeta.toInt()} / 5", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealPrimary)
                            Slider(value = puntajeMeta, onValueChange = { puntajeMeta = it }, valueRange = 1f..5f, steps = 3)
                        }
                    }
                }
            } else {
                items(diag.nic) { nic ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("${nic.dominio_nic} • ${nic.clase_nic}", fontSize = 11.sp, color = PurpleQuiz, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
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

        // Generador PAE en tiempo real
        Surface(color = Color.White, shadowElevation = 14.dp) {
            Column(modifier = Modifier.padding(14.dp)) {
                val rcTexto = if (factoresSeleccionados.isEmpty()) "[selecciona factor r/c]" else factoresSeleccionados.joinToString(", ")
                val mpTexto = when (diag.tipo) {
                    "RIESGO" -> ""
                    else -> if (caracteristicasSeleccionadas.isEmpty()) " m/p [selecciona signos m/p]" else " m/p ${caracteristicasSeleccionadas.joinToString(", ")}"
                }
                val nocActual = diag.noc.firstOrNull()
                val nicActual = diag.nic.firstOrNull()
                val paeRedactado = "DIAGNÓSTICO NANDA: (${diag.codigo}) ${diag.etiqueta} r/c $rcTexto$mpTexto.\n" +
                    "RESULTADO NOC: (${nocActual?.codigo ?: "-"}) ${nocActual?.etiqueta ?: "-"} — Mantener en ${puntajeInicial.toInt()}, Aumentar a ${puntajeMeta.toInt()}.\n" +
                    "INTERVENCIÓN NIC: (${nicActual?.codigo ?: "-"}) ${nicActual?.etiqueta ?: "-"}: ${actividadesSeleccionadas.joinToString("; ")}."

                Text("📝 Borrador PAE Estructurado:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TealPrimary)
                Text(paeRedactado, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PAE", paeRedactado))
                        Toast.makeText(context, "¡PAE oficial copiado!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
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
    val escala by animateFloatAsState(if (seleccionado) 1.01f else 1f, label = "")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(escala)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, borde, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(
            imageVector = if (seleccionado) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (seleccionado) colorActivo else Color.Gray
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(texto, fontSize = 13.sp, fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal)
    }
}

// --- PANTALLA 3: TUTOR CLÍNICO POR VOZ CON PISTAS GRADUALES Y RESPUESTA OFICIAL ---
@Composable
fun PantallaTutorVoz(diagnosticos: List<DiagnosticoNNN>, hablar: (String) -> Unit) {
    val context = LocalContext.current
    var indiceCaso by remember { mutableIntStateOf(0) }
    val casoActual = diagnosticos.getOrNull(indiceCaso) ?: return

    var escuchando by remember { mutableStateOf(false) }
    var intentosFallidos by remember { mutableIntStateOf(0) }
    var respuestaEstudiante by remember { mutableStateOf("") }
    var feedbackTutor by remember { mutableStateOf("Presiona el micrófono y di qué diagnóstico NANDA-I corresponde a este caso.") }
    var colorFeedback by remember { mutableStateOf(TealPrimary) }
    var respuestaRevelada by remember { mutableStateOf(false) }

    val permisoAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) Toast.makeText(context, "Activa el permiso de micrófono", Toast.LENGTH_LONG).show()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val pulso by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (escuchando) 1.22f else 1.04f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulso"
    )

    fun revelarRespuestaOficial() {
        respuestaRevelada = true
        colorFeedback = TealDark
        val nocRef = casoActual.noc.firstOrNull()
        val nicRef = casoActual.nic.firstOrNull()
        feedbackTutor = "✅ RESPUESTA OFICIAL (NANDA-I 2024-2026):\n" +
            "• Diagnóstico: (${casoActual.codigo}) ${casoActual.etiqueta} [${casoActual.tipo}]\n" +
            "• Ubicación: ${casoActual.dominio} > ${casoActual.clase}\n" +
            "• NOC recomendado: (${nocRef?.codigo ?: "-"}) ${nocRef?.etiqueta ?: "-"}\n" +
            "• NIC recomendado: (${nicRef?.codigo ?: "-"}) ${nicRef?.etiqueta ?: "-"}\n" +
            "• Fundamento: ${casoActual.puntos_clave}"
        hablar("La respuesta oficial es ${casoActual.etiqueta}, código ${casoActual.codigo}.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🏥 CASO CLÍNICO #${indiceCaso + 1} DE ${diagnosticos.size}", color = PurpleQuiz, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    TextButton(onClick = { hablar(casoActual.caso_voz) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Escuchar", fontSize = 12.sp)
                    }
                }
                Text(casoActual.caso_voz, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
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
                                revelarRespuestaOficial()
                            } else {
                                intentosFallidos++
                                colorFeedback = AmberRiesgo
                                if (intentosFallidos == 1) {
                                    feedbackTutor = "💡 Pista 1 (Dominio): Revisa el ${casoActual.dominio}. Es un diagnóstico de tipo ${casoActual.tipo}. Intenta de nuevo."
                                    hablar("Pista uno. Revisa el ${casoActual.dominio}.")
                                } else if (intentosFallidos == 2) {
                                    feedbackTutor = "💡 Pista 2 (Clase y Signos): Se ubica en ${casoActual.clase}. Fíjate en: ${casoActual.definicion}"
                                    hablar("Pista dos. Se ubica en ${casoActual.clase}.")
                                } else {
                                    revelarRespuestaOficial()
                                }
                            }
                        }
                    )
                }
        ) {
            Icon(
                imageVector = if (escuchando) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = "Hablar",
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (escuchando) "🎙️ Escuchando tu respuesta..." else "Toca el micrófono para responder",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (escuchando) CoralReal else Color.DarkGray
        )

        if (respuestaEstudiante.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Dijiste: \"$respuestaEstudiante\"", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = colorFeedback.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = feedbackTutor,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (!respuestaRevelada) {
                OutlinedButton(
                    onClick = { revelarRespuestaOficial() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver Respuesta Oficial", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Button(
                onClick = {
                    indiceCaso = (indiceCaso + 1) % diagnosticos.size
                    intentosFallidos = 0
                    respuestaRevelada = false
                    respuestaEstudiante = ""
                    colorFeedback = TealPrimary
                    feedbackTutor = "Presiona el micrófono y di qué diagnóstico NANDA-I corresponde a este caso."
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Siguiente Caso ➡️", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
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

// --- PANTALLA 4: FLASHCARDS + ACCESO A TERMINOLOGÍA ---
@Composable
fun PantallaFlashcards(
    diagnosticos: List<DiagnosticoNNN>,
    dominados: List<String>,
    abrirTerminologia: () -> Unit
) {
    var indice by remember { mutableIntStateOf(0) }
    var mostrarRespuesta by remember { mutableStateOf(false) }
    val actual = diagnosticos.getOrNull(indice) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🧠 REPASO RÁPIDO (${dominados.size}/${diagnosticos.size})", fontWeight = FontWeight.ExtraBold, color = PurpleQuiz, fontSize = 14.sp)
            OutlinedButton(onClick = abrirTerminologia, shape = RoundedCornerShape(50)) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Terminología", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (mostrarRespuesta) TealDark else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .clickable { mostrarRespuesta = !mostrarRespuesta }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!mostrarRespuesta) {
                    Text("${actual.dominio} • ${actual.clase}", color = TealPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(actual.definicion, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(22.dp))
                    Text("👆 Toca la tarjeta para ver etiqueta y enlace NNN", color = PurpleQuiz, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                } else {
                    Text("NANDA-I ${actual.codigo} • ${actual.tipo}", color = AmberRiesgo, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(actual.etiqueta, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("NOC: ${actual.noc.firstOrNull()?.etiqueta ?: "-"}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    Text("NIC: ${actual.nic.firstOrNull()?.etiqueta ?: "-"}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }

        Button(
            onClick = {
                mostrarRespuesta = false
                indice = (indice + 1) % diagnosticos.size
            },
            colors = ButtonDefaults.buttonColors(containerColor = PurpleQuiz),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Siguiente Tarjeta 🔄", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// --- PANTALLA 5: GLOSARIO DE TERMINOLOGÍA Y ESTRUCTURA DE LOS 3 LIBROS ---
@Composable
fun PantallaTerminologia(
    terminologia: List<TerminoGlosario>,
    taxonomia: TaxonomiaOficial,
    volver: () -> Unit
) {
    var filtro by remember { mutableStateOf("") }
    val listaFiltrada = terminologia.filter {
        filtro.isBlank() || it.sigla.contains(filtro, true) || it.termino.contains(filtro, true) || it.definicion.contains(filtro, true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(TealDark)
                .padding(14.dp)
        ) {
            IconButton(onClick = volver) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column {
                Text("Terminología Oficial y Taxonomías", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Estructura verificada de los 3 libros", color = Color(0xFF80CBC4), fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = filtro,
            onValueChange = { filtro = it },
            placeholder = { Text("Buscar sigla o término (ej. PAE, Likert, r/c)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("📚 Estructura Jerárquica de los 3 Libros", fontWeight = FontWeight.ExtraBold, color = TealDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• ${taxonomia.nanda.titulo}: ${taxonomia.nanda.resumen}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("• ${taxonomia.noc.titulo}: ${taxonomia.noc.resumen}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("• ${taxonomia.nic.titulo}: ${taxonomia.nic.resumen}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            items(listaFiltrada) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("${item.sigla} — ${item.termino}", fontWeight = FontWeight.ExtraBold, color = PurpleQuiz, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(item.definicion, fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}

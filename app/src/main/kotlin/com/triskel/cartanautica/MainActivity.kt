package com.triskel.cartanautica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triskel.cartanautica.ui.theme.CartaNauticaTheme
import kotlin.math.*

const val LAT_MAX = 36.3333
const val LAT_MIN = 35.6667
const val LON_MIN = 5.1667
const val LON_MAX = 6.3333

// Vector ahora almacena solo coordenadas Float
data class Vector(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CartaNauticaTheme {
                Surface(Modifier.fillMaxSize()) {
                    CartaNauticaScreen()
                }
            }
        }
    }
}

fun latLonToPixel(
    lat: Double,
    lon: Double,
    imageWidth: Float,
    imageHeight: Float
): Offset {
    val xNorm = (LON_MAX - lon) / (LON_MAX - LON_MIN)
    val yNorm = (LAT_MAX - lat) / (LAT_MAX - LAT_MIN)
    return Offset(
        (xNorm * imageWidth).toFloat(),
        (yNorm * imageHeight).toFloat()
    )
}

@Composable
fun CartaNauticaScreen() {
    var points by remember { mutableStateOf(listOf<Pair<Double, Double>>()) }
    var showDialog by remember { mutableStateOf(false) }
    var vectors by remember { mutableStateOf(listOf<Vector>()) }
    var showVectorDialog by remember { mutableStateOf(false) }
    var vectorStart by remember { mutableStateOf<Offset?>(null) }
    var imageWidth by remember { mutableStateOf(0f) }
    var imageHeight by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var vectorMode by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(vectorMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(vectorMode) {
                    if (vectorMode) {
                        detectTapGestures { tapOffset ->
                            val actualStart = Offset(
                                (tapOffset.x - offsetX) / scale,
                                (tapOffset.y - offsetY) / scale
                            )
                            vectorStart = actualStart
                            showVectorDialog = true
                        }
                    }
                },
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .wrapContentWidth(Alignment.Start)
                    .fillMaxHeight()
            ) {
                Image(
                    painter = painterResource(R.drawable.carta_estrecho),
                    contentDescription = null,
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            imageWidth = it.size.width.toFloat()
                            imageHeight = it.size.height.toFloat()
                        }
                )
                val density = LocalDensity.current
                val pointSize = 10.dp
                points.forEach { (lat, lon) ->
                    if (imageWidth == 0f || imageHeight == 0f) return@forEach
                    val pos = latLonToPixel(lat, lon, imageWidth, imageHeight)
                    Box(
                        modifier = Modifier
                            .offset {
                                with(density) {
                                    val r = pointSize.toPx()
                                    IntOffset(
                                        (pos.x - r / 2).roundToInt(),
                                        (pos.y - r / 2).roundToInt()
                                    )
                                }
                            }
                            .size(pointSize)
                            .background(Color.Red)
                    )
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    vectors.forEach { vector ->
                        drawVector(
                            Offset(vector.startX * scale + offsetX, vector.startY * scale + offsetY),
                            Offset(vector.endX * scale + offsetX, vector.endY * scale + offsetY)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { showDialog = true }) { Text("•", fontSize = 24.sp) }
            Button(onClick = { vectorMode = true }) { Text("→", fontSize = 24.sp) }
            Button(onClick = { }) { Text("←", fontSize = 24.sp) }
            Button(onClick = { }) { Text("↝", fontSize = 24.sp) }
            Button(onClick = { }) { Text("○", fontSize = 24.sp) }
            Button(onClick = { }) { Text("B", fontSize = 24.sp) }
            Button(onClick = { }) { Text("P", fontSize = 24.sp) }
            Button(onClick = { }) { Text("X", fontSize = 24.sp) }
        }
    }

    if (showDialog) {
        AddPointDialog(
            onDismiss = { showDialog = false },
            onAddPoint = { latG, latM, lonG, lonM ->
                val latDeg = latG.toDoubleOrNull() ?: return@AddPointDialog
                val latMin = latM.toDoubleOrNull() ?: 0.0
                val lonDeg = lonG.toDoubleOrNull() ?: return@AddPointDialog
                val lonMin = lonM.toDoubleOrNull() ?: 0.0
                points = points + ((latDeg + latMin / 60.0) to (lonDeg + lonMin / 60.0))
                showDialog = false
            }
        )
    }

    if (showVectorDialog && vectorStart != null) {
        AddVectorDialog(
            onDismiss = { showVectorDialog = false; vectorStart = null; vectorMode = false },
            onAddVector = { rumbo, distancia ->
                val start = vectorStart!!
                val rad = Math.toRadians(rumbo.toDouble())
                val deltaLat = distancia / 60.0
                val dx = (sin(rad) * deltaLat * (LON_MAX - LON_MIN) / (LAT_MAX - LAT_MIN)).toFloat()
                val dy = (-cos(rad) * deltaLat).toFloat()
                vectors = vectors + Vector(
                    startX = start.x,
                    startY = start.y,
                    endX = start.x + dx * imageWidth / (LON_MAX - LON_MIN).toFloat(),
                    endY = start.y + dy * imageHeight / (LAT_MAX - LAT_MIN).toFloat()
                )
                showVectorDialog = false
                vectorStart = null
                vectorMode = false
            }
        )
    }
}

@Composable
fun AddPointDialog(
    onDismiss: () -> Unit,
    onAddPoint: (String, String, String, String) -> Unit
) {
    var latG by remember { mutableStateOf("") }
    var latM by remember { mutableStateOf("") }
    var lonG by remember { mutableStateOf("") }
    var lonM by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir punto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Latitud")
                Row {
                    TextField(latG, { latG = it }, label = { Text("Grados") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(8.dp))
                    TextField(latM, { latM = it }, label = { Text("Minutos") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Spacer(Modifier.height(12.dp))
                Text("Longitud")
                Row {
                    TextField(lonG, { lonG = it }, label = { Text("Grados") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(8.dp))
                    TextField(lonM, { lonM = it }, label = { Text("Minutos") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if(latG.isNotBlank() && lonG.isNotBlank()) onAddPoint(latG, latM, lonG, lonM) }))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAddPoint(latG, latM, lonG, lonM) }) { Text("Añadir") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddVectorDialog(
    onDismiss: () -> Unit,
    onAddVector: (Int, Double) -> Unit
) {
    var rumbo by remember { mutableStateOf("") }
    var distancia by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir vector") },
        text = {
            Row {
                TextField(rumbo, { rumbo = it }, label = { Text("Rumbo") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.width(8.dp))
                TextField(distancia, { distancia = it }, label = { Text("Distancia (millas)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { val r = rumbo.toIntOrNull() ?: return@KeyboardActions; val d = distancia.toDoubleOrNull() ?: return@KeyboardActions; onAddVector(r, d) }))
            }
        },
        confirmButton = {
            Button(onClick = { val r = rumbo.toIntOrNull() ?: return@Button; val d = distancia.toDoubleOrNull() ?: return@Button; onAddVector(r, d) }) { Text("Validar") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVector(start: Offset, end: Offset) {
    val arrowPath = Path().apply {
        moveTo(start.x, start.y)
        lineTo(end.x, end.y)
        val angle = atan2(end.y - start.y, end.x - start.x)
        val arrowSize = 20f
        lineTo(
            end.x - arrowSize * cos(angle - PI.toFloat() / 6),
            end.y - arrowSize * sin(angle - PI.toFloat() / 6)
        )
        moveTo(end.x, end.y)
        lineTo(
            end.x - arrowSize * cos(angle + PI.toFloat() / 6),
            end.y - arrowSize * sin(angle + PI.toFloat() / 6)
        )
    }
    drawPath(arrowPath, Color.Blue, style = Stroke(width = 3f, cap = StrokeCap.Round))
}

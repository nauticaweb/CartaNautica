package com.triskel.cartanautica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MiniCartaVectorial()
                }
            }
        }
    }
}

data class Vector(
    val start: Offset,
    val end: Offset
)

@Composable
fun MiniCartaVectorial() {

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var imageSize by remember { mutableStateOf(Offset.Zero) }

    var vectors by remember { mutableStateOf(listOf<Vector>()) }

    var vectorStart by remember { mutableStateOf<Offset?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    // 🔑 conversión CORRECTA de pantalla → imagen
                    val imageX = (tap.x - offset.x) / scale
                    val imageY = (tap.y - offset.y) / scale

                    vectorStart = Offset(imageX, imageY)
                    showDialog = true
                }
            }
    ) {

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {

            Image(
                painter = painterResource(id = R.drawable.carta_estrecho),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .onGloballyPositioned {
                        imageSize = Offset(
                            it.size.width.toFloat(),
                            it.size.height.toFloat()
                        )
                    }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                vectors.forEach { v ->
                    drawVector(v.start, v.end)
                }
            }
        }
    }

    if (showDialog && vectorStart != null) {
        AddVectorDialog(
            onDismiss = {
                showDialog = false
                vectorStart = null
            },
            onAddVector = { rumbo, distancia ->
                val start = vectorStart!!

                val rad = Math.toRadians(rumbo.toDouble())
                val lengthPx = distancia.toFloat() * 10f

                val end = Offset(
                    x = start.x + cos(rad).toFloat() * lengthPx,
                    y = start.y + sin(rad).toFloat() * lengthPx
                )

                vectors = vectors + Vector(start, end)

                showDialog = false
                vectorStart = null
            }
        )
    }
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
            Column {
                TextField(
                    value = rumbo,
                    onValueChange = { rumbo = it },
                    label = { Text("Rumbo (°)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = distancia,
                    onValueChange = { distancia = it },
                    label = { Text("Distancia") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val r = rumbo.toIntOrNull() ?: return@Button
                val d = distancia.toDoubleOrNull() ?: return@Button
                onAddVector(r, d)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVector(
    start: Offset,
    end: Offset
) {
    val arrowSize = 20f
    val angle = atan2(end.y - start.y, end.x - start.x)

    val path = Path().apply {
        moveTo(start.x, start.y)
        lineTo(end.x, end.y)

        lineTo(
            end.x - arrowSize * cos(angle - PI / 6).toFloat(),
            end.y - arrowSize * sin(angle - PI / 6).toFloat()
        )

        moveTo(end.x, end.y)
        lineTo(
            end.x - arrowSize * cos(angle + PI / 6).toFloat(),
            end.y - arrowSize * sin(angle + PI / 6).toFloat()
        )
    }

    drawPath(
        path = path,
        color = Color.Cyan,
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}

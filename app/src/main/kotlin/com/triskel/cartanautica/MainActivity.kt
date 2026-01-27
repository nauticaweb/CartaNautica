package com.triskel.cartanautica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

const val LAT_MAX = 36.3333
const val LAT_MIN = 35.6667
const val LON_MIN = 5.1667
const val LON_MAX = 6.3333

data class Vector(
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(Modifier.fillMaxSize()) {
                CartaNauticaScreen()
            }
        }
    }
}

/* ================= UTILIDADES ================= */
fun latLonToPixel(lat: Double, lon: Double, w: Float, h: Float): Offset {
    val xNorm = (lon - LON_MIN) / (LON_MAX - LON_MIN)
    val yNorm = (LAT_MAX - lat) / (LAT_MAX - LAT_MIN)
    return androidx.compose.ui.geometry.Offset((xNorm * w).toFloat(), (yNorm * h).toFloat())
}

fun pixelToLatLon(x: Float, y: Float, w: Float, h: Float): Pair<Double, Double> {
    val lon = LON_MIN + x / w * (LON_MAX - LON_MIN)
    val lat = LAT_MAX - y / h * (LAT_MAX - LAT_MIN)
    return lat to lon
}

/* ================= UI ================= */
@Composable
fun CartaNauticaScreen() {
    var vectors by remember { mutableStateOf(listOf<Vector>()) }
    var imageWidth by remember { mutableStateOf(0f) }
    var imageHeight by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    if (imageWidth == 0f || imageHeight == 0f) return@detectTapGestures
                    val x = (tap.x - offsetX) / scale
                    val y = (tap.y - offsetY) / scale
                    val (startLat, startLon) = pixelToLatLon(x, y, imageWidth, imageHeight)
                    val distancia = 7.0
                    val rad = Math.toRadians(0.0)
                    val deltaLat = distancia / 60.0
                    val endLat = startLat + deltaLat * cos(rad)
                    val endLon = startLon + deltaLat * sin(rad) / cos(Math.toRadians(startLat))
                    vectors = vectors + Vector(startLat, startLon, endLat, endLon)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
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
            Canvas(Modifier.fillMaxSize()) {
                vectors.forEach { vector ->
                    val start = latLonToPixel(vector.startLat, vector.startLon, imageWidth, imageHeight)
                    val end = latLonToPixel(vector.endLat, vector.endLon, imageWidth, imageHeight)
                    drawVector(
                        androidx.compose.ui.geometry.Offset(start.x, start.y),
                        androidx.compose.ui.geometry.Offset(end.x, end.y)
                    )
                }
            }
        }
    }
}

/* ================= DIBUJO DEL VECTOR ================= */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVector(start: Offset, end: Offset) {
    val arrowPath = Path().apply {
        moveTo(start.x, start.y)
        lineTo(end.x, end.y)
        val angle = atan2(end.y - start.y, end.x - start.x)
        val arrowSize = 20f
        lineTo(
            (end.x - arrowSize * cos(angle - PI.toFloat() / 6)),
            (end.y - arrowSize * sin(angle - PI.toFloat() / 6))
        )
        moveTo(end.x, end.y)
        lineTo(
            (end.x - arrowSize * cos(angle + PI.toFloat() / 6)),
            (end.y - arrowSize * sin(angle + PI.toFloat() / 6))
        )
    }
    drawPath(arrowPath, Color.Blue, style = Stroke(width = 3f, cap = StrokeCap.Round))
}
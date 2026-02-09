package com.triskel.cartanautica

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import kotlin.math.*

class CartaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------- Carta ----------
    private var cartaBitmap: Bitmap? = null

    // ---------- Coordenadas reales ----------
    private val LAT_MAX = 36.3333
    private val LAT_MIN = 35.6667
    private val LON_MIN = 5.1667
    private val LON_MAX = 6.3333

    // ---------- Transformación ----------
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // ---------- Touch ----------
    private var lastX = 0f
    private var lastY = 0f

    // ---------- Drag ----------
    private var dragStartCartaX = 0f
    private var dragStartCartaY = 0f

    // ---------- Zoom ----------
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor *= detector.scaleFactor

                cartaBitmap?.let {
                    val minScaleX = width.toFloat() / it.width
                    val minScaleY = height.toFloat() / it.height
                    val minScale = min(minScaleX, minScaleY)
                    scaleFactor = scaleFactor.coerceIn(minScale, 5f)
                }

                offsetX =
                    (offsetX - detector.focusX) * (scaleFactor / prevScale) + detector.focusX
                offsetY =
                    (offsetY - detector.focusY) * (scaleFactor / prevScale) + detector.focusY

                invalidateMatrix()
                return true
            }
        }
    )

    // ---------- Elementos ----------
    data class VectorNautico(val start: PointF, val end: PointF)
    data class CirculoNautico(var center: PointF, val radiusPx: Float)
    data class Punto(var position: PointF)

    private val vectors = mutableListOf<VectorNautico>()
    private val circles = mutableListOf<CirculoNautico>()
    private val puntos = mutableListOf<Punto>()

    private var selectedVector: VectorNautico? = null
    private var selectedCircle: CirculoNautico? = null
    private var selectedPunto: Punto? = null

    // ---------- Paint ----------
    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 7f
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val selectedCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val puntoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val selectedPuntoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    // ---------- Estados ----------
    private var pendingVector = false
    private var rumboDeg = 0f
    private var distanciaMillas = 0f

    private var pendingCircle = false
    private var circleDistanceMiles = 0f

    private var modoVectorLibre = false
    private var vectorLibreInicio: PointF? = null
    private var vectorLibrePreview: VectorNautico? = null

    // ---------- Gestos ----------
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                manejarPulsacionLarga(e.x, e.y)
            }
        }
    )

    init {
        post {
            cartaBitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.carta_estrecho,
                BitmapFactory.Options().apply { inScaled = false }
            )
            centerCarta()
        }
    }

    // ---------- Draw ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.concat(matrix)

        cartaBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        circles.forEach {
            val p = if (it == selectedCircle) selectedCirclePaint else circlePaint
            canvas.drawCircle(it.center.x, it.center.y, it.radiusPx, p)
        }

        vectors.forEach {
            val p = if (it == selectedVector) selectedPaint else vectorPaint
            canvas.drawLine(it.start.x, it.start.y, it.end.x, it.end.y, p)
            drawArrow(canvas, it.start, it.end, p)
        }

        vectorLibrePreview?.let {
            canvas.drawLine(it.start.x, it.start.y, it.end.x, it.end.y, vectorPaint)
            drawArrow(canvas, it.start, it.end, vectorPaint)
        }

        puntos.forEach { p ->
            val paint = if (p == selectedPunto) selectedPuntoPaint else puntoPaint
            canvas.drawCircle(p.position.x, p.position.y, 10f, paint)
        }

        canvas.restore()
    }

    // ---------- Touch ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y

                if (modoVectorLibre) {
                    vectorLibreInicio = screenToCarta(event.x, event.y)
                    vectorLibrePreview = null
                    invalidate()
                    return true
                }

                selectedVector = null
                selectedCircle = null
                selectedPunto = null

                selectedCircle = findCircleAt(event.x, event.y)
                selectedVector =
                    if (selectedCircle == null) findVectorAt(event.x, event.y) else null
                if (selectedVector == null && selectedCircle == null) {
                    selectedPunto = puntos.lastOrNull {
                        val t = screenToCarta(event.x, event.y)
                        hypot(t.x - it.position.x, t.y - it.position.y) <= 30f
                    }
                }

                val pt = screenToCarta(event.x, event.y)
                dragStartCartaX = pt.x
                dragStartCartaY = pt.y
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {

                if (modoVectorLibre && vectorLibreInicio != null) {
                    val end = screenToCarta(event.x, event.y)
                    vectorLibrePreview = VectorNautico(vectorLibreInicio!!, end)
                    invalidate()
                    return true
                }

                if (selectedVector != null || selectedCircle != null || selectedPunto != null) {
                    val pt = screenToCarta(event.x, event.y)
                    val dx = pt.x - dragStartCartaX
                    val dy = pt.y - dragStartCartaY

                    selectedVector?.apply {
                        start.offset(dx, dy)
                        end.offset(dx, dy)
                    }

                    selectedCircle?.apply {
                        center.offset(dx, dy)
                    }

                    selectedPunto?.apply {
                        position.offset(dx, dy)
                    }

                    dragStartCartaX = pt.x
                    dragStartCartaY = pt.y
                    invalidate()
                } else if (!scaleDetector.isInProgress) {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY
                    invalidateMatrix()
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {

                if (modoVectorLibre && vectorLibrePreview != null) {
                    vectors.add(vectorLibrePreview!!)
                    vectorLibrePreview = null
                    vectorLibreInicio = null
                    modoVectorLibre = false
                    invalidate()
                    return true
                }

                if (pendingVector) {
                    crearVector(event.x, event.y)
                    pendingVector = false
                }

                if (pendingCircle) {
                    crearCirculo(event.x, event.y)
                    pendingCircle = false
                }
            }
        }
        return true
    }

    // ---------- API ----------
    fun prepararVector(rumbo: Float, distancia: Float) {
        rumboDeg = rumbo
        distanciaMillas = distancia
        pendingVector = true
    }

    fun prepararCirculo(distancia: Float) {
        circleDistanceMiles = distancia
        pendingCircle = true
    }

    fun activarVectorLibre() {
        modoVectorLibre = true
        vectorLibreInicio = null
        vectorLibrePreview = null
        selectedVector = null
        selectedCircle = null
        selectedPunto = null
        invalidate()
    }

    fun borrarElementoSeleccionado() {
        selectedPunto?.let { puntos.remove(it) }
        selectedVector?.let { vectors.remove(it) }
        selectedCircle?.let { circles.remove(it) }
        selectedPunto = null
        selectedVector = null
        selectedCircle = null
        invalidate()
    }

    fun crearPunto(lat: Double, lon: Double) {
        cartaBitmap?.let {
            val x = ((LON_MAX - lon) / (LON_MAX - LON_MIN) * it.width).toFloat()
            val y = ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN) * it.height).toFloat()
            puntos.add(Punto(PointF(x, y)))
            invalidate()
        }
    }

    // ---------- Pulsación larga ----------
    private fun manejarPulsacionLarga(x: Float, y: Float) {
        val v = findVectorAt(x, y)

        if (v != null) {
            mostrarInfoVector(v)
        } else {
            val p = screenToCarta(x, y)
            val (lat, lon) = cartaToLatLon(p)
            mostrarInfoPosicion(lat, lon)
        }
    }

    // ---------- Conversión ----------
    private fun cartaToLatLon(p: PointF): Pair<Double, Double> {
        val w = cartaBitmap!!.width.toDouble()
        val h = cartaBitmap!!.height.toDouble()
        val lon = LON_MAX - (p.x / w) * (LON_MAX - LON_MIN)
        val lat = LAT_MAX - (p.y / h) * (LAT_MAX - LAT_MIN)
        return lat to lon
    }

    private fun formatoGradosMinutos(v: Double, lat: Boolean): String {
        val abs = abs(v)
        val g = abs.toInt()
        val m = (abs - g) * 60.0
        val h = when {
            lat && v >= 0 -> "N"
            lat -> "S"
            !lat && v >= 0 -> "W"
            else -> "E"
        }
        return "%d° %.1f' %s".format(g, m, h)
    }

    private fun distanciaMillas(a: PointF, b: PointF): Double {
        val (lat1, lon1) = cartaToLatLon(a)
        val (lat2, lon2) = cartaToLatLon(b)
        val dLat = (lat2 - lat1) * 60.0
        val latMedia = Math.toRadians((lat1 + lat2) / 2.0)
        val dLon = (lon2 - lon1) * 60.0 * cos(latMedia)
        return sqrt(dLat * dLat + dLon * dLon)
    }

    private fun rumbo(a: PointF, b: PointF): Double {
        val (lat1, lon1) = cartaToLatLon(a)
        val (lat2, lon2) = cartaToLatLon(b)
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon1 - lon2)
        val y = sin(Δλ) * cos(φ2)
        val x =
            cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    // ---------- Diálogos ----------
    private fun mostrarInfoPosicion(lat: Double, lon: Double) {
        AlertDialog.Builder(context)
            .setTitle("Posición")
            .setMessage(
                "Latitud: ${formatoGradosMinutos(lat, true)}\n" +
                        "Longitud: ${formatoGradosMinutos(lon, false)}"
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun mostrarInfoVector(v: VectorNautico) {
        val (lat1, lon1) = cartaToLatLon(v.start)
        val (lat2, lon2) = cartaToLatLon(v.end)
        AlertDialog.Builder(context)
            .setTitle("Vector")
            .setMessage(
                "Inicio:\n${formatoGradosMinutos(lat1, true)}\n${formatoGradosMinutos(lon1, false)}\n\n" +
                        "Final:\n${formatoGradosMinutos(lat2, true)}\n${formatoGradosMinutos(lon2, false)}\n\n" +
                        "Rumbo: %.1f°\nDistancia: %.2f millas".format(
                            rumbo(v.start, v.end),
                            distanciaMillas(v.start, v.end)
                        )
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    // ---------- Utilidades ----------
    private fun crearVector(x: Float, y: Float) {
        val start = screenToCarta(x, y)
        val ang = Math.toRadians(rumboDeg.toDouble() - 90)
        val dir =
            PointF(start.x + cos(ang).toFloat(), start.y + sin(ang).toFloat())
        val factor = distanciaMillas / distanciaMillas(start, dir)
        val end = PointF(
            start.x + (dir.x - start.x) * factor.toFloat(),
            start.y + (dir.y - start.y) * factor.toFloat()
        )
        vectors.add(VectorNautico(start, end))
        invalidate()
    }

    private fun crearCirculo(x: Float, y: Float) {
        val center = screenToCarta(x, y)
        val ref = PointF(center.x + 100f, center.y)
        val millas100px = distanciaMillas(center, ref)
        val radiusPx =
            (circleDistanceMiles / millas100px * 100).toFloat()
        circles.add(CirculoNautico(center, radiusPx))
        invalidate()
    }

    private fun screenToCarta(x: Float, y: Float): PointF {
        inverseMatrix.reset()
        matrix.invert(inverseMatrix)
        val pt = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pt)
        return PointF(pt[0], pt[1])
    }

    private fun findVectorAt(x: Float, y: Float): VectorNautico? {
        val t = screenToCarta(x, y)
        return vectors.lastOrNull {
            distancePointToSegment(
                t.x,
                t.y,
                it.start,
                it.end
            ) <= 30f
        }
    }

    private fun findCircleAt(x: Float, y: Float): CirculoNautico? {
        val p = screenToCarta(x, y)
        return circles.lastOrNull {
            abs(hypot(p.x - it.center.x, p.y - it.center.y) - it.radiusPx) <= 25f
        }
    }

    private fun distancePointToSegment(
        px: Float,
        py: Float,
        a: PointF,
        b: PointF
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - a.x, py - a.y)
        var t = ((px - a.x) * dx + (py - a.y) * dy) / len2
        t = t.coerceIn(0f, 1f)
        val x = a.x + t * dx
        val y = a.y + t * dy
        return hypot(px - x, py - y)
    }

    private fun drawArrow(c: Canvas, s: PointF, e: PointF, p: Paint) {
        val ang = atan2(e.y - s.y, e.x - s.x)
        val l = 25f
        val a = Math.toRadians(25.0)
        c.drawLine(
            e.x,
            e.y,
            e.x - l * cos(ang - a).toFloat(),
            e.y - l * sin(ang - a).toFloat(),
            p
        )
        c.drawLine(
            e.x,
            e.y,
            e.x - l * cos(ang + a).toFloat(),
            e.y - l * sin(ang + a).toFloat(),
            p
        )
    }

    private fun invalidateMatrix() {
        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(offsetX, offsetY)
        invalidate()
    }

    private fun centerCarta() {
        cartaBitmap?.let {
            scaleFactor =
                min(width / it.width.toFloat(), height / it.height.toFloat())
            offsetX = (width - it.width * scaleFactor) / 2
            offsetY = (height - it.height * scaleFactor) / 2
            invalidateMatrix()
        }
    }
}

package com.triskel.cartanautica

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.graphics.pdf.PdfDocument
import android.view.*
import kotlin.math.*

class CartaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------- Carta ----------
    private var cartaBitmap: Bitmap? = null

    // ---------- Coordenadas reales ----------
    private val LAT_MAX = 36.33663333333333
    private val LAT_MIN = 35.66336666666667
    private val LON_MAX = 6.336633333333333
    private val LON_MIN = 5.163366666666667

    private val LAT_REF = (LAT_MAX + LAT_MIN) / 2.0
    private val COS_LAT_REF = cos(Math.toRadians(LAT_REF))

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
    enum class TipoVector { RUMBO, DEMORA, LIBRE }

    data class VectorNautico(val start: PointF, val end: PointF, val tipo: TipoVector)
    data class CirculoNautico(var center: PointF, val radiusPx: Float)
    data class Punto(var position: PointF)

    private val vectors = mutableListOf<VectorNautico>()
    private val circles = mutableListOf<CirculoNautico>()
    private val puntos = mutableListOf<Punto>()

    private var selectedVector: VectorNautico? = null
    private var selectedCircle: CirculoNautico? = null
    private var selectedPunto: Punto? = null

    // ---------- Paint ----------
    private val rumboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 5f
    }
    private val demoraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA500") // Naranja
        strokeWidth = 5f
    }
    private val librePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(165, 42, 42)  // Marrón
        strokeWidth = 5f
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 7f
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
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
            val paint = if (it == selectedVector) selectedPaint else when(it.tipo) {
                TipoVector.RUMBO -> rumboPaint
                TipoVector.DEMORA -> demoraPaint
                TipoVector.LIBRE -> librePaint
            }
            canvas.drawLine(it.start.x, it.start.y, it.end.x, it.end.y, paint)
            drawArrow(canvas, it.start, it.end, paint)
        }

        vectorLibrePreview?.let {
            val paint = librePaint
            canvas.drawLine(it.start.x, it.start.y, it.end.x, it.end.y, paint)
            drawArrow(canvas, it.start, it.end, paint)
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
                    vectorLibrePreview = VectorNautico(vectorLibreInicio!!, end, TipoVector.LIBRE)
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

            else -> {}
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
        cartaBitmap?.let { bmp ->
            val latMinTotal = (LAT_MAX - LAT_MIN) * 60.0
            val lonMinTotal = (LON_MAX - LON_MIN) * 60.0 * COS_LAT_REF

            val yMillas = (LAT_MAX - lat) * 60.0
            val xMillas = (LON_MAX - lon) * 60.0 * COS_LAT_REF

            val x = (xMillas / lonMinTotal * bmp.width).toFloat()
            val y = (yMillas / latMinTotal * bmp.height).toFloat()

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
        val bmp = cartaBitmap!!

        val latMinTotal = (LAT_MAX - LAT_MIN) * 60.0
        val lonMinTotal = (LON_MAX - LON_MIN) * 60.0 * COS_LAT_REF

        val yMillas = p.y / bmp.height * latMinTotal
        val xMillas = p.x / bmp.width * lonMinTotal

        val lat = LAT_MAX - yMillas / 60.0
        val lon = LON_MAX - (xMillas / COS_LAT_REF) / 60.0

        return lat to lon
    }

    private fun formatoGradosMinutos(v: Double, lat: Boolean): String {
        var absV = abs(v)
        var g = absV.toInt()
        var m = (absV - g) * 60.0

        if (m >= 59.9999995) {
            m = 0.0
            g += 1
        }

        val h = if (lat) {
            if (v >= 0) "N" else "S"
        } else {
            if (v >= 0) "O" else "E"
        }

        return "%d° %.1f' %s".format(g, m, h)
    }

    private fun distanciaMillas(a: PointF, b: PointF): Double {
        val (lat1, lon1) = cartaToLatLon(a)
        val (lat2, lon2) = cartaToLatLon(b)
        val dLat = (lat2 - lat1) * 60.0
        val dLon = (lon2 - lon1) * 60.0 * cos(Math.toRadians((lat1 + lat2) / 2.0))
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
        vectors.add(VectorNautico(start, end, TipoVector.RUMBO))
        invalidate()
    }

    private fun crearCirculo(x: Float, y: Float) {
        val center = screenToCarta(x, y)
        val ref = PointF(center.x + 100f, center.y)
        val millas100px = distanciaMillas(center, ref)
        val radiusPx = (circleDistanceMiles / millas100px * 100).toFloat()
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

    fun imprimirCartaPdf(nombreArchivo: String = "CartaNautica.pdf") {

        val pdfDocument = PdfDocument()

        // --- A3 APaisado ---
        val pageWidth = 1191   // A3 horizontal
        val pageHeight = 842

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        cartaBitmap?.let { bmp ->

            // Escalado proporcional máximo
            val scale = min(
                pageWidth.toFloat() / bmp.width,
                pageHeight.toFloat() / bmp.height
            )

            val offsetX = (pageWidth - bmp.width * scale) / 2f
            val offsetY = (pageHeight - bmp.height * scale) / 2f

            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)

            // --- DIBUJAR CARTA ---
            canvas.drawBitmap(bmp, 0f, 0f, null)

            // --- CÍRCULOS ---
            circles.forEach {
                val paint = if (it == selectedCircle) selectedCirclePaint else circlePaint
                canvas.drawCircle(it.center.x, it.center.y, it.radiusPx, paint)
            }

            // --- VECTORES ---
            vectors.forEach {
                val paint = if (it == selectedVector) selectedPaint else when (it.tipo) {
                    TipoVector.RUMBO -> rumboPaint
                    TipoVector.DEMORA -> demoraPaint
                    TipoVector.LIBRE -> librePaint
                }

                canvas.drawLine(it.start.x, it.start.y, it.end.x, it.end.y, paint)
                drawArrow(canvas, it.start, it.end, paint)
            }

            // --- PUNTOS ---
            puntos.forEach { pnt ->
                val paint = if (pnt == selectedPunto) selectedPuntoPaint else puntoPaint
                canvas.drawCircle(pnt.position.x, pnt.position.y, 10f, paint)
            }

            canvas.restore()
        }

        pdfDocument.finishPage(page)

        try {
            val file = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                .resolve(nombreArchivo)

            pdfDocument.writeTo(file.outputStream())
            pdfDocument.close()

            android.widget.Toast.makeText(
                context,
                "PDF generado: ${file.absolutePath}",
                android.widget.Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                "Error al generar PDF",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

}

package com.triskel.cartanautica

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
                val prev = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 5f)
                val factor = scaleFactor / prev
                offsetX = detector.focusX - factor * (detector.focusX - offsetX)
                offsetY = detector.focusY - factor * (detector.focusY - offsetY)
                invalidateMatrix()
                return true
            }
        }
    )

    // ---------- Escala ----------
    private val pixelsPerMile = 120f

    // ---------- Elementos ----------
    data class VectorNautico(val start: PointF, val end: PointF)
    data class CirculoNautico(var center: PointF, val radiusPx: Float)

    private val vectors = mutableListOf<VectorNautico>()
    private val circles = mutableListOf<CirculoNautico>()

    private var selectedVector: VectorNautico? = null
    private var selectedCircle: CirculoNautico? = null

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

    // ---------- Estados ----------
    private var pendingVector = false
    private var rumboDeg = 0f
    private var distanciaMillas = 0f

    private var pendingCircle = false
    private var circleDistanceMiles = 0f

    private var modoVectorLibre = false
    private var vectorLibreInicio: PointF? = null
    private var vectorLibrePreview: VectorNautico? = null

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

        canvas.restore()
    }

    // ---------- Touch ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

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

                selectedCircle = findCircleAt(event.x, event.y)
                selectedVector = if (selectedCircle == null) findVectorAt(event.x, event.y) else null

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

                if (selectedVector != null || selectedCircle != null) {
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
        invalidate()
    }

    fun borrarElementoSeleccionado() {
        selectedVector?.let { vectors.remove(it) }
        selectedCircle?.let { circles.remove(it) }
        selectedVector = null
        selectedCircle = null
        invalidate()
    }

    // ---------- Lógica ----------
    private fun crearVector(x: Float, y: Float) {
        val start = screenToCarta(x, y)
        val angle = Math.toRadians(rumboDeg.toDouble() - 90)
        val len = distanciaMillas * pixelsPerMile
        val end = PointF(
            start.x + len * cos(angle).toFloat(),
            start.y + len * sin(angle).toFloat()
        )
        vectors.add(VectorNautico(start, end))
        invalidate()
    }

    private fun crearCirculo(x: Float, y: Float) {
        val center = screenToCarta(x, y)
        val radius = circleDistanceMiles * pixelsPerMile
        circles.add(CirculoNautico(center, radius))
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
            distancePointToSegment(t.x, t.y, it.start, it.end) <= 30f
        }
    }

    private fun findCircleAt(x: Float, y: Float): CirculoNautico? {
        val p = screenToCarta(x, y)
        return circles.lastOrNull {
            abs(hypot(p.x - it.center.x, p.y - it.center.y) - it.radiusPx) <= 25f
        }
    }

    private fun distancePointToSegment(px: Float, py: Float, a: PointF, b: PointF): Float {
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
        c.drawLine(e.x, e.y, e.x - l * cos(ang - a).toFloat(), e.y - l * sin(ang - a).toFloat(), p)
        c.drawLine(e.x, e.y, e.x - l * cos(ang + a).toFloat(), e.y - l * sin(ang + a).toFloat(), p)
    }

    private fun invalidateMatrix() {
        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(offsetX, offsetY)
        invalidate()
    }

    private fun centerCarta() {
        cartaBitmap?.let {
            scaleFactor = min(width / it.width.toFloat(), height / it.height.toFloat())
            offsetX = (width - it.width * scaleFactor) / 2
            offsetY = (height - it.height * scaleFactor) / 2
            invalidateMatrix()
        }
    }
}

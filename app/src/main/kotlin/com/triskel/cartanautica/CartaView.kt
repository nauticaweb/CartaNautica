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

    // ---------- Control táctil ----------
    private var lastX = 0f
    private var lastY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ---------- Drag preciso en carta ----------
    private var dragStartCartaX = 0f
    private var dragStartCartaY = 0f

    // ---------- Zoom centrado ----------
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5f)

                val scaleChange = scaleFactor / prevScale
                offsetX = detector.focusX - scaleChange * (detector.focusX - offsetX)
                offsetY = detector.focusY - scaleChange * (detector.focusY - offsetY)

                invalidateMatrix()
                return true
            }
        }
    )

    // ---------- Vectores ----------
    data class VectorNautico(
        val start: PointF,
        val end: PointF
    )

    private val vectors = mutableListOf<VectorNautico>()
    private var selectedVector: VectorNautico? = null

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 7f
        style = Paint.Style.STROKE
    }

    // ---------- Creación controlada ----------
    private var pendingVector = false
    private var rumboDeg = 0f
    private var distanciaMillas = 0f

    // ---------- Escala náutica ----------
    private val pixelsPerMile = 120f

    init {
        post {
            val options = BitmapFactory.Options().apply { inScaled = false }
            cartaBitmap = BitmapFactory.decodeResource(
                resources,
                R.drawable.carta_estrecho,
                options
            )
            centerCarta()
        }
    }

    // ---------- Dibujo ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.concat(matrix)

        cartaBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        for (v in vectors) {
            val paint = if (v == selectedVector) selectedPaint else vectorPaint
            canvas.drawLine(v.start.x, v.start.y, v.end.x, v.end.y, paint)
            drawArrow(canvas, v.start, v.end, paint)
        }

        canvas.restore()
    }

    // ---------- Eventos ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y

                val touchedVector = findVectorAt(event.x, event.y)
                selectedVector = touchedVector

                if (touchedVector != null) {
                    inverseMatrix.reset()
                    matrix.invert(inverseMatrix)
                    val pt = floatArrayOf(event.x, event.y)
                    inverseMatrix.mapPoints(pt)
                    dragStartCartaX = pt[0]
                    dragStartCartaY = pt[1]
                }

                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (selectedVector != null) {
                    inverseMatrix.reset()
                    matrix.invert(inverseMatrix)

                    val pt = floatArrayOf(event.x, event.y)
                    inverseMatrix.mapPoints(pt)

                    val dx = pt[0] - dragStartCartaX
                    val dy = pt[1] - dragStartCartaY

                    selectedVector!!.start.offset(dx, dy)
                    selectedVector!!.end.offset(dx, dy)

                    dragStartCartaX = pt[0]
                    dragStartCartaY = pt[1]

                    invalidate()
                } else if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        offsetX += dx
                        offsetY += dy
                        invalidateMatrix()
                    }

                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                if (pendingVector) {
                    crearVector(event.x, event.y)
                    pendingVector = false
                }
            }
        }
        return true
    }

    // ---------- API pública ----------
    fun prepararVector(rumbo: Float, distancia: Float) {
        rumboDeg = rumbo
        distanciaMillas = distancia
        pendingVector = true
    }

    fun borrarVectorSeleccionado() {
        selectedVector?.let {
            vectors.remove(it)
            selectedVector = null
            invalidate()
        }
    }

    // ---------- Lógica ----------
    private fun crearVector(x: Float, y: Float) {
        inverseMatrix.reset()
        matrix.invert(inverseMatrix)

        val pt = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pt)

        val start = PointF(pt[0], pt[1])
        val angleRad = Math.toRadians(rumboDeg.toDouble() - 90)
        val lengthPx = distanciaMillas * pixelsPerMile

        val end = PointF(
            start.x + lengthPx * cos(angleRad).toFloat(),
            start.y + lengthPx * sin(angleRad).toFloat()
        )

        vectors.add(VectorNautico(start, end))
        invalidate()
    }

    private fun findVectorAt(x: Float, y: Float): VectorNautico? {
        inverseMatrix.reset()
        matrix.invert(inverseMatrix)

        val pt = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pt)
        val touch = PointF(pt[0], pt[1])

        val tolerance = 30f

        return vectors.lastOrNull { v ->
            distancePointToSegment(touch.x, touch.y, v.start, v.end) <= tolerance
        }
    }

    private fun distancePointToSegment(px: Float, py: Float, a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - a.x, py - a.y)

        var t = ((px - a.x) * dx + (py - a.y) * dy) / len2
        t = t.coerceIn(0f, 1f)

        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(px - projX, py - projY)
    }

    private fun drawArrow(canvas: Canvas, start: PointF, end: PointF, paint: Paint) {
        val angle = atan2(end.y - start.y, end.x - start.x)
        val arrowLength = 25f
        val arrowAngle = Math.toRadians(25.0)

        val x1 = end.x - arrowLength * cos(angle - arrowAngle).toFloat()
        val y1 = end.y - arrowLength * sin(angle - arrowAngle).toFloat()
        val x2 = end.x - arrowLength * cos(angle + arrowAngle).toFloat()
        val y2 = end.y - arrowLength * sin(angle + arrowAngle).toFloat()

        canvas.drawLine(end.x, end.y, x1, y1, paint)
        canvas.drawLine(end.x, end.y, x2, y2, paint)
    }

    private fun invalidateMatrix() {
        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(offsetX, offsetY)
        invalidate()
    }

    private fun centerCarta() {
        cartaBitmap?.let {
            scaleFactor = min(
                width.toFloat() / it.width,
                height.toFloat() / it.height
            )
            offsetX = (width - it.width * scaleFactor) / 2f
            offsetY = (height - it.height * scaleFactor) / 2f
            invalidateMatrix()
        }
    }
}

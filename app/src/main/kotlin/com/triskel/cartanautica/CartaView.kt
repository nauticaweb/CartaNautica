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
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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
    private val vectors = mutableListOf<Pair<PointF, PointF>>()

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // ---------- Control de creación ----------
    private var pendingVector = false
    private var rumboDeg = 0f
    private var distanciaMillas = 0f

    // ---------- Escala náutica ----------
    // AJUSTABLE: calibrar con la carta real
    private val pixelsPerMile = 80f   // ↓ reducido (antes 120)

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

        for ((start, end) in vectors) {
            drawArrow(canvas, start, end)
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
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        offsetX += dx
                        offsetY += dy
                        isDragging = true
                        invalidateMatrix()
                    }

                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging && pendingVector) {
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

        vectors.add(start to end)
        invalidate()
    }

    // ---------- Flecha ----------
    private fun drawArrow(canvas: Canvas, start: PointF, end: PointF) {
        canvas.drawLine(start.x, start.y, end.x, end.y, vectorPaint)

        val arrowLength = 25f
        val arrowAngle = Math.toRadians(25.0)

        val angle = atan2(end.y - start.y, end.x - start.x)

        val x1 = end.x - arrowLength * cos(angle - arrowAngle).toFloat()
        val y1 = end.y - arrowLength * sin(angle - arrowAngle).toFloat()

        val x2 = end.x - arrowLength * cos(angle + arrowAngle).toFloat()
        val y2 = end.y - arrowLength * sin(angle + arrowAngle).toFloat()

        canvas.drawLine(end.x, end.y, x1, y1, vectorPaint)
        canvas.drawLine(end.x, end.y, x2, y2, vectorPaint)
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

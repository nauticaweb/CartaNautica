package com.triskel.cartanautica

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CartaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------- Fondo ----------
    private var cartaBitmap: Bitmap? = null

    // ---------- Zoom y desplazamiento ----------
    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 5f)
            invalidateMatrix()
            return true
        }
    })

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // ---------- Vectores ----------
    private val vectors = mutableListOf<Pair<PointF, PointF>>()
    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // Datos introducidos por el usuario
    private var userRumbo: Float? = null // grados
    private var userDistancia: Float? = null // millas

    // Escala para convertir millas a píxeles (ajustable según la carta)
    private var pixelsPerMile = 10f

    // ---------- Inicialización ----------
    init {
        post {
            val options = BitmapFactory.Options().apply { inScaled = false }
            cartaBitmap = BitmapFactory.decodeResource(resources, R.drawable.carta_estrecho, options)
            fitCartaToView()
        }
    }

    private fun fitCartaToView() {
        cartaBitmap?.let {
            val scaleX = width.toFloat() / it.width
            val scaleY = height.toFloat() / it.height
            scaleFactor = minOf(scaleX, scaleY)
            offsetX = (width - it.width * scaleFactor) / 2f
            offsetY = (height - it.height * scaleFactor) / 2f
            invalidateMatrix()
        }
    }

    // ---------- Dibujado ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.concat(transformMatrix)

        cartaBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, 0f, 0f, null)
        }

        for ((start, end) in vectors) {
            canvas.drawLine(start.x, start.y, end.x, end.y, vectorPaint)
            drawArrow(canvas, start, end)
        }

        canvas.restore()
    }

    private fun drawArrow(canvas: Canvas, start: PointF, end: PointF) {
        val arrowSize = 20f
        val angle = kotlin.math.atan2((end.y - start.y), (end.x - start.x))
        val sin = sin(angle)
        val cos = cos(angle)
        val p1 = PointF(
            end.x - arrowSize * cos + arrowSize/2 * sin,
            end.y - arrowSize * sin - arrowSize/2 * cos
        )
        val p2 = PointF(
            end.x - arrowSize * cos - arrowSize/2 * sin,
            end.y - arrowSize * sin + arrowSize/2 * cos
        )
        canvas.drawLine(end.x, end.y, p1.x, p1.y, vectorPaint)
        canvas.drawLine(end.x, end.y, p2.x, p2.y, vectorPaint)
    }

    // ---------- Eventos táctiles ----------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                downX = event.x
                downY = event.y
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    offsetX += dx
                    offsetY += dy
                    lastX = event.x
                    lastY = event.y
                    dragging = dragging || distance(downX, downY, lastX, lastY) > touchSlop
                    invalidateMatrix()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging && userRumbo != null && userDistancia != null) {
                    addVectorAtTouch(event.x, event.y)
                }
            }
        }

        return true
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx*dx + dy*dy)
    }

    private fun invalidateMatrix() {
        transformMatrix.reset()
        transformMatrix.postScale(scaleFactor, scaleFactor)
        transformMatrix.postTranslate(offsetX, offsetY)
        invalidate()
    }

    private fun addVectorAtTouch(x: Float, y: Float) {
        inverseMatrix.reset()
        transformMatrix.invert(inverseMatrix)
        val touchPoint = floatArrayOf(x, y)
        inverseMatrix.mapPoints(touchPoint)

        val startX = touchPoint[0]
        val startY = touchPoint[1]

        // Calculamos vector usando rumbo y distancia
        val angleRad = Math.toRadians(userRumbo!!.toDouble())
        val distancePx = userDistancia!! * pixelsPerMile

        val endX = startX + distancePx * sin(angleRad).toFloat()
        val endY = startY - distancePx * cos(angleRad).toFloat() // coordenadas Y invertidas
        vectors.add(PointF(startX, startY) to PointF(endX, endY))
        invalidate()
    }

    // ---------- Setters para datos ----------
    fun setRumboDistancia(rumbo: Float, distancia: Float) {
        userRumbo = rumbo
        userDistancia = distancia
    }
}

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
    private val vectorLength = 200f
    private val vectorAngleRad = Math.toRadians(45.0).toFloat()

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // ---------- Inicialización ----------
    init {
        // Cargar bitmap grande de forma escalable
        post {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.carta_estrecho, options)
            cartaBitmap = bmp
            invalidate()
        }
    }

    // ---------- Dibujado ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.concat(transformMatrix)

        cartaBitmap?.let {
            // Escalar al tamaño de la vista si es demasiado grande
            val scaled = Bitmap.createScaledBitmap(it, width, height, true)
            canvas.drawBitmap(scaled, 0f, 0f, null)
        }

        for ((start, end) in vectors) {
            canvas.drawLine(start.x, start.y, end.x, end.y, vectorPaint)
        }

        canvas.restore()
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
                if (!dragging) addVectorAtTouch(event.x, event.y)
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

        val endX = startX + vectorLength * cos(vectorAngleRad.toDouble()).toFloat()
        val endY = startY + vectorLength * sin(vectorAngleRad.toDouble()).toFloat()

        vectors.add(PointF(startX, startY) to PointF(endX, endY))
        invalidate()
    }
}

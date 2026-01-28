package com.triskel.cartanautica

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CartaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var cartaBitmap: Bitmap? = null

    private val matrix = Matrix()
    private val inverseMatrix = Matrix()  // ⚡ matriz inversa para los taps

    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val vectors = mutableListOf<Pair<PointF, PointF>>()
    private val vectorLength = 200f
    private val vectorAngleRad = Math.toRadians(45.0).toFloat()

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5f)
                invalidate()
                return true
            }
        }
    )

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        val options = BitmapFactory.Options().apply { inScaled = false }
        val original = BitmapFactory.decodeResource(resources, R.drawable.carta_estrecho, options)

        val scale = min(w.toFloat() / original.width, h.toFloat() / original.height)
        val scaledWidth = (original.width * scale).toInt()
        val scaledHeight = (original.height * scale).toInt()

        cartaBitmap = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)
        original.recycle()

        offsetX = (w - scaledWidth) / 2f
        offsetY = (h - scaledHeight) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = cartaBitmap ?: return

        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(offsetX, offsetY)

        inverseMatrix.reset()
        matrix.invert(inverseMatrix)  // ⚡ recalcular inversa cada draw

        canvas.save()
        canvas.concat(matrix)

        canvas.drawBitmap(bmp, 0f, 0f, null)

        for ((s, e) in vectors) {
            canvas.drawLine(s.x, s.y, e.x, e.y, vectorPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    dragging = true
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    addVector(event.x, event.y)
                }
            }
        }
        return true
    }

    private fun addVector(x: Float, y: Float) {
        val p = floatArrayOf(x, y)
        inverseMatrix.mapPoints(p)  // ⚡ transformar tap a coordenadas de bitmap

        val startX = p[0]
        val startY = p[1]
        val endX = startX + vectorLength * cos(vectorAngleRad)
        val endY = startY + vectorLength * sin(vectorAngleRad)

        vectors.add(PointF(startX, startY) to PointF(endX, endY))
        invalidate()
    }
}





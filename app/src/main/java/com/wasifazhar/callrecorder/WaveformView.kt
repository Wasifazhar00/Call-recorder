package com.wasifazhar.callrecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class WaveformView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; color = 0xff4169e1.toInt() }
    private val values = ArrayDeque<Float>()
    fun addLevel(level: Int) {
        values.addLast((level / 32767f).coerceIn(0.02f, 1f))
        while (values.size > 180) values.removeFirst()
        invalidate()
    }
    fun clear() { values.clear(); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = height / 2f
        val step = width.toFloat() / max(1, 180)
        values.forEachIndexed { index, value ->
            val x = index * step + step / 2f
            val half = value * (height * 0.43f)
            canvas.drawLine(x, center - half, x, center + half, paint)
        }
    }
}

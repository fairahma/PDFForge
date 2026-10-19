package com.pdfforge.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Consistent, density-independent outline icons for the home tool cards. */
class ToolIcon(context: Context, private val tool: String, private val tint: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / 32f, side / 32f)
        fun line(x: Float, y: Float, a: Float, b: Float) = canvas.drawLine(x,y,a,b,paint)
        fun rect(l: Float,t: Float,r: Float,b: Float) = canvas.drawRoundRect(l,t,r,b,2f,2f,paint)
        fun page(x: Float,y: Float) { rect(x,y,x+13f,y+18f); line(x+3,y+6,x+10,y+6); line(x+3,y+10,x+9,y+10) }
        when(tool) {
            "merge" -> { page(2f,7f); page(17f,7f); line(12f,3f,20f,3f); line(16f,0f,16f,6f) }
            "pages" -> {
                page(3f,7f)
                line(20f,7f,29f,7f); line(20f,15f,29f,15f); line(20f,23f,29f,23f)
                line(24.5f,4f,24.5f,26f)
            }
            "organize" -> {
                rect(3f,3f,29f,29f)
                line(9f,10f,23f,10f); line(9f,16f,23f,16f); line(9f,22f,23f,22f)
                canvas.drawCircle(6f,10f,0.7f,paint); canvas.drawCircle(6f,16f,0.7f,paint)
            }
            "compress" -> {
                line(4f,12f,12f,12f); line(12f,12f,12f,4f); line(20f,4f,20f,12f)
                line(20f,12f,28f,12f); line(4f,20f,12f,20f); line(12f,20f,12f,28f)
                line(20f,28f,20f,20f); line(20f,20f,28f,20f)
                line(12f,12f,15f,15f); line(20f,12f,17f,15f)
            }
            else -> {
                rect(3f,5f,29f,27f)
                canvas.drawCircle(11f,12f,2f,paint)
                val path = Path().apply { moveTo(5f,24f); lineTo(13f,16f); lineTo(18f,21f); lineTo(23f,14f); lineTo(28f,21f) }
                canvas.drawPath(path,paint)
            }
        }
        canvas.restore()
    }
}

package com.pdfforge.app

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * Illustrated, full-colour icons for PDFForge's six PDF tools.
 * Drawn as scalable Android graphics so they remain sharp at every density.
 */
class ToolIcon(context: Context, private val tool: String, private val tint: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun color(c: Long) = c.toInt()
    private fun fill(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, c: Int) {
        p.reset(); p.isAntiAlias = true; p.color = c; p.style = Paint.Style.FILL
        canvas.drawRoundRect(l,t,r,b,radius,radius,p)
    }
    private fun line(canvas: Canvas, x: Float,y: Float,xx: Float,yy: Float,c: Int,w: Float=2.5f) {
        p.reset();p.isAntiAlias=true;p.color=c;p.strokeWidth=w;p.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(x,y,xx,yy,p)
    }
    private fun document(c: Canvas,x: Float,y: Float,w: Float,h: Float, pdf: Boolean=true) {
        fill(c,x+1,y+2,x+w+1,y+h+2,4f,0x33000000)
        fill(c,x,y,x+w,y+h,4f,Color.WHITE)
        val fold=6f
        val path=Path().apply {moveTo(x+w-fold,y);lineTo(x+w,y+fold);lineTo(x+w-fold,y+fold);close()}
        p.color=0xFFB8D8F3.toInt();p.style=Paint.Style.FILL;c.drawPath(path,p)
        if(pdf) {
            fill(c,x+3,y+h*0.49f,x+w-3,y+h*0.76f,2f,0xFFF0444D.toInt())
            p.reset();p.isAntiAlias=true;p.color=Color.WHITE;p.typeface=Typeface.create("sans-serif-black",Typeface.BOLD);p.textSize=5.4f
            c.drawText("PDF",x+4,y+h*0.68f,p)
        } else {
            for(i in 0..2) line(c,x+4,y+9+i*5,x+w-5,y+9+i*5,0xFFB3C0D1.toInt(),1.6f)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side=minOf(width,height).toFloat()
        canvas.save()
        canvas.translate((width-side)/2f,(height-side)/2f)
        canvas.scale(side/48f,side/48f)
        val pair=when(tool) {
            "merge" -> intArrayOf(0xFFFF7F93.toInt(),0xFFED3857.toInt())
            "pages","extract","split" -> intArrayOf(0xFF62D8FF.toInt(),0xFF067CEB.toInt())
            "organize" -> intArrayOf(0xFF69F5B7.toInt(),0xFF00B77C.toInt())
            "compress" -> intArrayOf(0xFFFFE27B.toInt(),0xFFFFA600.toInt())
            "images" -> intArrayOf(0xFFCB9CFF.toInt(),0xFF7639F4.toInt())
            else -> intArrayOf(0xFF83F3EA.toInt(),0xFF00B9B5.toInt())
        }
        p.reset();p.isAntiAlias=true
        p.shader=LinearGradient(0f,0f,48f,48f,pair[0],pair[1],Shader.TileMode.CLAMP)
        canvas.drawRoundRect(1f,1f,47f,47f,10f,10f,p)
        p.shader=null
        when(tool) {
            "merge" -> {
                document(canvas,7f,9f,16f,23f)
                document(canvas,25f,9f,16f,23f)
                line(canvas,15f,34f,24f,40f,Color.WHITE,3.4f)
                line(canvas,33f,34f,24f,40f,Color.WHITE,3.4f)
                line(canvas,24f,32f,24f,40f,Color.WHITE,3.4f)
            }
            "pages","extract","split" -> {
                document(canvas,9f,7f,29f,32f)
                line(canvas,24f,7f,24f,36f,0xFF0665D4.toInt(),2.4f)
                p.color=0xFF0760CC.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=3.5f
                canvas.drawCircle(17f,33f,5f,p);canvas.drawCircle(32f,33f,5f,p)
                line(canvas,19f,29f,30f,17f,0xFF0760CC.toInt(),3f)
                line(canvas,30f,29f,19f,17f,0xFF0760CC.toInt(),3f)
            }
            "organize" -> {
                document(canvas,20f,7f,21f,28f,false)
                document(canvas,13f,12f,21f,28f,false)
                document(canvas,6f,17f,21f,26f,false)
                line(canvas,11f,26f,21f,26f,0xFF17B37C.toInt(),2.2f)
                line(canvas,11f,31f,21f,31f,0xFF17B37C.toInt(),2.2f)
            }
            "compress" -> {
                document(canvas,7f,7f,23f,33f)
                line(canvas,36f,10f,36f,20f,0xFFE96C00.toInt(),4f)
                line(canvas,31f,16f,36f,21f,0xFFE96C00.toInt(),4f)
                line(canvas,41f,16f,36f,21f,0xFFE96C00.toInt(),4f)
                line(canvas,31f,30f,36f,25f,0xFFE96C00.toInt(),4f)
                line(canvas,41f,30f,36f,25f,0xFFE96C00.toInt(),4f)
                line(canvas,36f,26f,36f,38f,0xFFE96C00.toInt(),4f)
            }
            "images" -> {
                fill(canvas,6f,9f,29f,31f,4f,Color.WHITE)
                fill(canvas,9f,12f,26f,28f,2f,0xFF6ECAFF.toInt())
                p.color=0xFFFFDA55.toInt();canvas.drawCircle(21f,17f,3f,p)
                val mountain=Path().apply {moveTo(9f,28f);lineTo(16f,18f);lineTo(21f,23f);lineTo(25f,19f);lineTo(26f,28f);close()}
                p.color=0xFF3154CF.toInt();canvas.drawPath(mountain,p)
                document(canvas,27f,22f,16f,22f)
            }
            else -> {
                document(canvas,6f,9f,21f,29f)
                fill(canvas,24f,18f,43f,39f,3f,Color.WHITE)
                fill(canvas,27f,21f,40f,36f,2f,0xFF6ECAFF.toInt())
                p.color=0xFFFFD34D.toInt();canvas.drawCircle(36f,25f,2.4f,p)
                val hill=Path().apply {moveTo(27f,36f);lineTo(32f,27f);lineTo(36f,32f);lineTo(39f,29f);lineTo(40f,36f);close()}
                p.color=0xFF3154CF.toInt();canvas.drawPath(hill,p)
            }
        }
        canvas.restore()
    }
}

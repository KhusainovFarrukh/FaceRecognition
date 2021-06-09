package kh.farrukh.facerecognition.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kh.farrukh.facerecognition.database.Recognition

/**
 *Created by farrukh_kh on 6/8/21 12:56 PM
 *kh.farrukh.facerecognition.customviews
 **/
class RecognitionScoreView(context: Context, set: AttributeSet) :
    View(context, set) {

    companion object {
        private const val TEXT_SIZE_DIP = 14f
    }

    private val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
    )
    private val fgPaint = Paint()
    private val bgPaint = Paint()
    private var results: List<Recognition>? = null

    init {
        fgPaint.textSize = textSizePx
        bgPaint.color = -0x33bd7a0c
    }

    public override fun onDraw(canvas: Canvas) {
        val x = 10
        var y = (fgPaint.textSize * 1.5f).toInt()
        canvas.drawPaint(bgPaint)
        results?.let {
            for (recognition in it) {
                canvas.drawText(
                    recognition.title + ": " + recognition.distance,
                    x.toFloat(),
                    y.toFloat(),
                    fgPaint
                )
                y += (fgPaint.textSize * 1.5f).toInt()
            }
        }
    }

    fun setResults(results: List<Recognition>?) {
        this.results = results
        postInvalidate()
    }
}
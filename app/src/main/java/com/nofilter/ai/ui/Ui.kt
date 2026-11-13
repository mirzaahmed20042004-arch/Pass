package com.nofilter.ai.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.nofilter.ai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ui {

    class Palette(
        val background: Int, val surface: Int, val surface2: Int,
        val text: Int, val muted: Int, val border: Int,
        val accent: Int, val accent2: Int
    ) {
        val codeBg = Color.parseColor("#0A0E1A")
        val userBubbleStart = Color.parseColor("#7C3AED")
        val userBubbleEnd = Color.parseColor("#2563EB")
        val danger = Color.parseColor("#F87171")
        val ok = Color.parseColor("#34D399")
    }

    fun palette(ctx: Context): Palette {
        val attrs = intArrayOf(
            R.attr.nfBackground, R.attr.nfSurface, R.attr.nfSurface2,
            R.attr.nfText, R.attr.nfMuted, R.attr.nfBorder, R.attr.nfAccent, R.attr.nfAccent2
        )
        val ta = ctx.obtainStyledAttributes(attrs)
        val p = Palette(
            ta.getColor(0, Color.BLACK), ta.getColor(1, Color.DKGRAY), ta.getColor(2, Color.GRAY),
            ta.getColor(3, Color.WHITE), ta.getColor(4, Color.LTGRAY), ta.getColor(5, Color.GRAY),
            ta.getColor(6, Color.parseColor("#8B5CF6")), ta.getColor(7, Color.parseColor("#22D3EE"))
        )
        ta.recycle()
        return p
    }

    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    fun icon(ctx: Context, resId: Int, sizeDp: Int, tint: Int): Drawable? {
        val d = ctx.getDrawable(resId) ?: return null
        d.setBounds(0, 0, dp(ctx, sizeDp.toFloat()), dp(ctx, sizeDp.toFloat()))
        d.setTint(tint)
        return d
    }

    fun tint(imageView: ImageView, tint: Int) {
        imageView.setColorFilter(tint)
    }

    fun bubble(p: Palette, isUser: Boolean, radiusDp: Float = 18f): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dpDp(p, radiusDp)
        if (isUser) {
            g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
            g.colors = intArrayOf(p.userBubbleStart, p.userBubbleEnd)
        } else {
            g.setColor(p.surface2)
        }
        return g
    }

    private fun dpDp(p: Palette, v: Float): Float = v * 2.6f // approx density independent-ish; fine for bubbles

    fun inputBackground(p: Palette): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = 22f * 2.6f
        g.setColor(p.surface2)
        g.setStroke((1 * 2.6f).toInt(), p.border)
        return g
    }

    fun text(ctx: Context, text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        tv.setTextColor(color)
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD)
        return tv
    }

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun timeStr(ts: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ts
        return when {
            diff < 60_000 -> "now"
            diff < 3600_000 -> "${diff / 60_000}m"
            diff < 86400_000 -> "${diff / 3600_000}h"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        }
    }

    fun fullTime(ts: Long): String =
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(ts))

    fun hide(view: View) { view.visibility = View.GONE }
    fun show(view: View) { view.visibility = View.VISIBLE }

    fun setTintList(view: ImageView, color: Int) {
        view.imageTintList = ColorStateList.valueOf(color)
    }
}

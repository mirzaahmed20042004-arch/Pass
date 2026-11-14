package com.voicefilter.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Rounded neon level meter. */
public class LevelMeterView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float level;
    private float smooth;

    public LevelMeterView(Context context) {
        super(context);
        init();
    }

    public LevelMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setColor(0xFF1B2440);
    }

    public void setLevel(float rms) {
        // map rms (0..~0.5) to a lively 0..1 scale
        level = Math.max(0f, Math.min(1f, rms * 4.5f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, bgPaint);

        smooth += (level - smooth) * 0.35f;
        if (smooth > 0.02f) {
            float bw = Math.max(h, w * smooth);
            barPaint.setShader(new LinearGradient(0, 0, w, 0, 0xFF22D3EE, 0xFFF472B6, Shader.TileMode.CLAMP));
            rect.set(0, 0, bw, h);
            canvas.drawRoundRect(rect, r, r, barPaint);
        }
        level *= 0.82f; // decay between updates
    }
}

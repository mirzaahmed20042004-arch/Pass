package com.voicefilter.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Peak-bucket waveform of the last take, drawn as a neon gradient mirror wave. */
public class WaveformView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] peaks = new float[0];

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        basePaint.setColor(0xFF1A2440);
    }

    public void setPcm(short[] pcm) {
        int buckets = 96;
        peaks = new float[buckets];
        if (pcm == null || pcm.length == 0) {
            invalidate();
            return;
        }
        int per = Math.max(1, pcm.length / buckets);
        float max = 1f;
        for (int b = 0; b < buckets; b++) {
            int off = b * per;
            float peak = 0;
            for (int i = off; i < off + per && i < pcm.length; i += 2) {
                float a = Math.abs(pcm[i] / 32768f);
                if (a > peak) peak = a;
            }
            peaks[b] = peak;
            if (peak > max) max = peak;
        }
        for (int b = 0; b < buckets; b++) peaks[b] = Math.min(1f, peaks[b] / max);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float mid = h / 2f;
        canvas.drawLine(0, mid, w, mid, basePaint);
        if (peaks.length == 0) return;
        barPaint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0xFF38BDF8, 0xFF8B5CF6, 0xFFF472B6},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        float bw = w / (float) peaks.length;
        for (int i = 0; i < peaks.length; i++) {
            float bh = Math.max(2f, peaks[i] * (h / 2f - 2f));
            float x = i * bw;
            canvas.drawRoundRect(x + bw * 0.18f, mid - bh, x + bw * 0.82f, mid + bh, 2f, 2f, barPaint);
        }
    }
}

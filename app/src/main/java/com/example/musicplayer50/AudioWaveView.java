package com.example.musicplayer50;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class AudioWaveView extends View {
    private static final int FRAME_DELAY_MS = 42;
    private static final int BAR_COUNT = 34;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final RectF barRect = new RectF();
    private float phase;
    private boolean playing;

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            phase += 0.18f;
            invalidate();
            if (playing) {
                handler.postDelayed(this, FRAME_DELAY_MS);
            }
        }
    };

    public AudioWaveView(Context context) {
        super(context);
        init();
    }

    public AudioWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        barPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Color.argb(34, 56, 189, 248));
        glowPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        barPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{
                        Color.rgb(56, 189, 248),
                        Color.rgb(244, 114, 182),
                        Color.rgb(251, 191, 36)
                },
                null,
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float centerY = height * 0.54f;
        float usableWidth = width * 0.84f;
        float startX = (width - usableWidth) / 2f;
        float gap = usableWidth / BAR_COUNT;
        float barWidth = Math.max(5f, gap * 0.48f);

        canvas.drawOval(width * 0.16f, height * 0.18f, width * 0.84f, height * 0.9f, glowPaint);

        for (int i = 0; i < BAR_COUNT; i++) {
            float x = startX + i * gap + (gap - barWidth) / 2f;
            float wave = (float) Math.sin(phase + i * 0.55f);
            float secondary = (float) Math.cos(phase * 0.72f + i * 0.33f);
            float amplitude = playing ? 0.42f + 0.36f * Math.abs(wave) + 0.18f * Math.abs(secondary) : 0.18f;
            float barHeight = Math.max(12f, height * amplitude);
            float top = centerY - barHeight / 2f;
            float bottom = centerY + barHeight / 2f;
            barPaint.setAlpha(playing ? 230 : 120);
            barRect.set(x, top, x + barWidth, bottom);
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, barPaint);
        }
    }

    public void start() {
        if (playing) {
            return;
        }
        playing = true;
        handler.removeCallbacks(frameRunnable);
        handler.post(frameRunnable);
    }

    public void stop() {
        playing = false;
        handler.removeCallbacks(frameRunnable);
        invalidate();
    }

    public boolean isPlayingAnimation() {
        return playing;
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(frameRunnable);
        super.onDetachedFromWindow();
    }
}

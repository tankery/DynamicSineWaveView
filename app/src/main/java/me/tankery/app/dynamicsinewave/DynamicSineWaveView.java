package me.tankery.app.dynamicsinewave;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.SystemClock;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by tankery on 8/27/15.
 *
 * A View can display the animated sine wave, and the wave can be dynamic changed by input.
 */
public class DynamicSineWaveView extends View {

    // Each cycle contains at least 10 points
    private static final int POINTS_FOR_CYCLE = 10;

    public static class Wave {
        public float amplitude; // wave amplitude, relative to view, range from 0 ~ 0.5
        public float cycle;     // contains how many cycles in scene
        public float speed;     // space per second, relative to view. X means the wave move X times of width per second

        public Wave(float a, float c, float s) {
            this.amplitude = a;
            this.cycle = c;
            this.speed = s;
        }

        public Wave(Wave other) {
            this.amplitude = other.amplitude;
            this.cycle = other.cycle;
            this.speed = other.speed;
        }
    }

    private final List<Wave> waveConfigs = new ArrayList<>();
    private final List<Paint> wavePaints = new ArrayList<>();
    private final Matrix wavePathScale = new Matrix();
    private List<Path> currentPaths = new ArrayList<>();
    private Path drawingPath = new Path();

    private int viewWidth = 0;
    private int viewHeight = 0;

    private float baseWaveAmplitudeFactor = 0.1f;

    /**
     * The computation result, a set of wave path can draw.
     */
    private final BlockingQueue<List<Path>> transferPathsQueue = new LinkedBlockingQueue<>(1);

    /**
     * animate ticker will set this to true to request further data change.
     * computer set this to false before compute, after computation, if this still false,
     * stop and wait for next request, or continue to compute next data change.
     *
     * After data change, the computer post a invalidate to redraw.
     */
    private boolean requestFutureChange = false;
    private final Object requestCondition = new Object();

    private long startAnimateTime = 0;

    private Runnable animateTicker = new Runnable() {
        public void run() {
            requestUpdateFrame();
            ViewCompat.postOnAnimation(DynamicSineWaveView.this, this);
        }
    };

    public DynamicSineWaveView(Context context) {
        super(context);
        init();
    }

    public DynamicSineWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DynamicSineWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (isInEditMode()) {
            addWave(0.5f, 0.5f, 0, 0, 0);
            addWave(0.5f, 2.5f, 0, Color.YELLOW, 2);
            addWave(0.3f, 2f, 0, Color.RED, 2);
            setBaseWaveAmplitudeFactor(1);
            tick();
            return;
        }

        createComputationThread().start();
    }

    /**
     * Add new wave to the view.
     *
     * The first added wave will become the 'base wave', which ignored the color & stroke, and
     * other wave will multiple with the 'base wave'.
     *
     * @param amplitude wave amplitude, relative to view, range from 0 ~ 0.5
     * @param cycle contains how many cycles in scene
     * @param speed space per second, relative to view. X means the wave move X times of width per second
     * @param color the wave color, ignored when add the 'base wave'
     * @param stroke wave stroke width, in pixel, ignored when add the 'base wave'
     * @return wave count (exclude the base wave).
     */
    public int addWave(float amplitude, float cycle, float speed, int color, float stroke) {
        synchronized (waveConfigs) {
            waveConfigs.add(new Wave(amplitude, cycle, speed));
        }

        if (waveConfigs.size() > 1) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(stroke);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            wavePaints.add(paint);
        }

        return wavePaints.size();
    }

    public void clearWave() {
        synchronized (waveConfigs) {
            waveConfigs.clear();
        }
        wavePaints.clear();
    }

    public void startAnimation() {
        startAnimateTime = SystemClock.uptimeMillis();
        removeCallbacks(animateTicker);
        ViewCompat.postOnAnimation(this, animateTicker);
    }

    public void stopAnimation() {
        removeCallbacks(animateTicker);
    }

    public void setBaseWaveAmplitudeFactor(float factor) {
        baseWaveAmplitudeFactor = factor;
    }

    public float getBaseWaveAmplitudeFactor() {
        return baseWaveAmplitudeFactor;
    }

    // Update just one frame.
    public void requestUpdateFrame() {
        synchronized (requestCondition) {
            requestFutureChange = true;
            requestCondition.notify();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (!transferPathsQueue.isEmpty()) {
            currentPaths = transferPathsQueue.poll();

            if (currentPaths.size() != wavePaints.size()) {
                throw new RuntimeException("Generated paths size " + currentPaths.size() +
                        " not match the paints size " + wavePaints.size());
            }
        }

        if (currentPaths.isEmpty())
            return;

        wavePathScale.setScale(viewWidth, viewHeight * baseWaveAmplitudeFactor);
        wavePathScale.postTranslate(0, viewHeight / 2);
        for (int i = 0; i < currentPaths.size(); i++) {
            Path path = currentPaths.get(i);
            Paint paint = wavePaints.get(i);

            drawingPath.set(path);
            drawingPath.transform(wavePathScale);
            canvas.drawPath(drawingPath, paint);
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        viewWidth = getWidth();
        viewHeight = getHeight();
    }

    private Thread createComputationThread() {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    synchronized (requestCondition) {
                        try {
                            if (!requestFutureChange)
                                requestCondition.wait();
                            if (!requestFutureChange)
                                continue;
                            requestFutureChange = false;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // If tick has new data offered, post a invalidate notify.
                    if (tick()) {
                        postInvalidate();
                    }
                }
            }
        });
    }

    private boolean tick() {
        List<Wave> waveList;
        List<Path> newPaths;

        synchronized (waveConfigs) {
            if (waveConfigs.size() < 2)
                return false;

            waveList = new ArrayList<>(waveConfigs.size());
            newPaths = new ArrayList<>(waveConfigs.size());

            for (Wave o : waveConfigs) {
                waveList.add(new Wave(o));
            }
        }

        long currentTime = SystemClock.uptimeMillis();
        float t = (currentTime - startAnimateTime) / 1000f;

        float maxCycle = 0;
        float maxAmplitude = 0;
        for (int i = 0; i < waveList.size(); i++) {
            Wave w = waveList.get(i);
            if (w.cycle > maxCycle)
                maxCycle = w.cycle;
            if (w.amplitude > maxAmplitude && i > 0)
                maxAmplitude = w.amplitude;
        }
        int pointCount = (int) (POINTS_FOR_CYCLE * maxCycle);

        Wave baseWave = waveList.get(0);
        waveList.remove(0);

        float normal = baseWave.amplitude / maxAmplitude;
        List<PointF> baseWavePoints = generateSineWave(baseWave.amplitude, baseWave.cycle, - baseWave.speed * t, pointCount);

        for (Wave w : waveList) {
            float space = - w.speed * t;

            List<PointF> wavePoints = generateSineWave(w.amplitude, w.cycle, space, pointCount);
            if (wavePoints.size() != baseWavePoints.size()) {
                throw new RuntimeException("base wave point size " + baseWavePoints.size() +
                        " not match the sub wave point size " + wavePoints.size());
            }

            // multiple up
            for (int i = 0; i < wavePoints.size(); i++) {
                PointF p = wavePoints.get(i);
                PointF base = baseWavePoints.get(i);
                p.set(p.x, p.y * base.y * normal);
            }

            Path path = generatePathForCurve(wavePoints);
            newPaths.add(path);
        }

        // offer the new wave paths & post invalidate to redraw.
        transferPathsQueue.offer(newPaths);
        return true;
    }

    private List<PointF> generateSineWave(float amplitude, float cycle, float offset, int pointCount) {
        int count = pointCount;
        if (count <= 0)
            count = (int) (POINTS_FOR_CYCLE * cycle);
        double T = 1. / cycle;
        double f = 1f / T;

        List<PointF> points = new ArrayList<>(count);
        if (count < 2)
            return points;

        double dx = 1. / (count - 1);
        for (double x = 0; x <= 1; x+= dx) {
            double y = amplitude * Math.sin(2 * Math.PI * f * (x + offset));
            points.add(new PointF((float) x, (float) y));
        }

        return points;
    }

    private Path generatePathForCurve(List<PointF> points) {
        Path path = new Path();
        if (points.isEmpty())
            return path;

        path.reset();

        PointF prevPoint = points.get(0);
        for (int i = 0; i < points.size(); i++) {
            PointF point = points.get(i);

            if (i == 0) {
                path.moveTo(point.x, point.y);
            } else {
                float midX = (prevPoint.x + point.x) / 2;
                float midY = (prevPoint.y + point.y) / 2;

                if (i == 1) {
                    path.lineTo(midX, midY);
                } else {
                    path.quadTo(prevPoint.x, prevPoint.y, midX, midY);
                }
            }
            prevPoint = point;
        }
        path.lineTo(prevPoint.x, prevPoint.y);

        return path;
    }

}

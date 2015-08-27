package me.tankery.app.dynamicsinewave;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.SystemClock;
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
        public float amplitude; // wave amplitude, in dimension unit
        public float cycle;     // contains how many cycles in scene
        public float speed;     // dimens per second

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

    private int viewWidth = 0;
    private int viewHeight = 0;

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
            synchronized (requestCondition) {
                requestFutureChange = true;
                requestCondition.notify();
            }
            postDelayed(this, 20); // 20ms == 60fps
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

    /**
     * Add new wave to the view.
     *
     * The first added wave will become the 'base wave', which ignored the color & stroke, and
     * other wave will multiple with the 'base wave'.
     *
     * @param amplitude wave amplitude, in dimension
     * @param cycle contains how many cycles in scene
     * @param speed wave moves speed, in dimens per second
     * @param color the wave color, ignored when add the 'base wave'
     * @param stroke wave stroke width, in dimension, ignored when add the 'base wave'
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
        post(animateTicker);
    }

    public void stopAnimation() {
        removeCallbacks(animateTicker);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (transferPathsQueue.isEmpty())
            return;

        List<Path> paths = transferPathsQueue.poll();

        if (paths.size() != wavePaints.size()) {
            throw new RuntimeException("Generated paths size " + paths.size() +
                    " not match the paints size " + wavePaints.size());
        }

        canvas.save();
        canvas.translate(0, viewHeight / 2);
        for (int i = 0; i < paths.size(); i++) {
            Path path = paths.get(i);
            Paint paint = wavePaints.get(i);

            canvas.drawPath(path, paint);
        }
        canvas.restore();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        viewWidth = getWidth();
        viewHeight = getHeight();
    }

    private void init() {
        if (isInEditMode())
            return;
        createComputationThread().start();
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

                    List<Wave> waveList;
                    List<Path> newPaths;

                    synchronized (waveConfigs) {
                        if (waveConfigs.size() < 2)
                            continue;

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
                    for (Wave w : waveList) {
                        if (w.cycle > maxCycle)
                            maxCycle = w.cycle;
                        if (w.amplitude > maxAmplitude)
                            maxAmplitude = w.amplitude;
                    }
                    int pointCount = (int) (POINTS_FOR_CYCLE * maxCycle);

                    Wave baseWave = waveList.get(0);
                    waveList.remove(0);

                    float normal = baseWave.amplitude / (baseWave.amplitude * maxAmplitude);
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
                    postInvalidate();
                }
            }
        });
    }

    private List<PointF> generateSineWave(float amplitude, float cycle, float offset, int pointCount) {
        int w = viewWidth;
        int h = viewHeight;
        if (amplitude > h / 2f) {
            amplitude = h / 2f;
        }

        int count = pointCount;
        if (count <= 0)
            count = (int) (POINTS_FOR_CYCLE * cycle);
        double T = w / cycle;
        double f = 1f / T;

        List<PointF> points = new ArrayList<>(count);
        if (count < 2 || w == 0 || h == 0)
            return points;

        for (int i = 0; i < count; i++) {
            double x = i * w / (count - 1);
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

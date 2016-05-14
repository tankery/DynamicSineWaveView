# DynamicSineWaveView

A View that can display the animated sine wave, and the wave properties can be dynamic change.

![Dynamic Sine Wave](art/dynamic-sine-wave.gif)

## Usage

1. Copy the View (no resource needed) to your project.
2. Layout with the `me.tankery.app.dynamicsinewave.DynamicSineWaveView`
3. Add waves for the view programatically.

The layout:

``` xml
<me.tankery.app.dynamicsinewave.DynamicSineWaveView
    android:id="@+id/view_sine_wave"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

The `addWave` method.

``` java
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
public int addWave(float amplitude, float cycle, float speed, int color, float stroke);
```

How to use in code:

``` java
wavesView = (DynamicSineWaveView) findViewById(R.id.view_sine_wave);
wavesView.addWave(0.5f, 0.5f, 0, 0, 0); // Fist wave is for the shape of other waves.
wavesView.addWave(0.5f, 2f, 0.5f, getResources().getColor(android.R.color.holo_red_dark), stroke);
wavesView.addWave(0.1f, 2f, 0.7f, getResources().getColor(android.R.color.holo_blue_dark), stroke);
wavesView.startAnimation();
```

If you need to stop the animation, call:

``` java
wavesView.stopAnimation();
```

What's more, you can even change the `amplitude` when need (useful when you need to response for a voice input):

``` java
wavesView.setBaseWaveAmplitudeScale(1);
```


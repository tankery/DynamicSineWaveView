package me.tankery.app.dynamicsinewave;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;


public class MainActivity extends Activity {

    DynamicSineWaveView dynamicSineWaveView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        float stroke = dipToPixels(this, 2);
        dynamicSineWaveView = (DynamicSineWaveView) findViewById(R.id.view_sine_wave);
        dynamicSineWaveView.addWave(0.5f, 0.5f, 0, 0, 0);
        dynamicSineWaveView.addWave(0.5f, 2f, 0.5f, getResources().getColor(android.R.color.holo_red_dark), stroke);
        dynamicSineWaveView.addWave(0.1f, 2f, 0.7f, getResources().getColor(android.R.color.holo_blue_dark), stroke);
        dynamicSineWaveView.setBaseWaveAmplitudeFactor(1);
        dynamicSineWaveView.startAnimation();
    }

    @Override
    protected void onDestroy() {
        dynamicSineWaveView.stopAnimation();
        super.onDestroy();
    }

    public static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }

}

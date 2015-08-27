package me.tankery.app.dynamicsinewave;

import android.app.Activity;
import android.os.Bundle;


public class MainActivity extends Activity {

    DynamicSineWaveView dynamicSineWaveView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dynamicSineWaveView = (DynamicSineWaveView) findViewById(R.id.view_sine_wave);
        dynamicSineWaveView.addWave(400, 0.5f, 0, 0, 0);
        dynamicSineWaveView.addWave(200, 2f, 100, getResources().getColor(android.R.color.holo_red_dark), 2);
        dynamicSineWaveView.addWave(100, 2f, 300, getResources().getColor(android.R.color.holo_blue_dark), 2);
        dynamicSineWaveView.startAnimation();
    }

    @Override
    protected void onDestroy() {
        dynamicSineWaveView.stopAnimation();
        super.onDestroy();
    }

}

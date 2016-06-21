package com.ksy.recordlib.service.util;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;

/**
 * Created by hansentian on 9/26/15.
 */
public abstract class OrientationObserver extends OrientationEventListener {

    public OrientationObserver(Context context) {
        super(context, SensorManager.SENSOR_DELAY_NORMAL);
        disable();
    }

    @Override
    public void onOrientationChanged(int orientation) {
        onOrientationChangedEvent(orientation);
//        mCurrentOrientation = (((orientation + 45) / 90) * 90) % 360;
    }


    public abstract void onOrientationChangedEvent(int orientation);


}



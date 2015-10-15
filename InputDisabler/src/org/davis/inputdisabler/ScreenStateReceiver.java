package org.davis.inputdisabler;

/*
 * Created by Dāvis Mālnieks on 04/10/2015
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;

import org.davis.inputdisabler.Constants;

public class ScreenStateReceiver extends BroadcastReceiver implements SensorEventListener {

    public static final String TAG = "ScreenStateReceiver";

    public static final boolean DEBUG = false;

    public static final int DOZING_TIME = 1000 * 5;

    android.os.Handler mDozeDisable;

    boolean mScreenOn;

    SensorManager mSensorManager;

    Sensor mSensor;

    @Override
    public void onReceive(Context context, Intent intent) {

        if(DEBUG)
            Log.d(TAG, "Received intent");

        switch (intent.getAction()) {
            case Intent.ACTION_SCREEN_ON:
                Log.d(TAG, "Screen on!");
                mScreenOn = true;

                // Perform enable->disable->enable sequence
                enableDevices(true);
				enableDevices(false, true);
				enableDevices(true, true);
                break;
            case Intent.ACTION_SCREEN_OFF:
                Log.d(TAG, "Screen off!");

                mScreenOn = false;

                enableDevices(false);
                break;
            case Constants.ACTION_DOZE_PULSE_STARTING:
                Log.d(TAG, "Doze");

                mDozeDisable = new Handler();
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        if(!mScreenOn) {
                            if(DEBUG)
                                Log.d(TAG, "Screen was turned on while dozing");

                            enableDevices(false);
                        } else {
                            if(DEBUG)
                                Log.d(TAG, "Screen was turned off while dozing");

                            enableDevices(true);
                        }
                    }
                };
                mDozeDisable.postDelayed(runnable, DOZING_TIME);

                // Don't enable touch keys when dozing
                // Perform enable->disable->enable sequence
                enableDevices(true, true);
		enableDevices(false, true);
		enableDevices(true, true);
                break;
            case TelephonyManager.ACTION_PHONE_STATE_CHANGED:
                Log.d(TAG, "Phone state changed!");

                final TelephonyManager telephonyManager =
                        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

                switch (telephonyManager.getCallState()) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                        mSensorManager.registerListener(this, mSensor, 3);
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if(mSensorManager != null) {
                            mSensorManager.unregisterListener(this);
                        }
                        break;
                }


                break;
        }
    }

    /*
     * Wrapper method
     */
    private void enableDevices(boolean enable) {
        enableDevices(enable, false);
    }

    /* Enables or disables input devices by writing to sysfs path
     *  Only touch is used when dozing
     */
    private void enableDevices(boolean enable, boolean onlyTouch) {

        boolean ret;
        if(enable) {
            if(!onlyTouch) {
                // Turn on key input
                ret = write_sysfs(Constants.TK_PATH, true);
                if (DEBUG)
                    Log.d(TAG, "Enabled touch keys, success? " + ret);
            }

            // Turn on touch input
            ret = write_sysfs(Constants.TS_PATH, true);
            if(DEBUG)
                Log.d(TAG, "Enabled touchscreen, success? " + ret);
        } else {
            if(!onlyTouch) {
                // Turn off key input
                ret = write_sysfs(Constants.TK_PATH, false);
                if(DEBUG)
                    Log.d(TAG, "Disabled touch keys, success? " + ret);
            }

            // Turn off touch input
            ret = write_sysfs(Constants.TS_PATH, false);
            if(DEBUG)
                Log.d(TAG, "Disabled touchscreen, success? " + ret);
        }
    }

    // Writes to sysfs node, returns true if success, false if fail
    private boolean write_sysfs(String path, boolean on) {
        try {
            FileOutputStream fos = new FileOutputStream(path);
            byte[] bytes = new byte[2];
            bytes[0] = (byte)(on ? '1' : '0');
            bytes[1] = '\n';
            fos.write(bytes);
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Fail: " + e.getLocalizedMessage());
            return false;
        }
        
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.values[0] == 0.0f) {
            if(DEBUG)
            	Log.d(TAG, "Proximity: screen off");

	    enableDevices(false);
        } else {
            if(DEBUG)
            	Log.d(TAG, "Proximity: screen on");

            // Perform enable->disable->enable sequence
            enableDevices(true);
            enableDevices(false, true);
            enableDevices(true, true);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}

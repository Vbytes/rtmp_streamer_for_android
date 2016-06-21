package com.ksy.recordlib.service.util;

import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

/**
 * Created by hansentian on 9/26/15.
 */
public class CameraUtil {

    // 由于摄像头水平镜像，前置摄像头横屏拍摄时需旋转180度

    /**
     * @param previewDegrees cameraDisplayDegrees
     * @param orientation    onOrientationChanged获取的orientation
     * @param isFrontCamera
     * @return
     */
    public static int getMediaRecordRotation(int previewDegrees, int orientation, boolean isFrontCamera) {
        int degree = (previewDegrees
                + orientation + (isFrontCamera
                && orientation != 0 && orientation % 90 == 0 ? 180 : 0)) % 360;
        return degree;
    }

    public static int getDisplayOrientation(Activity activity, int mCameraId, boolean isFrontCamera) {
        int mDisplayRotation = getDisplayRotation(activity);
        int mDisplayOrientation = getDisplayOrientation(mDisplayRotation, mCameraId);
        int mCameraDisplayOrientation = getDisplayOrientation(0, mCameraId);
        int previewDegrees = (!isFrontCamera) ? mDisplayOrientation
                : mDisplayOrientation + 180;
        previewDegrees = previewDegrees % 360;
        Log.e("degree", "previewDegrees" + previewDegrees);
        return previewDegrees;
    }


    //    廿四桥 13:02:49
//    Util的getDisplayOrientation方法

    public static int getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }


}
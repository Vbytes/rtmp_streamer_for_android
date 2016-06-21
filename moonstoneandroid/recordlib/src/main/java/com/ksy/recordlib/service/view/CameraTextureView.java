package com.ksy.recordlib.service.view;

import android.content.Context;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.TextureView;

public class CameraTextureView extends TextureView {

    private static final String TAG = CameraTextureView.class.getSimpleName();

    private Camera.Size previewSize;

    public CameraTextureView(Context context) {
        this(context, null);
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CameraTextureView setPreviewSize(Camera.Size pSize) {
        if (pSize != null) {
            previewSize = pSize;
            fixPreviewFrame();
        }
        return this;
    }

    private void fixPreviewFrame() {
        final int viewWid = getWidth();
        final int viewHei = getHeight();
        if (viewWid == 0 || viewHei == 0) {
            return;
        }
        Matrix matrix = new Matrix();
        getTransform(matrix);
        final int previewWidth = previewSize.height;
        final int previewHei = previewSize.width;
        float scaleWid = (float) viewWid / previewWidth;
        float scaleHei = (float) viewHei / previewHei;

//        Log.v(TAG, "fixPreviewFrame previewWid=" + previewWidth + " previewHei=" + previewHei);
//        Log.v(TAG, "fixPreviewFrame viewWid=" + viewWid + " viewHei=" + viewHei);
//        Log.v(TAG, "fixPreviewFrame scaleWid " + scaleWid + " scaleHei=" + scaleHei);
        float fixFactor = 1f;
        if (scaleWid < scaleHei) {
            fixFactor = scaleHei;
        } else {
            fixFactor = scaleWid;
        }
        matrix.setScale(1f / scaleWid * fixFactor, 1f / scaleHei * fixFactor, viewWid / 2, viewHei / 2);
        setTransform(matrix);
    }


}

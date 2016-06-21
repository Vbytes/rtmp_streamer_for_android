package com.ksy.ksyrecordsdk;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.ksy.recordlib.service.core.KsyRecordClient;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.ScreenResolution;

/**
 * Created by eflakemac on 15/6/11.
 */
public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback, KsyRecordClient.CameraSizeChangeListener {
    private final Context mContext;
    private int mVideoSarNum;
    private int mVideoSarDen;
    private float mVideoHeight;
    private int mVideoWidth;
    private float mSurfaceHeight;
    private int mSurfaceWidth;

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
//        getHolder().addCallback(this);
    }




   /* public void setVideoLayout(int layout) {

        Log.d(Constants.LOG_TAG, "SetVideoLayout ,Mode = " + layout);
        Pair<Integer, Integer> res = ScreenResolution.getResolution(mContext);
        int windowWidth = res.first.intValue(), windowHeight = res.second
                .intValue();
        float windowRatio = windowWidth / (float) windowHeight;
        ViewGroup.LayoutParams lp = getLayoutParams();
        int sarNum = mVideoSarNum;
        int sarDen = mVideoSarDen;
        if (mVideoHeight > 0 && mVideoWidth > 0) {
            float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;
            if (sarNum > 0 && sarDen > 0)
                videoRatio = videoRatio * sarNum / sarDen;
            mSurfaceHeight = mVideoHeight;
            mSurfaceWidth = mVideoWidth;

            if (layout == MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_16_9) {
                // 16:9
                float target_ratio = 16.0f / 9.0f;
                float dh = windowHeight;
                float dw = windowWidth;
                if (windowRatio < target_ratio) {
                    dh = dw / target_ratio;
                } else {
                    dw = dh * target_ratio;
                }
                lp.width = (int) dw;
                lp.height = (int) dh;

            } else if (layout == MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_4_3) {
                // 4:3
                float target_ratio = 4.0f / 3.0f;
                float source_height = windowHeight;
                float source_width = windowWidth;
                if (windowRatio < target_ratio) {
                    source_height = source_width / target_ratio;
                } else {
                    source_width = source_height * target_ratio;
                }
                lp.width = (int) source_width;
                lp.height = (int) source_height;
            } *//*
             * else if (layout ==
			 * MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_ORIGIN &&
			 * mSurfaceWidth < windowWidth && mSurfaceHeight < windowHeight) {
			 * // origin lp.width = (int) (mSurfaceHeight * videoRatio);
			 * lp.height = mSurfaceHeight; } else if (layout ==
			 * MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_FULLSCREEN) { //
			 * fullscreen lp.width = (windowRatio < videoRatio) ? windowWidth :
			 * (int) (videoRatio * windowHeight); lp.height = (windowRatio >
			 * videoRatio) ? windowHeight : (int) (windowWidth / videoRatio); }
			 *//*

            setLayoutParams(lp);
            getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
//            Log.d(Constants.LOG_TAG, "VIDEO: %dx%dx%f[SAR:%d:%d],Layout :%d," +
//                            " Surface: %dx%d, LP: %dx%d, Window: %dx%dx%f", mVideoWidth, mVideoHeight,
//                    videoRatio, mVideoSarNum,
//                    mVideoSarDen, layout, mSurfaceWidth, mSurfaceHeight,
//                    lp.width, lp.height, windowWidth, windowHeight, windowRatio);
        }
        mVideoLayout = layout;
    }*/

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }


    @Override
    public void onCameraSizeChanged(int width, int height) {

    }

    @Override
    public void onCameraPreviewSize(int width, int height) {
//        Log.d(Constants.LOG_TAG, "camera preview size width = " + width + ",height = " + height);
//        ViewGroup.LayoutParams lp = getLayoutParams();
//        lp.width = width;
//        lp.height = height;
//        setLayoutParams(lp);
//        getHolder().setFixedSize(width, height);
    }
}

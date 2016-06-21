package com.ksy.recordlib.service.recoder;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceView;

import com.ksy.recordlib.service.core.KsyMediaSource;
import com.ksy.recordlib.service.core.KsyRecordClient;
import com.ksy.recordlib.service.core.KsyRecordClientConfig;
import com.ksy.recordlib.service.core.KsyRecordSender;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.FileUtil;
import com.ksy.recordlib.service.util.MP4Config;
import com.ksy.recordlib.service.util.OnClientErrorListener;
import com.ksy.recordlib.service.util.PrefUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by eflakemac on 15/6/19.
 */
public class RecoderVideoTempSource extends KsyMediaSource implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
    private final KsyRecordClient.RecordHandler mHandler;
    private final Context mContext;
    private Camera mCamera;
    private MediaRecorder mRecorder;
    private KsyRecordClientConfig mConfig;
    private String path;
    private static final int VIDEO_TEMP = 1;

    private KsyRecordSender ksyVideoTempSender;

    public RecoderVideoTempSource(Camera mCamera, KsyRecordClientConfig mConfig, SurfaceView mSurfaceView, KsyRecordClient.RecordHandler mRecordHandler, Context mContext) {
//        super(mConfig.getUrl(), VIDEO_TEMP);
        this.mCamera = mCamera;
        this.mConfig = mConfig;
        mRecorder = new MediaRecorder();
        mHandler = mRecordHandler;
        this.mContext = mContext;
        ksyVideoTempSender = KsyRecordSender.getRecordInstance();
    }

    @Override
    public void prepare() {
        try {
            mRecorder.setCamera(mCamera);
            mConfig.configMediaRecorder(mRecorder, KsyRecordClientConfig.MEDIA_TEMP);
            path = FileUtil.getOutputMediaFile(mContext, Constants.MEDIA_TYPE_VIDEO);
            mRecorder.setOutputFile(path);
            mRecorder.setMaxDuration(3000);
            mRecorder.setOnInfoListener(this);
            mRecorder.setOnErrorListener(this);
            mRecorder.prepare();
            mRecorder.start();
            mHandler.sendEmptyMessage(Constants.MESSAGE_MP4CONFIG_START_PREVIEW);
        } catch (Exception e) {
            e.printStackTrace();
            release();
            if (onClientErrorListener != null) {
                onClientErrorListener.onClientError(OnClientErrorListener.SOURCE_VIDEO_TEMP, OnClientErrorListener.ERROR_MEDIACODER_START_FAILED);
            }
        }
        Log.d(Constants.LOG_TAG, "record 400ms for mp4config");
        // Retrieve SPS & PPS & ProfileId with MP4Config
    }

    @Override
    public void start() {
        if (!mRunning) {
            mRunning = true;
            this.thread = new Thread(this);
            this.thread.start();
        }
    }

    @Override
    public void stop() {
        if (mRunning == true) {
            mRunning = false;
            release();
        }
    }

    @Override
    public void release() {
        releaseRecorder();
//        reconnectCamera();
    }

    private void reconnectCamera() {
        if (mCamera != null) {
            try {
                mCamera.reconnect();
                mCamera.lock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.setOnErrorListener(null);
            mRecorder.setOnInfoListener(null);
            try {
                mRecorder.reset();
                Log.d(Constants.LOG_TAG, "mRecorder reset");
                mRecorder.release();
                Log.d(Constants.LOG_TAG, "mRecorder release");
                mRecorder = null;
                Log.d(Constants.LOG_TAG, "mRecorder complete");
//                mCamera.lock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public void createFile(String path, byte[] content) {
        try {
            FileOutputStream outputStream = new FileOutputStream(path);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            bufferedOutputStream.write(content);
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onInfo Message what = " + what + ",extra =" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            Log.d(Constants.LOG_TAG, "MediaRecorder: MAX_DURATION_REACHED");
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            Log.d(Constants.LOG_TAG, "MediaRecorder: MAX_FILESIZE_REACHED");
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN) {
            Log.d(Constants.LOG_TAG, "MediaRecorder: INFO_UNKNOWN");
        } else {
            Log.d(Constants.LOG_TAG, "WTF ?");
        }
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onError Message what = " + what + ",extra =" + extra);
    }

    @Override
    public void run() {
        prepare();
        File file = null;
        long startTime = System.currentTimeMillis();
        if (mRunning) {
            do {
                file = new File(path);
                if (file.exists() && file.length() > (1024 * 50)) {
                    break;
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } while (System.currentTimeMillis() - startTime < 5000);
            release();
        }
        if (file != null && file.exists() && file.length() > 0) {
            if (mRunning) {
                try {
                    MP4Config config = new MP4Config(path);
                    // Delete dummy video
                    Log.e(Constants.LOG_TAG, "waiting use" + (System.currentTimeMillis() - startTime));
                    Log.d(Constants.LOG_TAG, "ProfileLevel = " + config.getProfileLevel() + ",B64SPS = " + config.getB64SPS() + ",B64PPS = " + config.getB64PPS());
                    PrefUtil.saveMp4Config(mContext, config);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!file.delete()) {
                    Log.e(Constants.LOG_TAG, "Temp file could not be erased");
                }
                mHandler.sendEmptyMessage(Constants.MESSAGE_MP4CONFIG_FINISH);
            }
        } else {
            Log.e(Constants.LOG_TAG, "waiting for temp file failed");
        }
        mRunning = false;
    }
}

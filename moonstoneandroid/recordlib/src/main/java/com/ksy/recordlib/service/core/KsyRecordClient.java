package com.ksy.recordlib.service.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.ksy.recordlib.service.data.SenderStatData;
import com.ksy.recordlib.service.exception.KsyRecordException;
import com.ksy.recordlib.service.recoder.RecoderAudioSource;
import com.ksy.recordlib.service.recoder.RecoderVideoSource;
import com.ksy.recordlib.service.recoder.RecoderVideoTempSource;
import com.ksy.recordlib.service.rtmp.KSYRtmpFlvClient;
import com.ksy.recordlib.service.util.CameraUtil;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.NetworkMonitor;
import com.ksy.recordlib.service.util.OnClientErrorListener;
import com.ksy.recordlib.service.util.OrientationActivity;
import com.ksy.recordlib.service.view.CameraTextureView;

import java.io.IOException;
import java.util.List;

/**
 * Created by eflakemac on 15/6/17.
 */
public class KsyRecordClient implements KsyRecord, OnClientErrorListener {

    public static final int CAMEAR_NO_FLASH = -2;
    public static final int CAMEAR_LIGHT_ERROR = -1;
    public static final int CAMEAR_FLASH_SUCCESS = 0;


    private static final String TAG = "KsyRecordClient";
    private static KsyRecordClient mInstance;
    private RecordHandler mRecordHandler;
    private Context mContext;
    private int mEncodeMode = Constants.ENCODE_MODE_MEDIA_RECORDER;
    private static KsyRecordClientConfig mConfig;
    private Camera mCamera;
    private KSYRtmpFlvClient mKsyRtmpFlvClient;
    private SurfaceView mSurfaceView;
    private TextureView mTextureView;
    private RecoderVideoSource mVideoSource;
    private KsyMediaSource mAudioSource;
    private KsyMediaSource mVideoTempSource;

    private KsyRecordSender ksyRecordSender;

    private OrientationActivity orientationActivity;

    private StartListener startListener;

    private STATE clientState = STATE.STOP;

    private int displayOrientation;
    private int currentCameraId;
    private CameraSizeChangeListener mCameraSizeChangedListener;
    private NetworkChangeListener mNetworkChangeListener;
    private PushStreamStateListener mPushStreamStateListener;
    private SwitchCameraStateListener mSwitchCameraStateListener;
    private OnClientErrorListener onClientErrorListener;
    private NetworkMonitor.OnNetworkPoorListener onNetworkPoorListener;

    public static final int NETWORK_UNAVAILABLE = -1;
    public static final int NETWORK_WIFI = 1;
    public static final int NETWORK_MOBILE = 0;
    private volatile boolean mSwitchCameraLock = false;
    public static long startWaitTIme, startTime;

    private boolean isCanTurnLightFlag = false;

    private enum STATE {
        RECORDING, STOP, PAUSE, ERROR
    }

    public interface CameraSizeChangeListener {
        void onCameraSizeChanged(int width, int height);

        void onCameraPreviewSize(int width, int height);
    }

    public interface NetworkChangeListener {
        void onNetworkChanged(int state);
    }

    public interface PushStreamStateListener {
        void onPushStreamState(int state);
    }

    public interface SwitchCameraStateListener {
        void onSwitchCameraDisable();

        void onSwitchCameraEnable();
    }

    public interface StartListener {
        int START_COMPLETE = 23;
        int START_FAILED = 24;

        void OnStartComplete();

        void OnStartFailed();
    }


    @Override
    public void onClientError(int source, int what) {
        if (onClientErrorListener != null) {
            onClientErrorListener.onClientError(source, what);
        }
        stopRecord();
    }

    private KsyRecordClient(Context context) {
        this.mContext = context;
        mRecordHandler = new RecordHandler();
        ksyRecordSender = KsyRecordSender.getRecordInstance();
        ksyRecordSender.setStateMonitor(mRecordHandler);
    }

    public void takePicture(Camera.PictureCallback callback) {
        try {
            mCamera.takePicture(null, null, callback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerNetworkMonitor() {
        // Monitor network
        IntentFilter networkFilter = new IntentFilter();
        networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        this.mContext.registerReceiver(mReceiver, networkFilter);
    }

    public void unregisterNetworkMonitor() {
        if (mReceiver != null) {
            this.mContext.unregisterReceiver(mReceiver);
        }
    }

    public static KsyRecordClient getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new KsyRecordClient(context);
        }
        return mInstance;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager mConnMgr = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo aActiveInfo = mConnMgr.getActiveNetworkInfo();
                if (aActiveInfo != null && aActiveInfo.isAvailable()) {
                    // Network available
                    int type = aActiveInfo.getType();
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        // Wifi available
                        NetworkInfo wifiInfo = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        mNetworkChangeListener.onNetworkChanged(KsyRecordClient.NETWORK_WIFI);
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        // Mobile Network available
                        NetworkInfo mobileInfo = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                        mNetworkChangeListener.onNetworkChanged(KsyRecordClient.NETWORK_MOBILE);
                    } else {
                        // Other network
                    }

                } else {
                    // Network unavailable
                    stopRecord();
                    mNetworkChangeListener.onNetworkChanged(KsyRecordClient.NETWORK_UNAVAILABLE);
                }
            }
        }
    };

    public KsyRecordClient setOnClientErrorListener(OnClientErrorListener onClientErrorListener) {
        this.onClientErrorListener = onClientErrorListener;
        return this;
    }

    public KsyRecordClient setOrientationActivity(OrientationActivity activity) {
        this.orientationActivity = activity;
        return this;
    }

//    public void setCameraSizeChangedListener(CameraSizeChangeListener listener) {
//        this.mCameraSizeChangedListener = listener;
//    }

    public void setNetworkChangeListener(NetworkChangeListener listener) {
        this.mNetworkChangeListener = listener;
    }

    public void setPushStreamStateListener(PushStreamStateListener mPushStreamStateListener) {
        this.mPushStreamStateListener = mPushStreamStateListener;
    }

    public void setSwitchCameraStateListener(SwitchCameraStateListener mSwitchCameraStateListener) {
        this.mSwitchCameraStateListener = mSwitchCameraStateListener;
    }

    public KsyRecordClient setStartListener(StartListener startListener) {
        this.startListener = startListener;
        return this;
    }

    public KsyRecordClient setOnNetworkPoorListener(NetworkMonitor.OnNetworkPoorListener onNetworkPoorListener) {
        this.onNetworkPoorListener = onNetworkPoorListener;
        return this;
    }

    /*
                    *
                    * Ks3 Record API
                    * */
    @Override
    public void startRecord() throws KsyRecordException {
        if (clientState == STATE.RECORDING) {
            return;
        }
        startTime = System.currentTimeMillis();
        mEncodeMode = judgeEncodeMode(mContext);
        try {
            mConfig.setOrientationActivity(orientationActivity);
            ksyRecordSender.setInputUrl(mConfig.getUrl());
            ksyRecordSender.start(mContext);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(Constants.LOG_TAG, "startRecord() : e =" + e);
        }
        mSwitchCameraLock = true;
        if (checkConfig()) {
            // Here we begin
            if (mEncodeMode == Constants.ENCODE_MODE_MEDIA_RECORDER) {
                setUpMp4Config(mRecordHandler);
            } else {
//                startRecordStep();
            }
        } else {
            throw new KsyRecordException("Check KsyRecordClient Configuration, param should be correct");
        }
        clientState = STATE.RECORDING;
    }

    private void startRecordStep() {
        if (setUpCamera(true)) {
            setUpEncoder();
        }
    }


    private void setUpMp4Config(RecordHandler mRecordHandler) {
        if (setUpCamera(true)) {
            if (mVideoTempSource == null) {
                mVideoTempSource = new RecoderVideoTempSource(mCamera, mConfig, mSurfaceView, mRecordHandler, mContext);
                mVideoTempSource.setOnClientErrorListener(this);
                mVideoTempSource.start();
            }
        }
    }

    private void startRtmpFlvClient() throws KsyRecordException {
        mKsyRtmpFlvClient = new KSYRtmpFlvClient(mConfig.getUrl());
        try {
            mKsyRtmpFlvClient.start();
        } catch (IOException e) {
            throw new KsyRecordException("start muxer failed");
        }
    }

    private boolean checkConfig() throws KsyRecordException {
        if (mConfig == null) {
            throw new KsyRecordException("should set KsyRecordConfig first");
        }
        if (mSurfaceView == null && mTextureView == null) {
            throw new KsyRecordException("preview surface or texture must be set first");
        }
        return mConfig.validateParam();
    }

    public boolean canTurnLight() {
        return isCanTurnLightFlag;
    }

    public int turnLight(boolean on) {
        if (mCamera == null) {
            return CAMEAR_LIGHT_ERROR;
        }
        Camera.Parameters parameters = null;
        try {
            parameters = mCamera.getParameters();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (parameters == null) {
            return CAMEAR_LIGHT_ERROR;
        }

        List<String> flashModes = parameters.getSupportedFlashModes();
        // check camera flash mode
        if (flashModes == null) {
            return CAMEAR_NO_FLASH;
        }

        String flashMode = parameters.getFlashMode();

        if (on) {
            if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                // turn on the flash
                if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    mCamera.setParameters(parameters);
                    return CAMEAR_FLASH_SUCCESS;
                }
            }
        } else {
            if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                // Turn off the flash
                if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    mCamera.setParameters(parameters);
                    return CAMEAR_FLASH_SUCCESS;
                }
            }
        }
        return CAMEAR_NO_FLASH;
    }

    private Camera.Size findBestPreviewSize(Camera mCamera, View view) {
        List<Camera.Size> sizeList = mCamera.getParameters().getSupportedPreviewSizes();
        if (sizeList == null || sizeList.isEmpty()) {
            return null;
        }
        final int viewWid = view.getWidth();
        final int viewHei = view.getHeight();
        final int viewArea = viewWid * viewHei;
        final int length = sizeList.size();
        Log.v(TAG, "findBestPreviewSize viewWid=" + viewWid + " viewHei=" + viewHei);
        Camera.Size resultSize = null;
        int deltaArea = 0;
        for (int i = 0; i < length; i++) {
            Camera.Size size = sizeList.get(i);
            final int area = size.width * size.height;
            final int delta = Math.abs(area - viewArea);
            if (deltaArea == 0 || delta < deltaArea) {
                deltaArea = delta;
                resultSize = size;
            }
        }
        return resultSize;
    }


    private boolean setUpCamera(boolean needPreview) {
        try {
            if (mCamera == null) {
                int numberOfCameras = Camera.getNumberOfCameras();
                if (numberOfCameras > 0) {
                    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                    for (int i = 0; i < numberOfCameras; i++) {
                        Camera.getCameraInfo(i, cameraInfo);
                        if (cameraInfo.facing == mConfig.getCameraType()) {
                            mCamera = Camera.open(i);
                            currentCameraId = i;
                        }
                    }
                } else {
                    mCamera = Camera.open();
                }
                if (mCamera == null) {
                    return false;
                }
                displayOrientation = CameraUtil.getDisplayOrientation(0, currentCameraId);
                KsyRecordClientConfig.previewOrientation = displayOrientation;
                Log.d(TAG, "current displayOrientation = " + displayOrientation);
                mCamera.setDisplayOrientation(displayOrientation);
                Camera.Parameters parameters = mCamera.getParameters();
                if (mCameraSizeChangedListener != null)
                    mCameraSizeChangedListener.onCameraPreviewSize(parameters.getPreviewSize().width, parameters.getPreviewSize().height);
                parameters.setRotation(0);
                if (parameters.getSupportedFocusModes().contains(
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                Camera.Size size = findBestPreviewSize(mCamera, (mSurfaceView == null ? mTextureView : mSurfaceView));
                if (size != null) {
                    parameters.setPreviewSize(size.width, size.height);
                }
                mCamera.setParameters(parameters);
                if (needPreview) {
                    try {
                        if (mSurfaceView != null) {
                            mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                        } else if (mTextureView != null) {
                            ((CameraTextureView) mTextureView).setPreviewSize(size);
                            mCamera.setPreviewTexture(mTextureView.getSurfaceTexture());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }
            // Here we reuse camera, just unlock it
            mCamera.unlock();
        } catch (Exception e) {
            onClientError(SOURCE_CLIENT, ERROR_CAMERA_START_FAILED);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void setUpEncoder() {
        switch (mEncodeMode) {
            case Constants.ENCODE_MODE_MEDIA_RECORDER:
                DealWithMediaRecorder();
                break;
            case Constants.ENCODE_MODE_MEDIA_CODEC:
                DealWithMediaCodec();
                break;
            case Constants.ENCODE_MODE_WEBRTC:
                DealWithWebRTC();
                break;
            default:
                break;
        }
    }

    // Encode using MediaRecorder
    private void DealWithMediaRecorder() {
        Log.d(Constants.LOG_TAG, "DealWithMediaRecorder");
        // Video Source
        if (mVideoSource == null) {
            mVideoSource = new RecoderVideoSource(mCamera, mConfig, mSurfaceView, mRecordHandler, mContext);
            mVideoSource.setOnClientErrorListener(this);
            mVideoSource.start();
        }
        // Audio Source
        if (mAudioSource == null) {
            mAudioSource = new RecoderAudioSource(mConfig, mRecordHandler, mContext);
            mAudioSource.setOnClientErrorListener(this);
            mAudioSource.start();
        }

    }

    // Encode using MediaCodec
    // to do
    private void DealWithMediaCodec() {
        Log.d(Constants.LOG_TAG, "DealWithMediaCodec");

    }

    // Encode using WebRTC
    // to do
    private void DealWithWebRTC() {
        Log.d(Constants.LOG_TAG, "DealWithWebRTC");

    }

    private int judgeEncodeMode(Context context) {
        // to do
        return Constants.ENCODE_MODE_MEDIA_RECORDER;
    }


    @Override
    public boolean stopRecord() {
        if (clientState != STATE.RECORDING) {
            return false;
        }
        if (mVideoSource != null) {
            mVideoSource.stop();
            mVideoSource = null;
        }
        if (mVideoTempSource != null) {
            mVideoTempSource.stop();
            mVideoTempSource = null;
        }
        if (mAudioSource != null) {
            mAudioSource.stop();
            mAudioSource = null;
        }
        if (mCamera != null) {
            try {
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mCamera = null;
        }
        ksyRecordSender.disconnect();
        clientState = STATE.STOP;
        isCanTurnLightFlag = false;
        mSwitchCameraLock = false;
        return true;
    }

    @Override
    public void release() {
        if (mVideoSource != null) {
            mVideoSource.release();
            mVideoSource = null;
        }
        if (mVideoTempSource != null) {
            mVideoTempSource.release();
            mVideoTempSource = null;
        }
        if (mAudioSource != null) {
            mAudioSource.release();
            mAudioSource = null;
        }
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        isCanTurnLightFlag = false;
    }

    @Override
    public void switchCamera() {
        if (!mSwitchCameraLock && clientState == STATE.RECORDING) {
            turnLight(false);
            mSwitchCameraLock = true;
            isCanTurnLightFlag = false;
            if (mSwitchCameraStateListener != null) {
                mSwitchCameraStateListener.onSwitchCameraDisable();
            }
            if (mVideoSource != null) {
                mVideoSource.close();
                mVideoSource = null;
            }
            if (mVideoTempSource != null) {
                mVideoTempSource.release();
                mVideoTempSource = null;
            }
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            }
            if (mConfig.getCameraType() == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mConfig.setmCameraType(Camera.CameraInfo.CAMERA_FACING_FRONT);
            } else {
                mConfig.setmCameraType(Camera.CameraInfo.CAMERA_FACING_BACK);
            }
            RecoderVideoSource.sync.setForceSyncFlay(true);
            startRecordStep();
            KsyRecordSender.getRecordInstance().needResetTs = true;
        } else {
            //current is switching
        }
    }

    @Override
    public int getNewtWorkStatusType() {
        return 0;
    }

    @Override
    public void setDisplayPreview(SurfaceView surfaceView) {
        if (mConfig == null) {
            throw new IllegalStateException("should set KsyRecordConfig before invoke setDisplayPreview");
        }
        this.mSurfaceView = surfaceView;
        this.mTextureView = null;
    }

    @Override
    public void setDisplayPreview(TextureView textureView) {
        if (mConfig == null) {
            throw new IllegalStateException("should set KsyRecordConfig before invoke setDisplayPreview");
        }
        this.mTextureView = textureView;
        this.mSurfaceView = null;
    }

    @Override
    public SenderStatData getSenderStatData() {
        return ksyRecordSender.getStatData();
    }

    public class RecordHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Constants.MESSAGE_MP4CONFIG_FINISH:
                    //release();
                    // just release tem
                    startRecordStep();
                    break;
                case Constants.MESSAGE_MP4CONFIG_START_PREVIEW:
                    break;
                case Constants.MESSAGE_SWITCH_CAMERA_FINISH:
                    isCanTurnLightFlag = true;
                    if (mSwitchCameraLock) {
                        mSwitchCameraLock = false;
                        if (mSwitchCameraStateListener != null) {
                            mSwitchCameraStateListener.onSwitchCameraEnable();
                        }

                    }
                    break;
                case Constants.MESSAGE_SENDER_PUSH_FAILED:
                    if (mPushStreamStateListener != null) {
                        mPushStreamStateListener.onPushStreamState(Constants.PUSH_STATE_FAILED);
                    }
                    break;
                case StartListener.START_COMPLETE:
                    if (startListener != null) {
                        startListener.OnStartComplete();
                    }
                    break;
                case StartListener.START_FAILED:
                    if (startListener != null) {
                        startListener.OnStartFailed();
                    }
                    break;
                case NetworkMonitor.OnNetworkPoorListener.CACHE_QUEUE_MAX:
                case NetworkMonitor.OnNetworkPoorListener.FRAME_SEND_TOO_LONG:
                    if (onNetworkPoorListener != null) {
                        onNetworkPoorListener.onNetworkPoor(msg.what);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public static void setConfig(KsyRecordClientConfig mConfig) {
        KsyRecordClient.mConfig = mConfig;
    }

    public static KsyRecordClientConfig getConfig(){
        return mConfig;
    }

}


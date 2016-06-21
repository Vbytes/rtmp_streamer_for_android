package com.ksy.recordlib.service.core;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;

import com.ksy.recordlib.service.exception.KsyRecordException;
import com.ksy.recordlib.service.util.CameraUtil;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.OrientationActivity;

/**
 * Created by eflakemac on 15/6/17.
 */
public class KsyRecordClientConfig {

    public static final int MEDIA_TEMP = 1;
    public static final int MEDIA_SETP = 2;

    int mCameraType;
    int mVoiceType;
    int mAudioSampleRate;
    int mAudioBitRate;
    int mAudioEncoder;
    int mVideoFrameRate;
    int mVideoBitRate;
    int mDropFrameFrequency;
    int mVideoWidth;
    int mVideoHeight;
    int mVideoEncoder;
    int mVideoProfile;
    int cameraOriention;
    public static int recordOrientation;
    public static int previewOrientation;

    String mUrl;

    OrientationActivity orientationActivity;

    public int getRecordOrientation() {
        return recordOrientation;
    }

    public KsyRecordClientConfig(Builder builder) {
        mCameraType = builder.mCameraType;
        mVoiceType = builder.mVoiceType;
        mAudioSampleRate = builder.mAudioSampleRate;
        mAudioBitRate = builder.mAudioBitRate;
        mAudioEncoder = builder.mAudioEncorder;
        mDropFrameFrequency = builder.mDropFrameFrequency;
        mVideoEncoder = builder.mVideoEncorder;
        mVideoProfile = builder.mVideoProfile;
        mUrl = builder.mUrl;
        if (mVideoProfile >= 0) {
            int cameraId = -1;
            int numberOfCameras = Camera.getNumberOfCameras();
            if (numberOfCameras > 0) {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == mCameraType) {
                        cameraId = i;
                        break;
                    }
                }
            }
            if (cameraId < 0) {
                throw new IllegalArgumentException("camera unsupported quality level");
            }
            if (CamcorderProfile.hasProfile(cameraId, mVideoProfile)) {
                CamcorderProfile camcorderProfile = CamcorderProfile.get(cameraId, mVideoProfile);
                this.mVideoFrameRate = camcorderProfile.videoFrameRate;
                this.mVideoBitRate = camcorderProfile.videoBitRate;
                this.mVideoWidth = camcorderProfile.videoFrameWidth;
                this.mVideoHeight = camcorderProfile.videoFrameHeight;
            }
        }
        mVideoFrameRate = builder.mVideoFrameRate;
        mVideoBitRate = builder.mVideoBitRate;
        mVideoWidth = builder.mVideoWidth > 0 ? builder.mVideoWidth : mVideoWidth;
        mVideoHeight = builder.mVideoHeigh > 0 ? builder.mVideoHeigh : mVideoHeight;
    }

    public int getCameraType() {
        return mCameraType;
    }

    public int getVoiceType() {
        return mVoiceType;
    }

    public int getAudioSampleRate() {
        return mAudioSampleRate;
    }

    public int getAudioBitRate() {
        return mAudioBitRate;
    }

    public int getAudioEncoder() {
        return mAudioEncoder;
    }

    public int getVideoFrameRate() {
        return mVideoFrameRate;
    }

    public int getVideoBitRate() {
        return mVideoBitRate;
    }

    public int getDropFrameFrequency() {
        return mDropFrameFrequency;
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public int getVideoEncoder() {
        return mVideoEncoder;
    }

    public int getVideoProfile() {
        return mVideoProfile;
    }

    public String getUrl() {
        return mUrl;
    }

    public KsyRecordClientConfig setmCameraType(int mCameraType) {
        this.mCameraType = mCameraType;
        return this;
    }

    public KsyRecordClientConfig setmVoiceType(int mVoiceType) {
        this.mVoiceType = mVoiceType;
        return this;
    }

    public KsyRecordClientConfig setmAudioSampleRate(int mAudioSampleRate) {
        this.mAudioSampleRate = mAudioSampleRate;
        return this;
    }

    public KsyRecordClientConfig setmAudioBitRate(int mAudioBitRate) {
        this.mAudioBitRate = mAudioBitRate;
        return this;
    }

    public KsyRecordClientConfig setmAudioEncoder(int mAudioEncoder) {
        this.mAudioEncoder = mAudioEncoder;
        return this;
    }

    public KsyRecordClientConfig setmVideoFrameRate(int mVideoFrameRate) {
        this.mVideoFrameRate = mVideoFrameRate;
        return this;
    }

    public KsyRecordClientConfig setmVideoBitRate(int mVideoBitRate) {
        this.mVideoBitRate = mVideoBitRate;
        return this;
    }

    public KsyRecordClientConfig setmDropFrameFrequency(int mDropFrameFrequency) {
        this.mDropFrameFrequency = mDropFrameFrequency;
        return this;
    }

    public KsyRecordClientConfig setmVideoWidth(int mVideoWidth) {
        this.mVideoWidth = mVideoWidth;
        return this;
    }

    public KsyRecordClientConfig setmVideoHeight(int mVideoHeight) {
        this.mVideoHeight = mVideoHeight;
        return this;
    }

    public KsyRecordClientConfig setmVideoEncoder(int mVideoEncoder) {
        this.mVideoEncoder = mVideoEncoder;
        return this;
    }

    public KsyRecordClientConfig setmVideoProfile(int mVideoProfile) {
        this.mVideoProfile = mVideoProfile;
        return this;
    }

    public KsyRecordClientConfig setmUrl(String mUrl) {
        this.mUrl = mUrl;
        return this;
    }

    public boolean validateParam() throws KsyRecordException {
        //to do
        return true;
    }

    public KsyRecordClientConfig setOrientationActivity(OrientationActivity orientationActivity) {
        this.orientationActivity = orientationActivity;
        return this;
    }

    public void configMediaRecorder(MediaRecorder mediaRecorder, int type) {
        if (mediaRecorder == null) {
            throw new IllegalArgumentException("mediaRecorder is null");
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        if (type == MEDIA_SETP) {
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        } else if (type == MEDIA_TEMP) {
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        }
        mediaRecorder.setVideoEncoder(mVideoEncoder);
        int cameraId = -1;
        if (mVideoProfile >= 0) {

            int numberOfCameras = Camera.getNumberOfCameras();
            if (numberOfCameras > 0) {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == mCameraType) {
                        cameraId = i;
                        break;
                    }
                }
            }
            if (cameraId < 0) {
                throw new IllegalArgumentException("camera unsupported quality level");
            }
            if (CamcorderProfile.hasProfile(cameraId, mVideoProfile)) {
                CamcorderProfile camcorderProfile = CamcorderProfile.get(cameraId, mVideoProfile);
                mediaRecorder.setVideoFrameRate(camcorderProfile.videoFrameRate);
                mediaRecorder.setVideoEncodingBitRate(camcorderProfile.videoBitRate);
                mediaRecorder.setVideoSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
            }
        }
        if (mVideoBitRate > 0) {
            mediaRecorder.setVideoEncodingBitRate(mVideoBitRate);
        }
        if (mVideoFrameRate > 0) {
//            mediaRecorder.setCaptureRate(24);
        }
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            mediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);
        }
        if (orientationActivity != null) {
            int previewDegree = CameraUtil.getDisplayOrientation(orientationActivity.getActivity(), cameraId, mCameraType == Camera.CameraInfo.CAMERA_FACING_FRONT);
            recordOrientation = CameraUtil.getMediaRecordRotation(previewDegree, orientationActivity.getOrientation(), mCameraType == Camera.CameraInfo.CAMERA_FACING_FRONT);
        } else {
            recordOrientation = 90;
        }
        mediaRecorder.setOrientationHint(recordOrientation);
    }

    public static class Builder {
        private int mCameraType = Constants.CONFIG_CAMERA_TYPE_BACK;
        private int mVoiceType = Constants.CONFIG_VOICE_TYPE_MIC;
        private int mAudioSampleRate = Constants.CONFIG_AUDIO_SAMPLERATE_44100;
        private int mAudioBitRate = Constants.CONFIG_AUDIO_BITRATE_32K;
        private int mAudioEncorder = MediaRecorder.AudioEncoder.AAC;
        private int mVideoFrameRate = Constants.CONFIG_VIDEO_FRAME_RATE_30;
        private int mVideoBitRate = Constants.CONFIG_VIDEO_BITRATE_750K;
        private int mDropFrameFrequency = 0;
        private int mVideoWidth;
        private int mVideoHeigh;
        private int mVideoEncorder = MediaRecorder.VideoEncoder.H264;
        private int mVideoProfile = -1;
        private String mUrl;

        public KsyRecordClientConfig build() {
            return new KsyRecordClientConfig(this);
        }

        public String getmUrl() {
            return mUrl;
        }

        public Builder setUrl(String mUrl) {
            this.mUrl = mUrl;
            return this;
        }

        public int getCameraType() {
            return mCameraType;
        }

        public Builder setCameraType(int mCameraType) {
            this.mCameraType = mCameraType;
            return this;
        }

        public int getVoiceType() {
            return mVoiceType;
        }

        public Builder setVoiceType(int mVoiceType) {
            this.mVoiceType = mVoiceType;
            return this;
        }

        public int getAudioSampleRate() {
            return mAudioSampleRate;
        }

        public Builder setAudioSampleRate(int mAudioSampleRate) {
            this.mAudioSampleRate = mAudioSampleRate;
            return this;
        }

        public int getAudioBitRate() {
            return mAudioBitRate;
        }

        public Builder setAudioBitRate(int mAudioBitRate) {
            this.mAudioBitRate = mAudioBitRate;
            return this;
        }

        public int getAudioEncorder() {
            return mAudioEncorder;
        }

        public Builder setAudioEncorder(int mAudioEncorder) {
            this.mAudioEncorder = mAudioEncorder;
            return this;
        }

        public int getVideoFrameRate() {
            return mVideoFrameRate;
        }

        public Builder setVideoFrameRate(int mVideoFrameRate) {
            this.mVideoFrameRate = mVideoFrameRate;
            return this;
        }

        public int getVideoBitRate() {
            return mVideoBitRate;
        }

        public Builder setVideoBitRate(int mVideoBitRate) {
            this.mVideoBitRate = mVideoBitRate;
            return this;
        }

        public int getDropFrameFrequency() {
            return mDropFrameFrequency;
        }

        public Builder setDropFrameFrequency(int mDropFrameFrequency) {
            this.mDropFrameFrequency = mDropFrameFrequency;
            return this;
        }

        public int getVideoWidth() {
            return mVideoWidth;
        }

        public Builder setVideoWidth(int mVideoWidth) {
            this.mVideoWidth = mVideoWidth;
            return this;
        }

        public int getVideoHeigh() {
            return mVideoHeigh;
        }

        public Builder setVideoHeigh(int mVideoHeigh) {
            this.mVideoHeigh = mVideoHeigh;
            return this;
        }

        public int getVideoEncorder() {
            return mVideoEncorder;
        }

        public Builder setVideoEncorder(int mVideoEncorder) {
            this.mVideoEncorder = mVideoEncorder;
            return this;
        }

        public int getVideoProfile() {
            return mVideoProfile;
        }

        public Builder setVideoProfile(int mVideoProfile) {
            this.mVideoProfile = mVideoProfile;
            return this;
        }
    }


}

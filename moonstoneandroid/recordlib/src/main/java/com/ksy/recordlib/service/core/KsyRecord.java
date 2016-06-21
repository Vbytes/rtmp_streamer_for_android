package com.ksy.recordlib.service.core;

import android.view.SurfaceView;
import android.view.TextureView;

import com.ksy.recordlib.service.data.SenderStatData;
import com.ksy.recordlib.service.exception.KsyRecordException;

/**
 * Created by eflakemac on 15/6/17.
 * <p/>
 * Interface provider for app
 */
public interface KsyRecord {
    void startRecord() throws KsyRecordException;

    /**
     * stop recorder
     *
     * @return true is success
     */
    boolean stopRecord();

    void release();

    void switchCamera();

//    void setCameraType(int cameraType);
//
//    void setVoiceType(int voiceType);
//
//    void setAudioEncodeConfig(int audioSampleRate, int audioBitRate);
//
//    void setVideoEncodeConfig(int videoFrameRate,int videoBitRate);
//
//    void setVideoResolution(int vResolutionType);
//
//    void setUrl(String url);

    int getNewtWorkStatusType();

//    void setDropFrameFrequency(int frequency);

    /**
     * @param surfaceView
     * @deprecated use {@link KsyRecord#setDisplayPreview(TextureView)} instead avoid preview distortion
     */
    void setDisplayPreview(SurfaceView surfaceView);

    void setDisplayPreview(TextureView textureView);

    SenderStatData getSenderStatData();
}

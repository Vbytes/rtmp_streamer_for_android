package com.ksy.ksyrecordsdk.com.ksy.ksyrecordsdk.config;


import com.ksy.recordlib.service.core.CameraHelper;
import com.ksy.recordlib.service.core.KsyRecordClientConfig;
import com.ksy.recordlib.service.util.Constants;

/**
 * Created by hansentian on 7/17/15.
 */
public class ConfigItem {
    public int index;
    public String configName;
    public int[] configValue;
    public String[] configValueName;


    public int currentValue(KsyRecordClientConfig config) {
        int currentValueString = 0;
        switch (index) {
            case Constants.SETTING_AUDIO_SAMPLE_RATE:
                currentValueString = config.getAudioSampleRate();
                break;
            case Constants.SETTING_AUDIO_BITRATE:
                currentValueString = config.getAudioBitRate();
                break;
            case Constants.SETTING_VIDEO_BITRATE:
                currentValueString = config.getVideoBitRate();
                break;
            case Constants.SETTING_CAMERY_TYPE:
                currentValueString = config.getCameraType();
                break;
            case Constants.SETTING_VIDEO_SIZE:
                currentValueString = CameraHelper.cameraSizeToInt(config.getVideoWidth(), config.getVideoHeight());
                break;
        }
        return currentValueString;
    }

    public String currentValueString(KsyRecordClientConfig config) {
        if (config == null) {
            return null;
        }
        String currentValue = "not set";
        switch (index) {
            case Constants.SETTING_AUDIO_SAMPLE_RATE:
                currentValue = config.getAudioSampleRate() + "Hz";
                break;
            case Constants.SETTING_AUDIO_BITRATE:
                currentValue = config.getAudioBitRate() / 1000 + "Kbps";
                break;
            case Constants.SETTING_VIDEO_BITRATE:
                currentValue = config.getVideoBitRate() / 1000 + "Kbps";
                break;
            case Constants.SETTING_CAMERY_TYPE:
                currentValue = config.getCameraType() == Constants.CONFIG_CAMERA_TYPE_BACK ? "back" : "front";
                break;
            case Constants.SETTING_VIDEO_SIZE:
                currentValue = config.getVideoWidth() + "x" + config.getVideoHeight();
                break;
            case Constants.SETTING_URL:
                currentValue = "click to set";
                break;
        }
        return currentValue;

    }

    public void changeValue(KsyRecordClientConfig config, int selected, String value) {
        switch (index) {
            case Constants.SETTING_AUDIO_SAMPLE_RATE:
                config.setmAudioSampleRate(configValue[selected]);
                break;
            case Constants.SETTING_AUDIO_BITRATE:
                config.setmAudioBitRate(configValue[selected]);
                break;
            case Constants.SETTING_VIDEO_BITRATE:
                config.setmVideoBitRate(configValue[selected]);
                break;
            case Constants.SETTING_CAMERY_TYPE:
                config.setmCameraType(configValue[selected]);
                break;
            case Constants.SETTING_VIDEO_SIZE:
                config.setmVideoWidth(CameraHelper.intToCameraWidth(configValue[selected]));
                config.setmVideoHeight(CameraHelper.intToCameraHeight(configValue[selected]));
                break;
            case Constants.SETTING_URL:
                config.setmUrl(value);
                break;
        }
    }

}

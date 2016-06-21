package com.ksy.recordlib.service.core;

import android.media.MediaRecorder;

import com.ksy.recordlib.service.util.Constants;

import java.util.ArrayList;

/**
 * Created by tianyu on 15-12-29.
 * This class is able to generate a FLVTAG in accordance with Adobe Flash Video File Format
 * Specification v10.1 Annex E.5 with limited types available.
 */
public class FLvMetaData {
    private static final String Name = "onMetaData";
    private static final int ScriptData = 18;
    private static final byte[] TS_SID = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] ObjEndMarker = {0x00, 0x00, 0x09};
    private static final int EmptySize = 32;
    private ArrayList<byte[]> MetaData;
    private int DataSize;
    private int pointer;
    private byte[] MetaDataFrame;

    public FLvMetaData() {
        MetaData = new ArrayList<>();
        DataSize = 0;
    }

    public FLvMetaData(KsyRecordClientConfig builder) {
        this();
        //Audio
        switch (builder.getAudioEncoder()) {
            case MediaRecorder.AudioEncoder.AAC:
                setProperty("audiocodecid", 10);
                break;
            default:
                break;
        }
        switch (builder.getAudioBitRate()) {
            case Constants.CONFIG_AUDIO_BITRATE_32K:
                setProperty("audiodatarate", 32);
                break;
            case Constants.CONFIG_AUDIO_BITRATE_48K:
                setProperty("audiodatarate", 48);
                break;
            case Constants.CONFIG_AUDIO_BITRATE_64K:
                setProperty("audiodatarate", 64);
                break;
            default:
                break;
        }
        switch (builder.getAudioSampleRate()) {
            case Constants.CONFIG_AUDIO_SAMPLERATE_44100:
                setProperty("audiosamplerate", 44100);
                break;
            default:
                break;
        }
        //Video
        switch (builder.getVideoEncoder()) {
            case MediaRecorder.VideoEncoder.H264:
                setProperty("videocodecid", 7);
                break;
            default:
                break;
        }
        switch (builder.getVideoFrameRate()) {
            case Constants.CONFIG_VIDEO_FRAME_RATE_10:
                setProperty("framerate", 10);
                break;
            case Constants.CONFIG_VIDEO_FRAME_RATE_15:
                setProperty("framerate", 15);
                break;
            case Constants.CONFIG_VIDEO_FRAME_RATE_21:
                setProperty("framerate", 21);
                break;
            case Constants.CONFIG_VIDEO_FRAME_RATE_30:
                setProperty("framerate", 30);
                break;
        }
        setProperty("width",builder.getVideoWidth());
        setProperty("height",builder.getVideoHeight());
    }

    public void setProperty(String Key, int value) {
        addProperty(toFlvString(Key), (byte) 0, toFlvNum(value));
    }

    public void setProperty(String Key, String value) {
        addProperty(toFlvString(Key), (byte) 2, toFlvString(value));
    }

    private void addProperty(byte[] Key, byte datatype, byte[] data) {
        int Propertysize = Key.length + 1 + data.length;
        byte[] Property = new byte[Propertysize];

        System.arraycopy(Key, 0, Property, 0, Key.length);
        Property[Key.length] = datatype;
        System.arraycopy(data, 0, Property, Key.length + 1, data.length);

        MetaData.add(Property);
        DataSize += Propertysize;
    }

    public byte[] getMetaData() {
        MetaDataFrame = new byte[DataSize + EmptySize + 4];
        pointer = 0;
        Addbyte(ScriptData);
        AddbyteArray(toUI(DataSize + EmptySize - 11, 3));
        AddbyteArray(TS_SID);
        //SCRIPTDATA.name
        Addbyte(2);
        AddbyteArray(toFlvString(Name));
        //SCRIPTDATA.value ECMA array
        Addbyte(8);
        AddbyteArray(toUI(MetaData.size(), 4));
        for (byte[] Property : MetaData) {
            AddbyteArray(Property);
        }
        AddbyteArray(ObjEndMarker);
        //PreviousTagSize
        AddbyteArray(toUI(DataSize + EmptySize, 4));
        return MetaDataFrame;
    }

    private void Addbyte(int value) {
        MetaDataFrame[pointer] = (byte) value;
        pointer++;
    }

    private void AddbyteArray(byte[] value) {
        System.arraycopy(value, 0, MetaDataFrame, pointer, value.length);
        pointer += value.length;
    }

    private byte[] toFlvString(String text) {
        byte[] FlvString = new byte[text.length() + 2];
        System.arraycopy(toUI(text.length(), 2), 0, FlvString, 0, 2);
        System.arraycopy(text.getBytes(), 0, FlvString, 2, text.length());
        return FlvString;
    }

    private byte[] toUI(long value, int bytes) {
        byte[] UI = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            UI[bytes - 1 - i] = (byte) (value >> (8 * i) & 0xff);
        }
        return UI;
    }

    private byte[] toFlvNum(double value) {
        long tmp = Double.doubleToLongBits(value);
        return toUI(tmp, 8);
    }
}

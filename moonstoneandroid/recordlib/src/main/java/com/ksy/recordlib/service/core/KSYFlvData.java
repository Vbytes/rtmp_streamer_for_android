package com.ksy.recordlib.service.core;

/**
 * Created by lixiaopeng on 15/7/6.
 */
public class KSYFlvData {

    public final static int FLV_TYPE_VIDEO = 11;
    public final static int FLV_TYTPE_AUDIO = 12;
    public final static int NALU_TYPE_IDR = 5;


    public int dts;//解码时间戳

    public byte[] byteBuffer; //数据

    public int size; //字节长度

    public int type; //视频和音频的分类

    public int frameType;

    public boolean isKeyframe() {
        return frameType == NALU_TYPE_IDR;
    }

}

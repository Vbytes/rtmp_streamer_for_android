package com.ksy.recordlib.service.recoder;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.ksy.recordlib.service.core.KSYFlvData;
import com.ksy.recordlib.service.core.KsyMediaSource;
import com.ksy.recordlib.service.core.KsyRecordClient;
import com.ksy.recordlib.service.core.KsyRecordClientConfig;
import com.ksy.recordlib.service.core.KsyRecordSender;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.OnClientErrorListener;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * Created by eflakemac on 15/6/19.
 */
public class RecoderAudioSource extends KsyMediaSource implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
    private static final int FRAME_DEFINE_TYPE_AUDIO = 8;
    private KsyRecordClient.RecordHandler mRecordHandler;
    private Context mContext;
    private KsyRecordClientConfig mConfig;
    private MediaRecorder mRecorder;
    private ParcelFileDescriptor[] piple;
    private long oldTime = 0;
    private long duration = 0;

    private double delay = 0;
    private int length;
    private byte[] content;
    private int sum;
    private int last_sum = 0;
    private byte[] flvFrameByteArray;
    private byte[] dataLengthArray;
    private byte[] timestampArray;
    private byte[] allFrameLengthArray;
    private int videoExtraSize = 2;
    private static final int FRAME_DEFINE_HEAD_LENGTH = 11;
    private static final int FRAME_DEFINE_FOOTER_LENGTH = 4;
    private static final int AUDIO_TAG = 0;

    private byte[] judge_buffer;
    private boolean isWaitingParse = false;
    private boolean isNeedLoop = true;
    private int recordsum = 0;
    private byte[] buffer = new byte[100 * 1000];
    private boolean isWriteFlvInSdcard = false;
    private byte[] special_content;
    private boolean isSpecialFrame = true;
    private byte aac_flag = (byte) 0xA2;

    private static final int FROM_AUDIO_DATA = 8;
    private KsyRecordSender ksyRecordSender;
    private Handler delayHandler = new Handler();
    private boolean isFirstDelay = false;

    public static long startAudioTime;

    public RecoderAudioSource(KsyRecordClientConfig mConfig, KsyRecordClient.RecordHandler mRecordHandler, Context mContext) {
//        super(mConfig.getUrl(), AUDIO_TAG);//TODO
        this.mConfig = mConfig;
        this.mRecordHandler = mRecordHandler;
        this.mContext = mContext;
        mRecorder = new MediaRecorder();
        ksyRecordSender = KsyRecordSender.getRecordInstance();
    }

    @Override
    public void prepare() {
        mRecorder.setOnErrorListener(this);
        mRecorder.setOnInfoListener(this);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        mRecorder.setAudioChannels(2);
        mRecorder.setAudioSamplingRate(mConfig.getAudioSampleRate());
        mRecorder.setAudioEncodingBitRate(mConfig.getAudioBitRate());
        mRecorder.setAudioEncoder(mConfig.getAudioEncoder());
        delay = 1024 * 1000 / mConfig.getAudioSampleRate();
        aac_flag = (byte) 0xAF;

        try {
            this.piple = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
            release();
        }
        mRecorder.setOutputFile(this.piple[1].getFileDescriptor());
        try {
            mRecorder.prepare();
            mRecorder.start();
            startAudioTime = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
            release();
            if (onClientErrorListener != null) {
                onClientErrorListener.onClientError(OnClientErrorListener.SOURCE_AUDIO, OnClientErrorListener.ERROR_MEDIACODER_START_FAILED);
            }
        }
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
            release();
        }
    }

    @Override
    public void release() {
        mRunning = false;
        releaseRecorder();
    }

    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.setOnErrorListener(null);
            mRecorder.setOnInfoListener(null);
            mRecorder.reset();
            Log.d(Constants.LOG_TAG, "mRecorder reset");
            mRecorder.release();
            Log.d(Constants.LOG_TAG, "mRecorder release");
            mRecorder = null;
            Log.d(Constants.LOG_TAG, "mRecorder complete");
        }
    }

    @Override
    public void run() {
        prepare();
        if (mRunning) {
            is = new FileInputStream(this.piple[0].getFileDescriptor());
        }
        while (mRunning) {
            // parse audio
            isNeedLoop = true;
            while (isNeedLoop) {
                judge_buffer = new byte[2];
                try {
                    fill(judge_buffer, 0, 2);
                    if (judge_buffer[0] == (byte) 0xff && (judge_buffer[1] & (byte) 0xF0) == (byte) 0xF0) {
                        isNeedLoop = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            oldTime = System.currentTimeMillis();
            // parse ADTS header
            int header_type = judge_buffer[1] & (byte) 0x01;
            int header_size = (header_type == 1 ? 7 : 9);
            byte[] header_buffer_rest = new byte[header_size - 2];
            try {
                fill(header_buffer_rest, 0, header_size - 2);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int length = ((header_buffer_rest[1] & 0x00000003) << 11) | (header_buffer_rest[2] << 3) | ((header_buffer_rest[3] >> 5) & (byte) 0x07);
            int profile = (header_buffer_rest[0] & (byte) 0xc0) >> 6;
            int sfi = (header_buffer_rest[0] & (byte) 0x3c) >> 2;
            int ch = ((header_buffer_rest[0] & (byte) 0x01) << 6) | ((header_buffer_rest[1] & (byte) 0xc0) >> 6);
            int x = (((profile + 1) & 0x1f) << 11) | ((sfi & 0x0f) << 7) | ((ch & 0x0f) << 3);
            special_content = intToByteArrayTwoByte(x);
            int frame_length = length - header_size;
            byte[] frame_content = new byte[frame_length];
            try {
                fill(frame_content, 0, frame_length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (isSpecialFrame) {
                //AAC_AudioSpecificConfig use 1210 as default temporary
                frame_content = new byte[]{
                        0x12, 0x10
                };
                frame_length = frame_content.length;
            }
            // make flv
            ts += delay;
            flvFrameByteArray = new byte[FRAME_DEFINE_HEAD_LENGTH + frame_length + videoExtraSize + FRAME_DEFINE_FOOTER_LENGTH];
            flvFrameByteArray[0] = (byte) FRAME_DEFINE_TYPE_AUDIO;
            dataLengthArray = intToByteArray(frame_length + videoExtraSize);
            flvFrameByteArray[1] = dataLengthArray[0];
            flvFrameByteArray[2] = dataLengthArray[1];
            flvFrameByteArray[3] = dataLengthArray[2];
            timestampArray = longToByteArray(ts);
            flvFrameByteArray[4] = timestampArray[1];
            flvFrameByteArray[5] = timestampArray[2];
            flvFrameByteArray[6] = timestampArray[3];
            flvFrameByteArray[7] = timestampArray[0];
            flvFrameByteArray[8] = (byte) 0;
            flvFrameByteArray[9] = (byte) 0;
            flvFrameByteArray[10] = (byte) 0;
            // add extra
            for (int i = 0; i < frame_length + videoExtraSize; i++) {
                if (i < videoExtraSize) {
                    if (i == 0) {
                        flvFrameByteArray[11 + i] = aac_flag;//(byte) 0xAF;
                    } else if (i == 1) {
                        if (isSpecialFrame) {
                            flvFrameByteArray[11 + i] = (byte) 0x00;
                            isSpecialFrame = false;

                        } else {
                            flvFrameByteArray[11 + i] = (byte) 0x01;
                            //isSpecialFrame = true;
                        }
                    } else {

                    }
                } else {
                    flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + videoExtraSize + i - videoExtraSize] = frame_content[i - videoExtraSize];
                }
            }
            allFrameLengthArray = intToByteArrayFull(FRAME_DEFINE_HEAD_LENGTH + frame_length + videoExtraSize + FRAME_DEFINE_FOOTER_LENGTH);
            flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + frame_length + videoExtraSize] = allFrameLengthArray[0];
            flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + frame_length + videoExtraSize + 1] = allFrameLengthArray[1];
            flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + frame_length + videoExtraSize + 2] = allFrameLengthArray[2];
            flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + frame_length + videoExtraSize + 3] = allFrameLengthArray[3];

            //添加音频数据到队列
            KSYFlvData ksyAudio = new KSYFlvData();
            ksyAudio.byteBuffer = flvFrameByteArray;
            ksyAudio.size = flvFrameByteArray.length;
            ksyAudio.dts = (int) ts;
            ksyAudio.type = 12;
            ksyRecordSender.addToQueue(ksyAudio, FROM_AUDIO_DATA);
            isSpecialFrame = false;
        }

    }

    //delay
    /*Runnable runnableAudioSend = new Runnable() {
        @Override
        public void run() {
            ksyRecordSender.addToQueue(ksyAudio, FROM_AUDIO_DATA);
            isFirstDelay = true;
        }
    };*/

    private void fillArray(byte[] sps_pps, byte[] target) {
        for (int i = 0; i < target.length; i++) {
            sps_pps[last_sum + i] = target[i];
        }
        last_sum += target.length;
    }

    private byte[] longToByteArray(long ts) {
        byte[] result = new byte[4];
//        result[0] = new Long(ts >> 56 & 0xff).byteValue();
//        result[1] = new Long(ts >> 48 & 0xff).byteValue();
//        result[2] = new Long(ts >> 40 & 0xff).byteValue();
//        result[3] = new Long(ts >> 32 & 0xff).byteValue();
        result[0] = new Long(ts >> 24 & 0xff).byteValue();
        result[1] = new Long(ts >> 16 & 0xff).byteValue();
        result[2] = new Long(ts >> 8 & 0xff).byteValue();
        result[3] = new Long(ts >> 0 & 0xff).byteValue();
        return result;
    }

    private byte[] intToByteArray(int length) {
        byte[] result = new byte[3];
//        result[0] = (byte) ((length >> 24) & 0xFF);
        result[0] = (byte) ((length >> 16) & 0xFF);
        result[1] = (byte) ((length >> 8) & 0xFF);
        result[2] = (byte) ((length >> 0) & 0xFF);
        return result;
    }

    private byte[] intToByteArrayTwoByte(int length) {
        byte[] result = new byte[2];
//        result[0] = (byte) ((length >> 24) & 0xFF);
//        result[0] = (byte) ((length >> 16) & 0xFF);
        result[0] = (byte) ((length >> 8) & 0xFF);
        result[1] = (byte) ((length >> 0) & 0xFF);
        return result;
    }

    private byte[] intToByteArrayFull(int length) {
        byte[] result = new byte[4];
        result[0] = (byte) ((length >> 24) & 0xFF);
        result[1] = (byte) ((length >> 16) & 0xFF);
        result[2] = (byte) ((length >> 8) & 0xFF);
        result[3] = (byte) ((length >> 0) & 0xFF);
        return result;
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onError Message what = " + what + ",extra =" + extra);

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onInfo Message what = " + what + ",extra =" + extra);

    }

    private String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
            return sdDir.toString();
        }

        return null;
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

    private byte[] getBytes(char[] chars) {
        Charset cs = Charset.forName("UTF-8");
        CharBuffer cb = CharBuffer.allocate(chars.length);
        cb.put(chars);
        cb.flip();
        ByteBuffer bb = cs.encode(cb);
        return bb.array();
    }

    private char[] getChars(byte[] bytes) {
        Charset cs = Charset.forName("UTF-8");
        ByteBuffer bb = ByteBuffer.allocate(bytes.length);
        bb.put(bytes);
        bb.flip();
        CharBuffer cb = cs.decode(bb);
        return cb.array();
    }

    public byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
}

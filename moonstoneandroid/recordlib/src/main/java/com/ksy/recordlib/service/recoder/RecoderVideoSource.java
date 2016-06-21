package com.ksy.recordlib.service.recoder;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceView;

import com.ksy.recordlib.service.core.KSYFlvData;
import com.ksy.recordlib.service.core.KsyMediaSource;
import com.ksy.recordlib.service.core.KsyRecordClient;
import com.ksy.recordlib.service.core.KsyRecordClientConfig;
import com.ksy.recordlib.service.core.KsyRecordSender;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.OnClientErrorListener;
import com.ksy.recordlib.service.util.PrefUtil;

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
public class RecoderVideoSource extends KsyMediaSource implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {

    byte SEI_ROTATION_0[] = {0x00, 0x00, 0x03, 0x08, 0x00, 0x0A, -128};
    byte SEI_ROTATION_90[] = {0x66, 0x2F, 0x03, 0x08, 0x00, 0x0A, -128};
    byte SEI_ROTATION_180[] = {0x66, 0x2F, 0x03, 0x10, 0x00, 0x0A, -128};
    byte SEI_ROTATION_270[] = {0x66, 0x2F, 0x03, 0x18, 0x00, 0x0A, -128};

    private static final int FRAME_TYPE_SPS = 0;
    private static final int FRAME_TYPE_DATA = 2;
    private static final int FRAME_DEFINE_TYPE_VIDEO = 9;
    private static final int FRAME_DEFINE_HEAD_LENGTH = 11;
    private static final int FRAME_DEFINE_FOOTER_LENGTH = 4;
    private final KsyRecordClient.RecordHandler mHandler;
    private final Context mContext;
    private Camera mCamera;
    private MediaRecorder mRecorder;
    private KsyRecordClientConfig mConfig;
    private ParcelFileDescriptor[] piple;
    //    private long delay = 0;
    private int length;
    private int nalutype;
    private String pps;
    private String sps;
    private String pl;
    //    private int sum = 0;
    private boolean isSpsFrameSended = false;
    private ByteBuffer content;
    private byte[] flvFrameByteArray;
    private byte[] dataLengthArray;
    private byte[] timestampArray;
    private byte[] allFrameLengthArray;
    private int videoExtraSize = 5;
    private int last_sum = 0;

    private static final int VIDEO_TAG = 3;
    private static final int FROM_VIDEO_DATA = 6;
    private KsyRecordSender ksyVideoSender;

    private Byte kFlag;

    public static long startVideoTime;


    public RecoderVideoSource(Camera mCamera, KsyRecordClientConfig mConfig, SurfaceView mSurfaceView, KsyRecordClient.RecordHandler mRecordHandler, Context mContext) {
        this.mCamera = mCamera;
        this.mConfig = mConfig;
        mRecorder = new MediaRecorder();
        mHandler = mRecordHandler;
        this.mContext = mContext;

        ksyVideoSender = KsyRecordSender.getRecordInstance();
        Log.d(Constants.LOG_TAG, "test");
    }

    @Override
    public void prepare() {
        mRecorder.setCamera(mCamera);
        mConfig.configMediaRecorder(mRecorder, KsyRecordClientConfig.MEDIA_SETP);
        try {
            this.piple = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            e.printStackTrace();
            release();
        }
//        delay = 1000 / 20;
        mRecorder.setOutputFile(this.piple[1].getFileDescriptor());
        try {
            mRecorder.setOnInfoListener(this);
            mRecorder.setOnErrorListener(this);
            mRecorder.prepare();
            mRecorder.start();
            startVideoTime = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
            release();
            if (onClientErrorListener != null) {
                onClientErrorListener.onClientError(OnClientErrorListener.SOURCE_VIDEO, OnClientErrorListener.ERROR_MEDIACODER_START_FAILED);
            }
        }
        mHandler.sendEmptyMessage(Constants.MESSAGE_SWITCH_CAMERA_FINISH);
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
        releaseCamera();
        sync.clear();
    }

    public void close() {
        mRunning = false;
        releaseRecorder();
        releaseCamera();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
            inputChannel = is.getChannel();
        }
        while (mRunning) {
            Log.d(Constants.LOG_TAG, "entering video loop");
            // This will skip the MPEG4 header if this step fails we can't stream anything :(
            try {
                byte buffer[] = new byte[4];
                // Skip all atoms preceding mdat atom
                while (true) {  // box
                    while (is.read() != 'm') ;
                    is.read(buffer, 0, 3);
                    if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
                    //mdat
                }
            } catch (IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't skip mp4 header :/");
                return;
            }
            pl = PrefUtil.getMp4ConfigProfileLevel(mContext);
            pps = PrefUtil.getMp4ConfigPps(mContext);
            sps = PrefUtil.getMp4ConfigSps(mContext);

            while (!Thread.interrupted()) {
                // Begin parse video data
                parseAndSend();
               /* duration = System.currentTimeMillis() - oldTime;
                stats.push(duration);
                delay = stats.average();*/
//                delay = 33;
            }
        }
        Log.d(Constants.LOG_TAG, "exiting video loop");
    }


    private void parseAndSend() {
        if (content == null) {
            content = ByteBuffer.allocate(mConfig.getVideoBitRate() * 2);
        }
        if (isSpsFrameSended) {
            parseVideo();
        } else {
//            delay = startVideoTime - RecoderAudioSource.startAudioTime;
//            if (Math.abs(delay) > 2000) {
//                delay = 0;
//            }
            content.clear();
            // Step One ,insert in header,sps & pps prefix & data
            byte[] sps_prefix = hexStringToBytes("0142C028FFE1");
            byte[] sps_only = Base64.decode(sps.getBytes(), Base64.DEFAULT);
            byte[] sps_length = intToByteArrayTwoByte(sps_only.length);
            byte[] pps_prefix = hexStringToBytes("01");
            byte[] pps_only = Base64.decode(pps.getBytes(), Base64.DEFAULT);
            byte[] pps_length = intToByteArrayTwoByte(pps_only.length);
            // Remove SEI Here
//
//            int degree = mConfig.getRecordOrientation();
//            Log.e("degree", "mediarecord degree=" + degree);
//            byte[] sei_only = null;
//            byte[] sei_prefix = hexStringToBytes("01");
//            byte[] sei_length_bytes = intToByteArrayTwoByte(7);
//            int sei_length = sei_prefix.length + 7 + sei_length_bytes.length;
//
//            if (degree == 0) {
//                sei_length = 0;
//            } else if (degree == 90) {
//                sei_only = SEI_ROTATION_90;
//            } else if (degree == 180) {
//                sei_only = SEI_ROTATION_180;
//            } else if (degree == 270) {
//                sei_only = SEI_ROTATION_270;
//            }

//            byte[] sps_pps = new byte[sps_prefix.length + sps_length.length + sps_only.length + pps_prefix.length
//                    + pps_only.length + pps_length.length + sei_length];
            byte[] sps_pps = new byte[sps_prefix.length + sps_length.length + sps_only.length + pps_prefix.length
                    + pps_only.length + pps_length.length];
            fillArray(sps_pps, sps_prefix);
            fillArray(sps_pps, sps_length);
            fillArray(sps_pps, sps_only);
            fillArray(sps_pps, pps_prefix);
            fillArray(sps_pps, pps_length);
            fillArray(sps_pps, pps_only);
//            if (degree != 0) {
//                fillArray(sps_pps, sei_prefix);
//                fillArray(sps_pps, sei_length_bytes);
//                fillArray(sps_pps, sei_only);
//            }
            // build sps_pps end
            content.put(sps_pps);
            length = content.position();
            makeFlvFrame(FRAME_TYPE_SPS);
            isSpsFrameSended = true;

            // Send Sei Frame Here
//            content.clear();
//            byte[] sei_content = null;
//            int degree = mConfig.getRecordOrientation();
//            if (degree == 0) {
//            } else if (degree == 90) {
//                sei_content = SEI_ROTATION_90;
//            } else if (degree == 180) {
//                sei_content = SEI_ROTATION_180;
//            } else if (degree == 270) {
//                sei_content = SEI_ROTATION_270;
//            }
//            if (sei_content != null) {
//                content.put(sei_content);
//                length = content.position();
//                makeFlvFrame(FRAME_TYPE_DATA);
//            }
//            isSeiFrameSended = true;
        }
    }

    private void parseVideo() {
        try {
            // 0-3 length,4 type
            int headerResult = fill(header, 0, 4);
//            Log.d(Constants.LOG_TAG, "header size = " + 4 + "header read result = " + headerResult);
            length = (header[0] & 0xFF) << 24 | (header[1] & 0xFF) << 16 | (header[2] & 0xFF) << 8 | (header[3] & 0xFF);
            if (length > mConfig.getVideoBitRate() * 5 || length < 0) {
                return;
            }
//            Log.d(Constants.LOG_TAG, "header length size = " + length + "content length");
            content.clear();
            int contentLength = readIntoBuffer(content, length);
//            Log.d(Constants.LOG_TAG, "header length = " + length + "content length" + contentLength);
            if (content.limit() > 0) {
                kFlag = content.get(0);
                nalutype = kFlag & 0x1F;
            }
            // Three types of flv video frame
            makeFlvFrame(FRAME_TYPE_DATA);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void fillArray(byte[] sps_pps, byte[] target) {
        for (int i = 0; i < target.length; i++) {
            sps_pps[last_sum + i] = target[i];
        }
        last_sum += target.length;
    }

    private void makeFlvFrame(int type) {
        ts = sync.getTime();
        videoExtraSize = 5;
        int frameTotalLength;
        int degree = mConfig.getRecordOrientation();
        if (type == FRAME_TYPE_SPS) {
            ts = 0;
            frameTotalLength = FRAME_DEFINE_HEAD_LENGTH + length + videoExtraSize + FRAME_DEFINE_FOOTER_LENGTH;
            dataLengthArray = intToByteArray(length + videoExtraSize);
        } else if (degree == 0) {
            frameTotalLength = FRAME_DEFINE_HEAD_LENGTH + length + videoExtraSize + 4 + FRAME_DEFINE_FOOTER_LENGTH;
            dataLengthArray = intToByteArray(length + videoExtraSize + 4);
        } else {
            frameTotalLength = FRAME_DEFINE_HEAD_LENGTH + length + videoExtraSize + 11 + 4 + FRAME_DEFINE_FOOTER_LENGTH;
            dataLengthArray = intToByteArray(length + videoExtraSize + 11 + 4);
        }
        flvFrameByteArray = new byte[frameTotalLength];
        flvFrameByteArray[0] = (byte) FRAME_DEFINE_TYPE_VIDEO;
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
        // added 5 extra bytes
        for (int i = 0; i < videoExtraSize; i++) {
            if (i == 0) {
                //1 byte flag
                flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + i] = (byte) 23;
            } else if (i == 1) {
                if (type == FRAME_TYPE_SPS) {
                    flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + i] = (byte) 0;
                } else {
                    flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + i] = (byte) 1;
                }
            } else if (i < 5) {
                flvFrameByteArray[FRAME_DEFINE_HEAD_LENGTH + i] = (byte) 0;
            }
        }
        // Add Sei Content and Replace Data content here
//        int pos = 0;
        int pos = FRAME_DEFINE_HEAD_LENGTH + videoExtraSize;
        if (type != FRAME_TYPE_SPS) {
            if (degree != 0) {
                // copy sei content
                int sei_length = 7;
                byte[] sei_content = SEI_ROTATION_0;
                byte[] sei_length_array = intToByteArrayFull(sei_length);
                System.arraycopy(sei_length_array, 0, flvFrameByteArray, pos, 4);
                pos += 4;
                if (degree == 90) {
                    sei_content = SEI_ROTATION_90;
                } else if (degree == 180) {
                    sei_content = SEI_ROTATION_180;
                } else if (degree == 270) {
                    sei_content = SEI_ROTATION_270;
                }
                System.arraycopy(sei_content, 0, flvFrameByteArray, pos, 7);
                pos += 7;

            }
            byte[] real_data_length_array = intToByteArrayFull(length);
            System.arraycopy(real_data_length_array, 0, flvFrameByteArray, pos, real_data_length_array.length);
            pos += 4;
        } else {
            KsyRecordClient.startWaitTIme = System.currentTimeMillis() - KsyRecordClient.startTime;
        }
        //copy real frame  data

        System.arraycopy(content.array(), 0, flvFrameByteArray, pos, length);
        pos += length;

        allFrameLengthArray = intToByteArrayFull(pos + FRAME_DEFINE_FOOTER_LENGTH);
        System.arraycopy(allFrameLengthArray, 0, flvFrameByteArray, pos, allFrameLengthArray.length);

        //添加视频数据到队列
        KSYFlvData ksyVideo = new KSYFlvData();
        ksyVideo.byteBuffer = flvFrameByteArray;
        ksyVideo.size = flvFrameByteArray.length;
        ksyVideo.dts = (int) ts;
        ksyVideo.type = 11;
        if (type == FRAME_TYPE_SPS) {
            ksyVideo.frameType = KSYFlvData.NALU_TYPE_IDR;
        } else {
            ksyVideo.frameType = nalutype;
        }
        ksyVideoSender.addToQueue(ksyVideo, FROM_VIDEO_DATA);
    }
    // Add data here


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

    private void sendFlv() {

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onInfo Message what = " + what + ",extra =" + extra);
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.d(Constants.LOG_TAG, "onError Message what = " + what + ",extra =" + extra);
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



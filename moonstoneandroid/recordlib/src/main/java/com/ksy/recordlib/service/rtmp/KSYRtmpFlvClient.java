package com.ksy.recordlib.service.rtmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.ksy.recordlib.service.util.Constants;


public class KSYRtmpFlvClient {

    //	@AccessedByNative
    public long mNativeRTMP;

    private final String url;

    private Thread worker;
    private Looper looper;
    public static Handler handler;

    private final KSYFlv flv;
    private boolean sequenceHeaderOk;
    private KSYFlvFrame videoSequenceHeader;
    private KSYFlvFrame audioSequenceHeader;

    // use cache queue to ensure audio and video monotonically increase.
    private final ArrayList<KSYFlvFrame> cache;
    private int nb_videos;
    private int nb_audios;

    // private BufferedOutputStream bos;

    private static final int VIDEO_TRACK = 100;
    private static final int AUDIO_TRACK = 101;

    private boolean connected = false;
    private boolean isFristFrame = false;

    private final byte[] cachedBuffer;
    private int cachedSize;

    private final ByteBuffer pps = ByteBuffer.allocate(4);
    private final ByteBuffer th = ByteBuffer.allocate(11);

    public KSYRtmpFlvClient(String path) {

        nb_videos = 0;
        nb_audios = 0;
        sequenceHeaderOk = false;

        url = path;
        flv = new KSYFlv();
        cache = new ArrayList<KSYFlvFrame>();
        cachedBuffer = new byte[4096];
        cachedSize = 0;
        loadLibs();
    }

    private void loadLibs() {

        System.loadLibrary("rtmp");
        Log.i(Constants.LOG_TAG, "rtmp.so loaded");
        System.loadLibrary("ksyrtmp");
        Log.i(Constants.LOG_TAG, "ksyrtmp.so loaded");

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public int addTrack(MediaFormat format) {

        if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) {
            flv.setVideoTrack(format);
            return VIDEO_TRACK;
        }
        flv.setAudioTrack(format);
        return AUDIO_TRACK;
    }

    public void start() throws IOException {

        worker = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    cycle();
                } catch (InterruptedException ie) {
                } catch (Exception e) {
                    Log.i(Constants.LOG_TAG, "worker: thread exception.");
                    e.printStackTrace();
                }
            }
        });
        worker.start();
    }

    public void release() {

        stop();
    }

    /**
     * stop the muxer, disconnect HTTP connection from SRS.
     */
    public void stop() {

        clearCache();

        if (worker == null) {
            return;
        }

        if (looper != null) {
            looper.quit();
        }

        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Log.i(Constants.LOG_TAG, "worker: join thread failed.");
                e.printStackTrace();
                worker.stop();
            }
            worker = null;
        }

        _close();
        Log.i(Constants.LOG_TAG, String.format("worker: muxer closed, url=%s", url));
    }

    public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) throws Exception {

        // Log.i(TAG, String.format("dumps the %s stream %dB, pts=%d",
        // (trackIndex == VIDEO_TRACK) ? "Vdieo" : "Audio", bufferInfo.size,
        // bufferInfo.presentationTimeUs / 1000));
        // SrsHttpFlv.srs_print_bytes(TAG, byteBuf, bufferInfo.size);

        if (bufferInfo.offset > 0) {
//            Log.w(Constants.LOG_TAG, String.format("encoded frame %dB, offset=%d pts=%dms", bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs));
        }

        if (VIDEO_TRACK == trackIndex) {
            flv.writeVideoSample(byteBuf, bufferInfo);
        } else {
            flv.writeAudioSample(byteBuf, bufferInfo);
        }
    }

    private void disconnect() {

        Log.e("guoli", "worker: disconnect SRS begin.");
        clearCache();
        _close();
        connected = false;
        isFristFrame = true;
        Log.e("guoli", "worker: disconnect SRS ok.");
    }

    private void clearCache() {

        nb_videos = 0;
        nb_audios = 2;
        cache.clear();
        cachedSize = 0;
        sequenceHeaderOk = false;
    }

    private void reconnect() throws Exception {

        if (connected)
            return;
        disconnect();
        _set_output_url(url);
        Log.e("guoli", "open begin");
        int conncode = _open();
        connected = conncode == 0 ? true : false;
        // File file = new File(Environment.getExternalStorageDirectory(),
        // "log.flv");
        // file.createNewFile();
        // bos = new BufferedOutputStream(new FileOutputStream(file));
        Log.e("guoli", "connect code :" + conncode);

        // write 13B header
        // 9bytes header and 4bytes first previous-tag-size

        clearCache();
        cachedSize = 0;

    }

    private void cycle() throws Exception {

        // create the handler.
        Looper.prepare();
        looper = Looper.myLooper();
        handler = new Handler(looper) {

            @Override
            public void handleMessage(Message msg) {

                if (msg.what != KSYHttpFlvClient.SrsMessageType.FLV) {
                    Log.w(Constants.LOG_TAG, String.format("worker: drop unkown message, what=%d", msg.what));
                    return;
                }
                KSYFlvFrame frame = (KSYFlvFrame) msg.obj;
                try {
                    // only reconnect when got keyframe.
                    if (frame.is_keyframe()) {
                        reconnect();
                    }
                } catch (Exception e) {
                    Log.e(Constants.LOG_TAG, String.format("worker: reconnect failed. e=%s", e.getMessage()));
                    disconnect();
                }

                try {
                    // when sequence header required,
                    // adjust the dts by the current frame and sent it.

                    if (!sequenceHeaderOk) {
                        if (videoSequenceHeader != null) {
                            videoSequenceHeader.dts = frame.dts;
                        }
                        if (audioSequenceHeader != null) {
                            audioSequenceHeader.dts = frame.dts;
                        }

                        sendFlvTag(audioSequenceHeader);
                        sendFlvTag(videoSequenceHeader);
                        sequenceHeaderOk = true;
                    }

                    // try to send, igore when not connected.
                    if (sequenceHeaderOk) {
                        sendFlvTag(frame);
                    }

                    // cache the sequence header.
                    if (frame.type == KSYCodecFlvTag.Video && frame.avc_aac_type == KSYCodecVideoAVCType.SequenceHeader) {
                        videoSequenceHeader = frame;
                    } else if (frame.type == KSYCodecFlvTag.Audio && frame.avc_aac_type == 0) {
                        audioSequenceHeader = frame;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(Constants.LOG_TAG, String.format("worker: send flv tag failed, e=%s", e.getMessage()));
                    disconnect();
                }
            }
        };
        flv.setHandler(handler);

        Looper.loop();
    }

    private void sendFlvTag(KSYFlvFrame frame) throws IOException {

        if (frame == null) {
            return;
        }

        if (frame.tag.size <= 0) {
            return;
        }

        if (frame.is_video()) {
            nb_videos++;
        } else if (frame.is_audio()) {
            nb_audios++;
        }
        cache.add(frame);

        // always keep one audio and one videos in cache.
        if (nb_videos > 1 && nb_audios > 1) {
            sendCachedFrames();
        }
    }

    private void sendCachedFrames() throws IOException {

        Collections.sort(cache, new Comparator<KSYFlvFrame>() {

            @Override
            public int compare(KSYFlvFrame lhs, KSYFlvFrame rhs) {

                return lhs.dts - rhs.dts;
            }
        });

        while (nb_videos > 1 && nb_audios > 1) {

            KSYFlvFrame frame = cache.remove(0);

            if (frame.is_video()) {
                nb_videos--;
            } else if (frame.is_audio()) {
                nb_audios--;
            }

            // Reserved UB [2]
            // Filter UB [1]
            // TagType UB [5]
            // DataSize UI24
            // th.rewind();
            // int tag_size = (frame.tag.size & 0x00FFFFFF) | ((frame.type &
            // 0x1F) << 24);
            // th.putInt(tag_size);
            // // Timestamp UI24
            // // TimestampExtended UI8
            // int time = (frame.dts << 8) & 0xFFFFFF00 | ((frame.dts >> 24) &
            // 0x000000FF);
            // th.putInt(time);
            // // StreamID UI24 Always 0.
            // th.put((byte) 0);
            // th.put((byte) 0);
            // th.put((byte) 0);
            // byte[] thbs = th.array();
            // _write(thbs, thbs.length);

            // write the flv tag data.
            frame.tag.frame.rewind();
            byte[] data = frame.tag.frame.array();
            _write(data, data.length);

            // write the 4B previous tag size.
            // @remark, we append the tag size, this is different to SRS which
            // write RTMP packet.
            // pps.rewind();
            // pps.putInt(frame.tag.size + 11);
            // byte[] ppsbs = pps.array();
            // _write(ppsbs, ppsbs.length);
        }
    }

    private native int _set_output_url(String url);

    private native int _open();

    private native int _close();

    private native int _write(byte[] buffer, int size);

    private void write(byte[] buffer, Boolean video) {

        if (buffer == null || buffer.length <= 0)
            return;
        if (video) {
            int result = _write(buffer, buffer.length);
            if (result != buffer.length) {
                Log.e("guoli", "write result code :" + result + "   length :" + cachedSize);
                System.exit(-1);
            }
            return;
        }
        if (buffer.length > (cachedBuffer.length - cachedSize)) {
            int result = _write(cachedBuffer, cachedSize);
            if (result != cachedSize) {
                Log.e("guoli", "write result code :" + result + "   length :" + cachedSize);
                System.exit(-1);
            }
            cachedSize = 0;
        }

        System.arraycopy(buffer, 0, cachedBuffer, cachedSize, buffer.length);
        cachedSize += buffer.length;

    }

}

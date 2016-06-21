package com.ksy.recordlib.service.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.ksy.recordlib.service.data.SenderStatData;
import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.NetworkMonitor;
import com.ksy.recordlib.service.util.URLConverter;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Created by eflakemac on 15/6/26.
 */
public class KsyRecordSender {
    //	@AccessedByNative
    public long mNativeRTMP;

    private String TAG = "KsyRecordSender";

    private Thread worker;
    private String mUrl;
    private boolean connected = false;

    //    private LinkedList<KSYFlvData> recordQueue;
    private PriorityQueue<KSYFlvData> recordPQueue;

    private Object mutex = new Object();
    private Context mContext;
    private SenderStatData statData = new SenderStatData();

    private static final int FIRST_OPEN = 3;
    private static final int FROM_AUDIO = 8;
    private static final int FROM_VIDEO = 6;


    private static KsyRecordSender ksyRecordSenderInstance = new KsyRecordSender();

    private long lastRefreshTime;
    private long lastSendVideoDts;
    private long lastSendAudioDts;
    private long lastSendVideoTs;
    private long lastSendAudioTs;
    private int dropAudioCount;
    private int dropVideoCount;

    private int lastAddAudioTs = 0;
    private int lastAddVideoTs = 0;

    private boolean inited = false;
    private long ideaStartTime;
    private long systemStartTime;

    public boolean needResetTs = false;
    private volatile boolean dropNoneIDRFrame = false;
    private KsyRecordClient.RecordHandler recordHandler;

    private Speedometer vidoeFps = new Speedometer();
    private Speedometer audioFps = new Speedometer();
    private long lastPoorNotificationTime = 0;
    private String inputUrl = "";

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.NETWORK_STATE_CHANGED)) {
                onNetworkChanged();
            }
        }
    };

    static {
        //System.loadLibrary("m");
        System.loadLibrary("rtmp");
        System.loadLibrary("moonstone");

    }

    private KsyRecordSender() {
        recordPQueue = new PriorityQueue<>(10, new Comparator<KSYFlvData>() {
            @Override
            public int compare(KSYFlvData lhs, KSYFlvData rhs) {
                return lhs.dts - rhs.dts;
            }
        });
    }

    public static KsyRecordSender getRecordInstance() {
        return ksyRecordSenderInstance;
    }

    public String getAVBitrate() {
        return "\nwait=" + KsyRecordClient.startWaitTIme + "a.b=" + statData.audio_byte_count + " v.b=" + statData.video_byte_count +
                "\n,vFps =" + vidoeFps.getSpeed() + " aFps=" + audioFps.getSpeed() + " dropA:" + dropAudioCount + " dropV:" + dropVideoCount + " sendS:" + statData.getLastTimeSendByteCount() +
                "\n, lastStAudioTs:" + lastSendAudioTs + "stAvDist=" + (lastSendAudioTs - lastSendVideoDts) + "\n,size=" + recordPQueue.size() + " f_v=" + statData.frame_video + " f_a=" + statData.frame_audio + "\n" + KsyMediaSource.sync.lastMessage;
    }

    public void start(Context pContext) throws IOException {
        IntentFilter filter = new IntentFilter(Constants.NETWORK_STATE_CHANGED);
        LocalBroadcastManager.getInstance(pContext).registerReceiver(receiver, filter);
        mContext = pContext;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    setRecorderData();
                    InsertMetaData();
                    cycle();
                } catch (Exception e) {
                    Log.e(Constants.LOG_TAG, "worker: thread exception. e＝" + e);
                    e.printStackTrace();
                }
            }
        });
        worker.start();
    }

    private void InsertMetaData(){
        FLvMetaData myMeta = new FLvMetaData(KsyRecordClient.getConfig());
        byte[] MetaData = myMeta.getMetaData();
        _write(MetaData, MetaData.length);
    }

    private void cycle() throws InterruptedException {
        while (!Thread.interrupted()) {
            while (!connected) {
                Thread.sleep(10);
            }
            if (statData.frame_video > statData.MIN_QUEUE_BUFFER && statData.frame_audio > statData.MIN_QUEUE_BUFFER || recordPQueue.size() > 30) {
                KSYFlvData ksyFlv = null;
                synchronized (mutex) {
                    if (recordPQueue.size() > 0) {
                        ksyFlv = recordPQueue.remove();
                    } else {
                        statData.clear();
                        continue;
                    }
                }
                statData.remove(ksyFlv);
                if (ksyFlv.type == KSYFlvData.FLV_TYPE_VIDEO) {
                    lastSendVideoTs = ksyFlv.dts;
                } else if (ksyFlv.type == KSYFlvData.FLV_TYTPE_AUDIO) {
                    lastSendAudioTs = ksyFlv.dts;
                }
                if (needDropFrame(ksyFlv)) {
                    statDropFrame(ksyFlv);
                } else {
                    lastRefreshTime = System.currentTimeMillis();
                    waiting(ksyFlv);
//                    Log.e(TAG, "ksyFlv ts=" + ksyFlv.dts + " size=" + ksyFlv.size + " type=" + (ksyFlv.type == KSYFlvData.FLV_TYTPE_AUDIO ? "==ADO==" : "**VDO**"));
                    int w = _write(ksyFlv.byteBuffer, ksyFlv.byteBuffer.length);
                    statBitrate(w, ksyFlv.type);
                }
            }
        }
    }

    private boolean needDropFrame(KSYFlvData ksyFlv) {
        boolean dropFrame = false;
        int queueSize = recordPQueue.size();
        int dts = ksyFlv.dts;
        if (queueSize > statData.LEVEL2_QUEUE_SIZE || (dropNoneIDRFrame && ksyFlv.type == KSYFlvData.FLV_TYPE_VIDEO)) {
            dropFrame = true;
        }
        if (ksyFlv.type == KSYFlvData.FLV_TYPE_VIDEO) {
            lastSendVideoDts = dts;
            if (ksyFlv.isKeyframe()) {
                dropNoneIDRFrame = false;
                dropFrame = false;
            }
            if (dropFrame) {
                dropNoneIDRFrame = true;
            }
        } else {
            lastSendAudioDts = dts;
        }
        return dropFrame;
    }

    private void statDropFrame(KSYFlvData dropped) {
        if (dropped.type == KSYFlvData.FLV_TYPE_VIDEO) {
            dropVideoCount++;
        } else if (dropped.type == KSYFlvData.FLV_TYTPE_AUDIO) {
            dropAudioCount++;
        }
        Log.d(TAG, "drop frame !!" + dropped.isKeyframe());
    }

    private void statBitrate(int sent, int type) {
        if (sent == -1) {
            connected = false;
            Log.e(TAG, "statBitrate send frame failed!");
            recordHandler.sendEmptyMessage(Constants.MESSAGE_SENDER_PUSH_FAILED);
        } else {
            long time = System.currentTimeMillis() - lastRefreshTime;
            time = time == 0 ? 1 : time;
            if (time > 500) {
                sendPoorNetworkMessage(NetworkMonitor.OnNetworkPoorListener.FRAME_SEND_TOO_LONG);
                Log.e(TAG, "statBitrate time > 500ms network maybe poor! Time use:" + time);
            }
            statData.lastTimeSendByteCount += sent;
        }
    }

    private void sendPoorNetworkMessage(int reason) {
        if (System.currentTimeMillis() - lastPoorNotificationTime > 3000 && recordHandler != null) {
            recordHandler.sendEmptyMessage(reason);
            lastPoorNotificationTime = System.currentTimeMillis();
        }
    }

    private void removeToNextIDRFrame(PriorityQueue<KSYFlvData> recordPQueue) {
        if (recordPQueue.size() > 0) {
            KSYFlvData data = recordPQueue.remove();
            if (data.type == KSYFlvData.FLV_TYPE_VIDEO) {
                if (data.isKeyframe()) {
                    recordPQueue.add(data);
                } else {
                    statData.remove(data);
                    removeToNextIDRFrame(recordPQueue);
                }
            } else {
                statData.remove(data);
                removeToNextIDRFrame(recordPQueue);
            }
        }
    }

    private void removeQueue(PriorityQueue<KSYFlvData> recordPQueue) {
        if (recordPQueue.size() > 0) {
            KSYFlvData data = recordPQueue.remove();
            if (data.type == KSYFlvData.FLV_TYPE_VIDEO && data.isKeyframe()) {
                removeToNextIDRFrame(recordPQueue);
            }
            statData.remove(data);
        }
    }

    //send data to server
    public synchronized void addToQueue(KSYFlvData ksyFlvData, int k) {
        if (ksyFlvData == null) {
            return;
        }
        if (ksyFlvData.size <= 0) {
            return;
        }
        KsyMediaSource.sync.setAvDistance(lastAddAudioTs - lastAddVideoTs);
        // add video data
        synchronized (mutex) {
            if (recordPQueue.size() > statData.LEVEL1_QUEUE_SIZE) {
                removeQueue(recordPQueue);
                sendPoorNetworkMessage(NetworkMonitor.OnNetworkPoorListener.CACHE_QUEUE_MAX);
            }
            if (k == FROM_VIDEO) { //视频数据
                if (needResetTs) {
                    KsyMediaSource.sync.resetTs(lastAddAudioTs);
                    Log.d(Constants.LOG_TAG, "lastAddAudioTs = " + lastAddAudioTs);
                    Log.d(Constants.LOG_TAG, "lastAddVideoTs = " + lastAddVideoTs);
                    Log.d(Constants.LOG_TAG, "ksyFlvData.dts = " + ksyFlvData.dts);
                    needResetTs = false;
                    lastAddVideoTs = lastAddAudioTs;
                    ksyFlvData.dts = lastAddVideoTs;
                }
                vidoeFps.tickTock();
                lastAddVideoTs = ksyFlvData.dts;
//                Log.d(Constants.LOG_TAG, "video_enqueue = " + ksyFlvData.dts + " " + ksyFlvData.isKeyframe());
            } else if (k == FROM_AUDIO) {//音频数据
                lastAddAudioTs = ksyFlvData.dts;
            }
            statData.add(ksyFlvData);
            recordPQueue.add(ksyFlvData);
        }
    }


    private synchronized void onNetworkChanged() {
        Log.e(TAG, "onNetworkChanged .." + NetworkMonitor.networkConnected());
        if (NetworkMonitor.networkConnected()) {
            reconnect();
        } else {
            pauseSend();
        }
    }

    private void reconnect() {
        if (!connected) {
            Log.e(TAG, "reconnecting ...");
            Log.e(TAG, "close .." + _close());
            Log.e(TAG, "_set_output_url .." + _set_output_url(mUrl));
            int result = _open();
            connected = result == 0;
            if (connected) {
                if (recordHandler != null) {
                    recordHandler.sendEmptyMessage(KsyRecordClient.StartListener.START_COMPLETE);
                }
            } else {
                if (recordHandler != null) {
                    recordHandler.sendEmptyMessage(KsyRecordClient.StartListener.START_FAILED);
                }
            }
            Log.e(TAG, "opens result ..>" + result);
        }
    }

    private void pauseSend() {
        connected = false;
    }

    public void disconnect() {
        _close();
        if (worker.isAlive()) {
            worker.interrupt();
        }
        recordPQueue.clear();
        statData.clear();
        connected = false;
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(receiver);
    }

    public void setRecorderData() {
        if (connected) {
            return;
        }
       // int a=_getval();
        mUrl = URLConverter.convertUrl(inputUrl);
        //invoke nji
        int i = _set_output_url(mUrl);
        Log.e(TAG, "_set_output_url .." + i + " inputUrl=" + mUrl);
        //3视频  0音频
        int k = _open();
        connected = k == 0;
        if (connected) {
            if (recordHandler != null) {
                recordHandler.sendEmptyMessage(KsyRecordClient.StartListener.START_COMPLETE);
            }
        } else {
            if (recordHandler != null) {
                recordHandler.sendEmptyMessage(KsyRecordClient.StartListener.START_FAILED);
            }
        }
        Log.e(TAG, "connected .. open result=" + k);
    }

    public void waiting(KSYFlvData ksyFlvData) throws InterruptedException {
        if (ksyFlvData.type != KSYFlvData.FLV_TYTPE_AUDIO) {
            return;
        }
        long ts = ksyFlvData.dts;
        if (!inited) {
            ideaStartTime = ts;
            systemStartTime = System.currentTimeMillis();
            inited = true;
            return;
        }
        long ideaTime = System.currentTimeMillis() - systemStartTime + ideaStartTime;
        if (Math.abs(ideaTime - ts) > 100) {
            inited = false;
            return;
        }
        while (ts > ideaTime) {
            Thread.sleep(1);
            ideaTime = System.currentTimeMillis() - systemStartTime + ideaStartTime;
        }
    }

    public void clearData() {
        synchronized (mutex) {
            recordPQueue.clear();
            statData.clear();
        }
        inited = false;
    }
   //private  static native int _getval();

    private native int _set_output_url(String url);

    private native int _open();

    private native int _close();

    private native int _write(byte[] buffer, int size);

    public void setStateMonitor(KsyRecordClient.RecordHandler recordHandler) {
        this.recordHandler = recordHandler;
    }

    public SenderStatData getStatData() {
        return statData;
    }

    public KsyRecordSender setInputUrl(String inputUrl) {



        this.inputUrl = inputUrl;
        return this;
    }

    public static class Speedometer {
        private int time;
        private long startTime;
        private float currentFps = 0;

        public float getSpeedAndRestart() {
            float speed = getSpeed();
            time = 0;
            return speed;
        }

        public void tickTock() {
            if (time == 0) {
                startTime = System.currentTimeMillis();
            }
            time++;
            long current = System.currentTimeMillis();
            long lapse = current - startTime;
            if (lapse > 2000) {
                currentFps = ((float) time / lapse * 1000);
                startTime = current;
                time = 0;
            }
        }

        public void start() {
            time = 0;
            startTime = System.currentTimeMillis();
        }


        public float getSpeed() {
            return currentFps;
        }

    }

}


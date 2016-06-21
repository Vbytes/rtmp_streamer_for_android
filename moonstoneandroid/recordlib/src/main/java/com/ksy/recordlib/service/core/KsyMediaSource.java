package com.ksy.recordlib.service.core;

import android.util.Log;

import com.ksy.recordlib.service.util.Constants;
import com.ksy.recordlib.service.util.OnClientErrorListener;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by eflakemac on 15/6/19.
 */
public abstract class KsyMediaSource implements Runnable {
    protected Thread thread;
    protected FileInputStream is;
    protected FileChannel inputChannel;
    protected byte[] header = new byte[4];
    protected long ts = 0;
    protected static ClockSync sync = new ClockSync();
    protected OnClientErrorListener onClientErrorListener;
    protected boolean mRunning = false;

    public abstract void prepare();

    public abstract void start();

    public abstract void stop();

    public abstract void release();


    protected int fill(byte[] buffer, int offset, int length) throws IOException {
        int sum = 0, len;
        while (sum < length) {
            len = is.read(buffer, offset + sum, length - sum);
            if (len < 0) {
                throw new IOException("End of stream");
            } else sum += len;
        }
        return sum;
    }

    protected int readIntoBuffer(ByteBuffer byteBuffer, int length) throws IOException {
        int sum = 0, len;
        if (byteBuffer.position() + length > byteBuffer.capacity()) {
            byteBuffer.limit(byteBuffer.capacity());
        } else {
            byteBuffer.limit(byteBuffer.position() + length);
        }
        while (sum < length) {
            len = inputChannel.read(byteBuffer);
            if (len < 0) {
                throw new IOException("End of stream");
            } else sum += len;
        }
        return sum;
    }

    public static class ClockSync {
        private static final int MAX_DISTANCE_TIME = 100;
        private long frameSumDuration = 0;
        private long frameSumCount = 10000;
        private long lastSysTime = 0;
        private int avDistance = 0;
        private boolean inited = false;
        private double lastTS = 0;
        public String lastMessage;
        long average = 0;
        private boolean forceSyncFlag = false;

        public long getTime() {
            long d;
            long delta = 0;

            if (!inited) {
                frameSumCount = 10000;
                frameSumDuration = frameSumCount * 33;
                lastSysTime = System.currentTimeMillis();
//                lastTS = 0;
                inited = true;
                lastTS = 0;
            } else {
                long currentTime = System.currentTimeMillis();
                d = currentTime - lastSysTime;
                lastSysTime = currentTime;
                frameSumDuration += d;
                frameSumCount++;
                delta = 0;
                average = (frameSumDuration / frameSumCount);
                if (avDistance > MAX_DISTANCE_TIME || avDistance < -MAX_DISTANCE_TIME) {
                    //audio's DTS large than video's DTS so send video quickly ,delta--
                    delta = (long) (1f / MAX_DISTANCE_TIME * avDistance);
                    if (avDistance > 10000 || avDistance < -10000) {
                        Log.d(Constants.LOG_TAG, "lastdts = " + lastTS);
                        Log.d(Constants.LOG_TAG, "avDistance = " + avDistance);
                        lastTS += avDistance;
                        avDistance = 0;
                    }
                }
                if (average + delta <= 0) {
                    Log.d(Constants.LOG_TAG, "sync: average + delta <= 0" + lastTS);
                    lastTS += 1;
                } else {
                    lastTS += (average + delta);
                }
            }
            lastMessage = String.format("sync: avDis=%d delta=%d lastTs=%.1f avg=%d", avDistance, delta, lastTS, average);
            return (long) lastTS;
        }

        public void clear() {
            inited = false;
        }

        public void setAvDistance(int avDistance) {
            if (forceSyncFlag) {
                lastTS += avDistance;
                KsyRecordSender.getRecordInstance().clearData();
                forceSyncFlag = false;
            } else {
                this.avDistance = avDistance;
            }
        }

        public void resetTs(int ts) {
            lastTS = ts;
            this.avDistance = 0;
        }

        public void setForceSyncFlay(boolean flag) {
            this.forceSyncFlag = flag;
        }
    }

    public KsyMediaSource setOnClientErrorListener(OnClientErrorListener onClientErrorListener) {
        this.onClientErrorListener = onClientErrorListener;
        return this;
    }
}

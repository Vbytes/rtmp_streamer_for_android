package com.ksy.recordlib.service.rtmp;

import java.nio.ByteBuffer;

import android.media.MediaCodec;

public class KSYUtils {

    public boolean bytes_equals(byte[] a, byte[] b) {

        if ((a == null || b == null) && (a != null || b != null)) {
            return false;
        }

        if (a.length != b.length) {
            return false;
        }

        for (int i = 0; i < a.length && i < b.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        return true;
    }

    public KSYAnnexbSearch startswith_annexb(ByteBuffer bb, MediaCodec.BufferInfo bi) {

        KSYAnnexbSearch as = new KSYAnnexbSearch();
        as.match = false;

        int pos = bb.position();
        while (pos < bi.size - 3) {
            // not match.
            if (bb.get(pos) != 0x00 || bb.get(pos + 1) != 0x00) {
                break;
            }

            // match N[00] 00 00 01, where N>=0
            if (bb.get(pos + 2) == 0x01) {
                as.match = true;
                as.nb_start_code = pos + 3 - bb.position();
                break;
            }

            pos++;
        }

        return as;
    }

    public boolean aac_startswith_adts(ByteBuffer bb, MediaCodec.BufferInfo bi) {

        int pos = bb.position();
        if (bi.size - pos < 2) {
            return false;
        }

        // matched 12bits 0xFFF,
        // @remark, we must cast the 0xff to char to compare.
        if (bb.get(pos) != (byte) 0xff || (byte) (bb.get(pos + 1) & 0xf0) != (byte) 0xf0) {
            return false;
        }

        return true;
    }

    public int codec_aac_ts2rtmp(int profile) {

        switch (profile) {
            case KSYAacProfile.Main:
                return KSYAacObjectType.AacMain;
            case KSYAacProfile.LC:
                return KSYAacObjectType.AacLC;
            case KSYAacProfile.SSR:
                return KSYAacObjectType.AacSSR;
            default:
                return KSYAacObjectType.Reserved;
        }
    }

    public int codec_aac_rtmp2ts(int object_type) {

        switch (object_type) {
            case KSYAacObjectType.AacMain:
                return KSYAacProfile.Main;
            case KSYAacObjectType.AacHE:
            case KSYAacObjectType.AacHEV2:
            case KSYAacObjectType.AacLC:
                return KSYAacProfile.LC;
            case KSYAacObjectType.AacSSR:
                return KSYAacProfile.SSR;
            default:
                return KSYAacProfile.Reserved;
        }
    }

    // the color transform, @see
    // http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {

		/*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12 We convert by putting
		 * the corresponding U and V bytes together (interleaved).
		 */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb
            // (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    public static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {

		/*
		 * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V
		 * reversed. So we just have to reverse U and V.
		 */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr
        // (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb
        // (U)

        return output;
    }
}

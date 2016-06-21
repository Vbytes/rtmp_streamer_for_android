package com.ksy.recordlib.service.rtmp;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ksy.recordlib.service.util.Constants;


public class KSYFlv {

    private MediaFormat videoTrack;
    private MediaFormat audioTrack;
    private int achannel;
    private int asample_rate;

    private final KSYUtils utils;
    private Handler handler;

    private final KSYH264Stream avc;
    private byte[] h264_sps;
    private boolean h264_sps_changed;
    private byte[] h264_pps;
    private boolean h264_pps_changed;
    private boolean h264_sps_pps_sent;

    private byte[] aac_specific_config;

    public KSYFlv() {

        utils = new KSYUtils();

        avc = new KSYH264Stream();
        h264_sps = new byte[0];
        h264_sps_changed = false;
        h264_pps = new byte[0];
        h264_pps_changed = false;
        h264_sps_pps_sent = false;

        aac_specific_config = null;
    }

    /**
     * set the handler to send message to work thread.
     *
     * @param h the handler to send the message.
     */
    public void setHandler(Handler h) {

        handler = h;
    }

    public void setVideoTrack(MediaFormat format) {

        videoTrack = format;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void setAudioTrack(MediaFormat format) {

        audioTrack = format;
        achannel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        asample_rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {

        int pts = (int) (bi.presentationTimeUs);
        int dts = pts;

        byte[] frame = new byte[bi.size + 2 + 11 + 4];
        byte aac_packet_type = 1; // 1 = AAC raw
        if (aac_specific_config == null) {
            frame = new byte[4 + 11 + 4];

            // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
            // AudioSpecificConfig (), page 33
            // 1.6.2.1 AudioSpecificConfig
            // audioObjectType; 5 bslbf
            byte ch = (byte) (bb.get(0) & 0xf8);
            // 3bits left.

            // samplingFrequencyIndex; 4 bslbf
            byte samplingFrequencyIndex = 0x04;
            if (asample_rate == KSYCodecAudioSampleRate.R22050) {
                samplingFrequencyIndex = 0x07;
            } else if (asample_rate == KSYCodecAudioSampleRate.R11025) {
                samplingFrequencyIndex = 0x0a;
            }
            ch |= (samplingFrequencyIndex >> 1) & 0x07;
            frame[2 + 11] = ch;

            ch = (byte) ((samplingFrequencyIndex << 7) & 0x80);
            // 7bits left.

            // channelConfiguration; 4 bslbf
            byte channelConfiguration = 1;
            if (achannel == 2) {
                channelConfiguration = 2;
            }
            ch |= (channelConfiguration << 3) & 0x78;
            // 3bits left.

            // GASpecificConfig(), page 451
            // 4.4.1 Decoder configuration (GASpecificConfig)
            // frameLengthFlag; 1 bslbf
            // dependsOnCoreCoder; 1 bslbf
            // extensionFlag; 1 bslbf
            frame[3 + 11] = ch;

            aac_specific_config = frame;
            aac_packet_type = 0; // 0 = AAC sequence header
        } else {
            bb.get(frame, 2 + 11, frame.length - 2 - 11 - 4);
        }

        byte sound_format = 10; // AAC
        byte sound_type = 0; // 0 = Mono sound
        if (achannel == 2) {
            sound_type = 1; // 1 = Stereo sound
        }
        byte sound_size = 1; // 1 = 16-bit samples
        byte sound_rate = 3; // 44100, 22050, 11025
        if (asample_rate == 22050) {
            sound_rate = 2;
        } else if (asample_rate == 11025) {
            sound_rate = 1;
        }

        // for audio frame, there is 1 or 2 bytes header:
        // 1bytes, SoundFormat|SoundRate|SoundSize|SoundType
        // 1bytes, AACPacketType for SoundFormat == 10, 0 is sequence
        // header.
        byte audio_header = (byte) (sound_type & 0x01);
        audio_header |= (sound_size << 1) & 0x02;
        audio_header |= (sound_rate << 2) & 0x0c;
        audio_header |= (sound_format << 4) & 0xf0;

//		frame[0 + 11] = audio_header;
//		frame[1 + 11] = aac_packet_type;

        KSYFlvFrameBytes tag = new KSYFlvFrameBytes();
        tag.frame = ByteBuffer.wrap(frame);
        tag.size = frame.length;

        tag.frame.rewind();
        int tag_size = ((bi.size + 2) & 0x00FFFFFF) | ((KSYCodecFlvTag.Audio & 0x1F) << 24);
        tag.frame.putInt(tag_size);
        // Timestamp UI24
        // TimestampExtended UI8
        int time = (dts << 8) & 0xFFFFFF00 | ((dts >> 24) & 0x000000FF);
        tag.frame.putInt(time);
        // StreamID UI24 Always 0.
        tag.frame.put((byte) 0);
        tag.frame.put((byte) 0);
        tag.frame.put((byte) 0);
        tag.frame.put((byte) audio_header);
        tag.frame.put((byte) aac_packet_type);

        tag.frame.putInt(tag.frame.limit() - 4, tag.frame.limit() - 4);
        tag.frame.rewind();

        //int timestamp = dts;

        rtmp_write_packet(KSYCodecFlvTag.Audio, dts, 0, aac_packet_type, tag);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {

        int pts = (int) (bi.presentationTimeUs);
        int dts = pts;

        ArrayList<KSYFlvFrameBytes> ibps = new ArrayList<KSYFlvFrameBytes>();
        int frame_type = KSYCodecVideoAVCFrame.InterFrame;
        // Log.i(TAG,
        // String.format("video %d/%d bytes, offset=%d, position=%d, pts=%d",
        // bb.remaining(), bi.size, bi.offset, bb.position(), pts));

        // send each frame.
        while (bb.position() < bi.size) {
            KSYFlvFrameBytes frame = avc.annexb_demux(bb, bi);

            // 5bits, 7.3.1 NAL unit syntax,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
            // 7: SPS, 8: PPS, 5: I Frame, 1: P Frame
            int nal_unit_type = frame.frame.get(0) & 0x1f;
            if (nal_unit_type == KSYAvcNaluType.SPS || nal_unit_type == KSYAvcNaluType.PPS) {
                Log.i(Constants.LOG_TAG, String.format("annexb demux %dB, pts=%d, frame=%dB, nalu=%d", bi.size, pts, frame.size, nal_unit_type));
            }

            // for IDR frame, the frame is keyframe.
            if (nal_unit_type == KSYAvcNaluType.IDR) {
                frame_type = KSYCodecVideoAVCFrame.KeyFrame;
            }

            // ignore the nalu type aud(9)
            if (nal_unit_type == KSYAvcNaluType.AccessUnitDelimiter) {
                continue;
            }

            // for sps
            if (avc.is_sps(frame)) {
                byte[] sps = new byte[frame.size];
                frame.frame.get(sps);

                if (utils.bytes_equals(h264_sps, sps)) {
                    continue;
                }
                h264_sps_changed = true;
                h264_sps = sps;
                continue;
            }

            // for pps
            if (avc.is_pps(frame)) {
                byte[] pps = new byte[frame.size];
                frame.frame.get(pps);

                if (utils.bytes_equals(h264_pps, pps)) {
                    continue;
                }
                h264_pps_changed = true;
                h264_pps = pps;
                continue;
            }

            // ibp frame.
            KSYFlvFrameBytes nalu_header = avc.mux_ibp_frame(frame);
            ibps.add(nalu_header);
            ibps.add(frame);
        }

        write_h264_sps_pps(dts, pts);

        write_h264_ipb_frame(ibps, frame_type, dts, pts);
    }

    private void write_h264_sps_pps(int dts, int pts) {

        // when sps or pps changed, update the sequence header,
        // for the pps maybe not changed while sps changed.
        // so, we must check when each video ts message frame parsed.
        if (h264_sps_pps_sent && !h264_sps_changed && !h264_pps_changed) {
            return;
        }

        // when not got sps/pps, wait.
        if (h264_pps.length <= 0 || h264_sps.length <= 0) {
            return;
        }

        // h264 raw to h264 packet.
        ArrayList<KSYFlvFrameBytes> frames = new ArrayList<KSYFlvFrameBytes>();
        avc.mux_sequence_header(h264_sps, h264_pps, dts, pts, frames);

        // h264 packet to flv packet.
        int frame_type = KSYCodecVideoAVCFrame.KeyFrame;
        int avc_packet_type = KSYCodecVideoAVCType.SequenceHeader;
        KSYFlvFrameBytes flv_tag = avc.mux_avc2flv(frames, frame_type, avc_packet_type, dts, pts);

        // the timestamp in rtmp message header is dts.
        //int timestamp = dts;
        rtmp_write_packet(KSYCodecFlvTag.Video, dts, frame_type, avc_packet_type, flv_tag);

        // reset sps and pps.
        h264_sps_changed = false;
        h264_pps_changed = false;
        h264_sps_pps_sent = true;
        Log.i(Constants.LOG_TAG, String.format("flv: h264 sps/pps sent, sps=%dB, pps=%dB", h264_sps.length, h264_pps.length));
    }

    private void write_h264_ipb_frame(ArrayList<KSYFlvFrameBytes> ibps, int frame_type, int dts, int pts) {

        // when sps or pps not sent, ignore the packet.
        // @see https://github.com/simple-rtmp-server/srs/issues/203
        if (!h264_sps_pps_sent) {
            return;
        }

        int avc_packet_type = KSYCodecVideoAVCType.NALU;
        KSYFlvFrameBytes flv_tag = avc.mux_avc2flv(ibps, frame_type, avc_packet_type, dts, pts);

        //if (frame_type == KSYCodecVideoAVCFrame.KeyFrame) {
        // Log.i(TAG, String.format("flv: keyframe %dB, dts=%d",
        // flv_tag.size, dts));
        //}

        // the timestamp in rtmp message header is dts.
        //int timestamp = dts;
        rtmp_write_packet(KSYCodecFlvTag.Video, dts, frame_type, avc_packet_type, flv_tag);
    }

    private void rtmp_write_packet(int type, int dts, int frame_type, int avc_aac_type, KSYFlvFrameBytes tag) {

        KSYFlvFrame frame = new KSYFlvFrame();
        frame.tag = tag;
        frame.type = type;
        frame.dts = dts;
        frame.frame_type = frame_type;
        frame.avc_aac_type = avc_aac_type;

        // use handler to send the message.
        // TODO: FIXME: we must wait for the handler to ready, for the
        // sps/pps cannot be dropped.
        if (handler == null) {
            Log.w(Constants.LOG_TAG, "flv: drop frame for handler not ready.");
            return;
        }

        Message msg = Message.obtain();
        msg.what = 256;
        msg.obj = frame;
        handler.sendMessage(msg);
        // Log.i(TAG,
        // String.format("flv: enqueue frame type=%d, dts=%d, size=%dB",
        // frame.type, frame.dts, frame.tag.size));
    }

}

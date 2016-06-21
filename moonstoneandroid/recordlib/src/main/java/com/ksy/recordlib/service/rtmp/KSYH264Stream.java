package com.ksy.recordlib.service.rtmp;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.os.Build;
import android.util.Log;

import com.ksy.recordlib.service.util.Constants;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class KSYH264Stream {

    private final KSYUtils utils;

    public KSYH264Stream() {

        utils = new KSYUtils();
    }

    public boolean is_sps(KSYFlvFrameBytes frame) {

        if (frame.size < 1) {
            return false;
        }

        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // 7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int nal_unit_type = frame.frame.get(0) & 0x1f;

        return nal_unit_type == KSYAvcNaluType.SPS;
    }

    public boolean is_pps(KSYFlvFrameBytes frame) {

        if (frame.size < 1) {
            return false;
        }

        // 5bits, 7.3.1 NAL unit syntax,
        // H.264-AVC-ISO_IEC_14496-10.pdf, page 44.
        // 7: SPS, 8: PPS, 5: I Frame, 1: P Frame
        int nal_unit_type = frame.frame.get(0) & 0x1f;

        return nal_unit_type == KSYAvcNaluType.PPS;
    }

    public KSYFlvFrameBytes mux_ibp_frame(KSYFlvFrameBytes frame) {

        KSYFlvFrameBytes nalu_header = new KSYFlvFrameBytes();
        nalu_header.size = 4;
        nalu_header.frame = ByteBuffer.allocate(nalu_header.size);

        // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
        // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes size
        int NAL_unit_length = frame.size;

        // mux the avc NALU in "ISO Base Media File Format"
        // from H.264-AVC-ISO_IEC_14496-15.pdf, page 20
        // NALUnitLength
        nalu_header.frame.putInt(NAL_unit_length);

        // reset the buffer.
        nalu_header.frame.rewind();

        // Log.i(TAG, String.format("mux ibp frame %dB", frame.size));
        // SrsHttpFlv.srs_print_bytes(TAG, nalu_header.frame, 16);

        return nalu_header;
    }

    public void mux_sequence_header(byte[] sps, byte[] pps, int dts, int pts, ArrayList<KSYFlvFrameBytes> frames) {

        // 5bytes sps/pps header:
        // configurationVersion, AVCProfileIndication,
        // profile_compatibility,
        // AVCLevelIndication, lengthSizeMinusOne
        // 3bytes size of sps:
        // numOfSequenceParameterSets, sequenceParameterSetLength(2B)
        // Nbytes of sps.
        // sequenceParameterSetNALUnit
        // 3bytes size of pps:
        // numOfPictureParameterSets, pictureParameterSetLength
        // Nbytes of pps:
        // pictureParameterSetNALUnit

        // decode the SPS:
        // @see: 7.3.2.1.1, H.264-AVC-ISO_IEC_14496-10-2012.pdf, page 62
        if (true) {
            KSYFlvFrameBytes hdr = new KSYFlvFrameBytes();
            hdr.size = 5;
            hdr.frame = ByteBuffer.allocate(hdr.size);

            // @see: Annex A Profiles and levels,
            // H.264-AVC-ISO_IEC_14496-10.pdf, page 205
            // Baseline profile profile_idc is 66(0x42).
            // Main profile profile_idc is 77(0x4d).
            // Extended profile profile_idc is 88(0x58).
            byte profile_idc = sps[1];
            // u_int8_t constraint_set = frame[2];
            byte level_idc = sps[3];

            // generate the sps/pps header
            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // configurationVersion
            hdr.frame.put((byte) 0x01);
            // AVCProfileIndication
            hdr.frame.put(profile_idc);
            // profile_compatibility
            hdr.frame.put((byte) 0x00);
            // AVCLevelIndication
            hdr.frame.put(level_idc);
            // lengthSizeMinusOne, or NAL_unit_length, always use 4bytes
            // size,
            // so we always set it to 0x03.
            hdr.frame.put((byte) 0x03);
            // reset the buffer.
            hdr.frame.rewind();
            frames.add(hdr);
        }

        // sps
        if (true) {
            KSYFlvFrameBytes sps_hdr = new KSYFlvFrameBytes();
            sps_hdr.size = 3;
            sps_hdr.frame = ByteBuffer.allocate(sps_hdr.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // numOfSequenceParameterSets, always 1
            sps_hdr.frame.put((byte) 0x01);
            // sequenceParameterSetLength
            sps_hdr.frame.putShort((short) sps.length);

            sps_hdr.frame.rewind();
            frames.add(sps_hdr);

            // sequenceParameterSetNALUnit
            KSYFlvFrameBytes sps_bb = new KSYFlvFrameBytes();
            sps_bb.size = sps.length;
            sps_bb.frame = ByteBuffer.wrap(sps);
            frames.add(sps_bb);
        }

        // pps
        if (true) {
            KSYFlvFrameBytes pps_hdr = new KSYFlvFrameBytes();
            pps_hdr.size = 3;
            pps_hdr.frame = ByteBuffer.allocate(pps_hdr.size);

            // 5.3.4.2.1 Syntax, H.264-AVC-ISO_IEC_14496-15.pdf, page 16
            // numOfPictureParameterSets, always 1
            pps_hdr.frame.put((byte) 0x01);
            // pictureParameterSetLength
            pps_hdr.frame.putShort((short) pps.length);

            pps_hdr.frame.rewind();
            frames.add(pps_hdr);

            // pictureParameterSetNALUnit
            KSYFlvFrameBytes pps_bb = new KSYFlvFrameBytes();
            pps_bb.size = pps.length;
            pps_bb.frame = ByteBuffer.wrap(pps);
            frames.add(pps_bb);
        }
    }

    public KSYFlvFrameBytes mux_avc2flv(ArrayList<KSYFlvFrameBytes> frames, int frame_type, int avc_packet_type, int dts, int pts) {

        KSYFlvFrameBytes flv_tag = new KSYFlvFrameBytes();

        // for h264 in RTMP video payload, there is 5bytes header:
        // 1bytes, FrameType | CodecID
        // 1bytes, AVCPacketType
        // 3bytes, CompositionTime, the cts.
        // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
        flv_tag.size = 5;
        for (int i = 0; i < frames.size(); i++) {
            KSYFlvFrameBytes frame = frames.get(i);
            flv_tag.size += frame.size;
        }

        flv_tag.frame = ByteBuffer.allocate(flv_tag.size + 11 + 4);

        int tag_size = (flv_tag.size & 0x00FFFFFF) | ((KSYCodecFlvTag.Video & 0x1F) << 24);
        flv_tag.frame.putInt(tag_size);
        // Timestamp UI24
        // TimestampExtended UI8
        int time = (dts << 8) & 0xFFFFFF00 | ((dts >> 24) & 0x000000FF);
        flv_tag.frame.putInt(time);
        // StreamID UI24 Always 0.
        flv_tag.frame.put((byte) 0);
        flv_tag.frame.put((byte) 0);
        flv_tag.frame.put((byte) 0);

        // @see: E.4.3 Video Tags, video_file_format_spec_v10_1.pdf, page 78
        // Frame Type, Type of video frame.
        // CodecID, Codec Identifier.
        // set the rtmp header
        flv_tag.frame.put((byte) ((frame_type << 4) | KSYCodecVideo.AVC));

        // AVCPacketType
        flv_tag.frame.put((byte) avc_packet_type);

        // CompositionTime
        // pts = dts + cts, or
        // cts = pts - dts.
        // where cts is the header in rtmp video packet payload header.
        int cts = pts - dts;
        flv_tag.frame.put((byte) (cts >> 16));
        flv_tag.frame.put((byte) (cts >> 8));
        flv_tag.frame.put((byte) cts);

        // h.264 raw data.
        for (int i = 0; i < frames.size(); i++) {
            KSYFlvFrameBytes frame = frames.get(i);
            //flv_tag.frame.put(frame.frame.duplicate());
            byte[] frame_bytes = new byte[frame.size];
            frame.frame.get(frame_bytes);
            flv_tag.frame.put(frame_bytes);
        }

        flv_tag.frame.putInt(flv_tag.size + 11);
        // reset the buffer.
        flv_tag.frame.rewind();

        //Log.i("guisheng", String.format("flv tag muxed, %dB", (flv_tag.size + 11 + 4)));
        //KSYHttpFlvClient.KSY_print_bytes("guisheng", flv_tag.frame, 128);

        return flv_tag;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public KSYFlvFrameBytes annexb_demux(ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {

        KSYFlvFrameBytes tbb = new KSYFlvFrameBytes();

        while (bb.position() < bi.size) {
            // each frame must prefixed by annexb format.
            // about annexb, @see H.264-AVC-ISO_IEC_14496-10.pdf, page 211.
            KSYAnnexbSearch tbbsc = utils.startswith_annexb(bb, bi);
            if (!tbbsc.match || tbbsc.nb_start_code < 3) {
                Log.e(Constants.LOG_TAG, "annexb not match.");
                KSYHttpFlvClient.KSY_print_bytes(Constants.LOG_TAG, bb, 16);
                throw new Exception(String.format("annexb not match for %dB, pos=%d", bi.size, bb.position()));
            }

            // the start codes.
            ByteBuffer tbbs = bb.slice();
            for (int i = 0; i < tbbsc.nb_start_code; i++) {
                bb.get();
            }

            // find out the frame size.
            tbb.frame = bb.slice();
            int pos = bb.position();
            while (bb.position() < bi.size) {
                KSYAnnexbSearch bsc = utils.startswith_annexb(bb, bi);
                if (bsc.match) {
                    break;
                }
                bb.get();
            }

            tbb.size = bb.position() - pos;
            if (bb.position() < bi.size) {
                Log.i(Constants.LOG_TAG, String.format("annexb multiple match ok, pts=%d", bi.presentationTimeUs));
                KSYHttpFlvClient.KSY_print_bytes(Constants.LOG_TAG, tbbs, 16);
                KSYHttpFlvClient.KSY_print_bytes(Constants.LOG_TAG, bb.slice(), 16);
            }
            // Log.i(TAG, String.format("annexb match %d bytes", tbb.size));
            break;
        }

        return tbb;
    }

}
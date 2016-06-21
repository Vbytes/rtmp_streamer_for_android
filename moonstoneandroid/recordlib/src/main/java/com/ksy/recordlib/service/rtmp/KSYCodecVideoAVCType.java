package com.ksy.recordlib.service.rtmp;

//AVCPacketType IF CodecID == 7 UI8
// The following values are defined:
// 0 = AVC sequence header
// 1 = AVC NALU
// 2 = AVC end of sequence (lower level NALU sequence ender is
// not required or supported)
public class KSYCodecVideoAVCType {

	// set to the max value to reserved, for array map.
	public final static int Reserved = 3;

	public final static int SequenceHeader = 0;
	public final static int NALU = 1;
	public final static int SequenceHeaderEOF = 2;

}

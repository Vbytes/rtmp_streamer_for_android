package com.ksy.recordlib.service.rtmp;

//E.4.3.1 VIDEODATA
// CodecID UB [4]
// Codec Identifier. The following values are defined:
// 2 = Sorenson H.263
// 3 = Screen video
// 4 = On2 VP6
// 5 = On2 VP6 with alpha channel
// 6 = Screen video version 2
// 7 = AVC
public class KSYCodecVideo
{

	// set to the zero to reserved, for array map.
	public final static int Reserved = 0;
	public final static int Reserved1 = 1;
	public final static int Reserved2 = 9;

	// for user to disable video, for example, use pure audio hls.
	public final static int Disabled = 8;

	public final static int SorensonH263 = 2;
	public final static int ScreenVideo = 3;
	public final static int On2VP6 = 4;
	public final static int On2VP6WithAlphaChannel = 5;
	public final static int ScreenVideoVersion2 = 6;
	public final static int AVC = 7;
}
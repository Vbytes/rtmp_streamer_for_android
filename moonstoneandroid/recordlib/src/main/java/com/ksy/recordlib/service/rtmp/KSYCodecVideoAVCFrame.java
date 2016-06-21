package com.ksy.recordlib.service.rtmp;

// E.4.3.1 VIDEODATA
// Frame Type UB [4]
// Type of video frame. The following values are defined:
// 1 = key frame (for AVC, a seekable frame)
// 2 = inter frame (for AVC, a non-seekable frame)
// 3 = disposable inter frame (H.263 only)
// 4 = generated key frame (reserved for server use only)
// 5 = video info/command frame
public class KSYCodecVideoAVCFrame
{

	// set to the zero to reserved, for array map.
	public final static int Reserved = 0;
	public final static int Reserved1 = 6;

	public final static int KeyFrame = 1;
	public final static int InterFrame = 2;
	public final static int DisposableInterFrame = 3;
	public final static int GeneratedKeyFrame = 4;
	public final static int VideoInfoFrame = 5;
}
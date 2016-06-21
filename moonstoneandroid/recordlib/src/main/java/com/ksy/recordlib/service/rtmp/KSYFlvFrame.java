package com.ksy.recordlib.service.rtmp;

public class KSYFlvFrame {

	// the tag bytes.
	public KSYFlvFrameBytes tag;
	// the codec type for audio/aac and video/avc for instance.
	public int avc_aac_type;
	// the frame type, keyframe or not.
	public int frame_type;
	// the tag type, audio, video or data.
	public int type;
	// the dts in ms, tbn is 1000.
	public int dts;

	public boolean is_keyframe() {

		return type == KSYCodecFlvTag.Video && frame_type == KSYCodecVideoAVCFrame.KeyFrame;
	}

	public boolean is_video() {

		return type == KSYCodecFlvTag.Video;
	}

	public boolean is_audio() {

		return type == KSYCodecFlvTag.Audio;
	}

}

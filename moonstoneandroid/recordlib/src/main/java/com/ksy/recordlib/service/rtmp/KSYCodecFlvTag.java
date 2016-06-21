package com.ksy.recordlib.service.rtmp;

/**
 * E.4.1 FLV Tag, page 75
 */
public class KSYCodecFlvTag
{

	// set to the zero to reserved, for array map.
	public final static int Reserved = 0;

	// 8 = audio
	public final static int Audio = 8;
	// 9 = video
	public final static int Video = 9;
	// 18 = script data
	public final static int Script = 18;
};
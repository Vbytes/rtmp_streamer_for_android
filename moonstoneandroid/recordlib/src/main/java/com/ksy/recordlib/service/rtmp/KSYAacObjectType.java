package com.ksy.recordlib.service.rtmp;

/**
 * the aac object type, for RTMP sequence header for AudioSpecificConfig, @see
 * aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 33 for audioObjectType, @see
 * aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
 */
public class KSYAacObjectType
{

	public final static int Reserved = 0;

	// Table 1.1 – Audio Object Type definition
	// @see @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf, page 23
	public final static int AacMain = 1;
	public final static int AacLC = 2;
	public final static int AacSSR = 3;

	// AAC HE = LC+SBR
	public final static int AacHE = 5;
	// AAC HEv2 = LC+SBR+PS
	public final static int AacHEV2 = 29;
}
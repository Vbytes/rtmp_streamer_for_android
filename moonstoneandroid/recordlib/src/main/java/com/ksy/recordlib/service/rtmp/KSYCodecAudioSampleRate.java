package com.ksy.recordlib.service.rtmp;

/**
 * the FLV/RTMP supported audio sample rate. Sampling rate. The following values
 * are defined: 0 = 5.5 kHz = 5512 Hz 1 = 11 kHz = 11025 Hz 2 = 22 kHz = 22050
 * Hz 3 = 44 kHz = 44100 Hz
 */
public class KSYCodecAudioSampleRate
{

	// set to the max value to reserved, for array map.
	public final static int Reserved = 4;

	public final static int R5512 = 0;
	public final static int R11025 = 1;
	public final static int R22050 = 2;
	public final static int R44100 = 3;
}
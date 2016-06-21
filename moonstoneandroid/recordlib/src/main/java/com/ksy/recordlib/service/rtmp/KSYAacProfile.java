package com.ksy.recordlib.service.rtmp;

/**
 * the aac profile, for ADTS(HLS/TS)
 * 
 * @see https://github.com/simple-rtmp-server/srs/issues/310
 */
public class KSYAacProfile
{

	public final static int Reserved = 3;

	// @see 7.1 Profiles, aac-iso-13818-7.pdf, page 40
	public final static int Main = 0;
	public final static int LC = 1;
	public final static int SSR = 2;
}
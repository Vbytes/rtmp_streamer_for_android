package com.ksy.recordlib.service.rtmp;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.ksy.recordlib.service.util.Constants;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by winlin on 5/2/15. to POST the h.264/avc annexb frame to SRS over
 * HTTP FLV.
 * 
 * @remark we must start a worker thread to send data to server.
 * @see android.media.MediaMuxer
 *      https://developer.android.com/reference/android/media/MediaMuxer.html
 */
public class KSYHttpFlvClient {

	private final String url;
	private HttpURLConnection conn;
	private BufferedOutputStream bos;

	private Thread worker;
	private Looper looper;
	private Handler handler;

	private final KSYFlv flv;
	private boolean sequenceHeaderOk;
	private KSYFlvFrame videoSequenceHeader;
	private KSYFlvFrame audioSequenceHeader;

	// use cache queue to ensure audio and video monotonically increase.
	private final ArrayList<KSYFlvFrame> cache;
	private int nb_videos;
	private int nb_audios;

	private static final int VIDEO_TRACK = 100;
	private static final int AUDIO_TRACK = 101;

	/**
	 * constructor.
	 * 
	 * @param path
	 *            the http flv url to post to.
	 * @param format
	 *            the mux format, @see SrsHttpFlv.OutputFormat
	 */
	public KSYHttpFlvClient(String path, int format) {

		nb_videos = 0;
		nb_audios = 0;
		sequenceHeaderOk = false;

		url = path;
		flv = new KSYFlv();
		cache = new ArrayList<KSYFlvFrame>();
	}

	/**
	 * print the size of bytes in bb
	 * 
	 * @param bb
	 *            the bytes to print.
	 * @param size
	 *            the total size of bytes to print.
	 */
	public static void KSY_print_bytes(String tag, ByteBuffer bb, int size) {

		StringBuilder sb = new StringBuilder();
		int i = 0;
		int bytes_in_line = 16;
		int max = bb.remaining();
		for (i = 0; i < size && i < max; i++) {
			sb.append(String.format("0x%s ", Integer.toHexString(bb.get(i) & 0xFF)));
			if (((i + 1) % bytes_in_line) == 0) {
				Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
				sb = new StringBuilder();
			}
		}
		if (sb.length() > 0) {
			Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
		}
	}

	public static void KSY_print_bytes(String tag, byte[] bb, int size) {

		StringBuilder sb = new StringBuilder();
		int i = 0;
		int bytes_in_line = 16;
		int max = bb.length;
		for (i = 0; i < size && i < max; i++) {
			sb.append(String.format("0x%s ", Integer.toHexString(bb[i] & 0xFF)));
			if (((i + 1) % bytes_in_line) == 0) {
				Log.i(tag, String.format("%03d-%03d: %s", i / bytes_in_line * bytes_in_line, i, sb.toString()));
				sb = new StringBuilder();
			}
		}
		if (sb.length() > 0) {
			Log.i(tag, String.format("%03d-%03d: %s", size / bytes_in_line * bytes_in_line, i - 1, sb.toString()));
		}
	}

	/**
	 * Adds a track with the specified format.
	 * 
	 * @param format
	 *            The media format for the track.
	 * @return The track index for this newly added track.
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public int addTrack(MediaFormat format) {

		if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) {
			flv.setVideoTrack(format);
			return VIDEO_TRACK;
		}
		flv.setAudioTrack(format);
		return AUDIO_TRACK;
	}

	/**
	 * start to the remote SRS for remux.
	 */
	public void start() throws IOException {

		worker = new Thread(new Runnable() {

			@Override
			public void run() {

				try {
					cycle();
				} catch (InterruptedException ie) {
				} catch (Exception e) {
					Log.i(Constants.LOG_TAG, "worker: thread exception.");
					e.printStackTrace();
				}
			}
		});
		worker.start();
	}

	/**
	 * Make sure you call this when you're done to free up any resources instead
	 * of relying on the garbage collector to do this for you at some point in
	 * the future.
	 */
	public void release() {

		stop();
	}

	/**
	 * stop the muxer, disconnect HTTP connection from SRS.
	 */
	public void stop() {

		clearCache();

		if (worker == null && conn == null) {
			return;
		}

		if (looper != null) {
			looper.quit();
		}

		if (worker != null) {
			worker.interrupt();
			try {
				worker.join();
			} catch (InterruptedException e) {
				Log.i(Constants.LOG_TAG, "worker: join thread failed.");
				e.printStackTrace();
				worker.stop();
			}
			worker = null;
		}
		if (conn != null) {
			conn.disconnect();
			conn = null;
		}
		Log.i(Constants.LOG_TAG, String.format("worker: muxer closed, url=%s", url));
	}

	/**
	 * send the annexb frame to SRS over HTTP FLV.
	 * 
	 * @param trackIndex
	 *            The track index for this sample.
	 * @param byteBuf
	 *            The encoded sample.
	 * @param bufferInfo
	 *            The buffer information related to this sample.
	 */

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) throws Exception {

		// Log.i(TAG, String.format("dumps the %s stream %dB, pts=%d",
		// (trackIndex == VIDEO_TRACK) ? "Vdieo" : "Audio", bufferInfo.size,
		// bufferInfo.presentationTimeUs / 1000));
		// SrsHttpFlv.srs_print_bytes(TAG, byteBuf, bufferInfo.size);

		if (bufferInfo.offset > 0) {
			Log.w(Constants.LOG_TAG, String.format("encoded frame %dB, offset=%d pts=%dms", bufferInfo.size, bufferInfo.offset, bufferInfo.presentationTimeUs));
		}

		if (VIDEO_TRACK == trackIndex) {
			flv.writeVideoSample(byteBuf, bufferInfo);
		} else {
			flv.writeAudioSample(byteBuf, bufferInfo);
		}
	}

	private void disconnect() {

		clearCache();

		if (bos == null && conn == null) {
			return;
		}

		if (bos != null) {
			try {
				bos.close();
			} catch (IOException e) {
			}
			bos = null;
		}

		if (conn != null) {
			conn.disconnect();
			conn = null;
		}
		Log.i(Constants.LOG_TAG, "worker: disconnect SRS ok.");
	}

	private void clearCache() {

		nb_videos = 0;
		nb_audios = 0;
		cache.clear();
		sequenceHeaderOk = false;
	}

	private void reconnect() throws Exception {

		// when bos not null, already connected.
		if (bos != null) {
			return;
		}

		disconnect();
		URL u = new URL(url);
		conn = (HttpURLConnection) u.openConnection();
		int i = conn.getResponseCode();
		Log.i(Constants.LOG_TAG, String.format("worker: connect to SRS by url=%s", url));
		conn.setDoOutput(true);
		// conn.setChunkedStreamingMode(0);
		// conn.setRequestProperty("Content-Type", "application/octet-stream");
		bos = new BufferedOutputStream(conn.getOutputStream());
		Log.i(Constants.LOG_TAG, String.format("worker: muxer opened, url=%s", url));

		// write 13B header
		// 9bytes header and 4bytes first previous-tag-size
		byte[] flv_header = new byte[] {
				'F', 'L', 'V', // Signatures "FLV"
				(byte) 0x01, // File version (for example, 0x01 for FLV version
				// 1)
				(byte) 0x00, // 4, audio; 1, video; 5 audio+video.
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, // DataOffset
				// UI32 The
				// length of
				// this
				// header in
				// bytes
				(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
		};
		bos.write(flv_header);
		bos.flush();
		Log.i(Constants.LOG_TAG, String.format("worker: flv header ok."));

		clearCache();

	}

	// private void reconnect() throws Exception {
	//
	// // when bos not null, already connected.
	// if (bos != null) {
	// return;
	// }
	//
	// disconnect();
	//
	// URL u = new URL(url);
	// // conn = (HttpURLConnection) u.openConnection();
	//
	// Log.i(Constants.TAG, String.format("worker: connect to SRS by url=%s",
	// url));
	// // conn.setDoOutput(true);
	// // conn.setChunkedStreamingMode(0);
	// // conn.setRequestProperty("Content-Type", "application/octet-stream");
	// // bos = new BufferedOutputStream(conn.getOutputStream());
	// // Log.i(Constants.TAG, String.format("worker: muxer opened, url=%s",
	// // url));
	//
	// // write 13B header
	// // 9bytes header and 4bytes first previous-tag-size
	// File file = new File(Environment.getExternalStorageDirectory(),
	// "test.flv");
	// bos = new BufferedOutputStream(new FileOutputStream(file));
	//
	// byte[] flv_header = new byte[] {
	// 'F', 'L', 'V', // Signatures "FLV"
	// (byte) 0x01, // File version (for example, 0x01 for FLV version
	// // 1)
	// (byte) 0x00, // 4, audio; 1, video; 5 audio+video.
	// (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, // DataOffset
	// // UI32 The
	// // length of
	// // this
	// // header in
	// // bytes
	// (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
	// };
	// bos.write(flv_header);
	// bos.flush();
	// Log.i(Constants.TAG, String.format("worker: flv header ok."));
	//
	// clearCache();
	//
	// }

	private void cycle() throws Exception {

		// create the handler.
		Looper.prepare();
		looper = Looper.myLooper();
		handler = new Handler(looper) {

			@Override
			public void handleMessage(Message msg) {

				if (msg.what != SrsMessageType.FLV) {
					Log.w(Constants.LOG_TAG, String.format("worker: drop unkown message, what=%d", msg.what));
					return;
				}
				KSYFlvFrame frame = (KSYFlvFrame) msg.obj;
				try {
					// only reconnect when got keyframe.
					if (frame.is_keyframe()) {
						reconnect();
					}
				} catch (Exception e) {
					Log.e(Constants.LOG_TAG, String.format("worker: reconnect failed. e=%s", e.getMessage()));
					disconnect();
				}

				try {
					// when sequence header required,
					// adjust the dts by the current frame and sent it.
					if (!sequenceHeaderOk && bos != null) {
						if (videoSequenceHeader != null) {
							videoSequenceHeader.dts = frame.dts;
						}
						if (audioSequenceHeader != null) {
							audioSequenceHeader.dts = frame.dts;
						}

						sendFlvTag(bos, audioSequenceHeader);
						sendFlvTag(bos, videoSequenceHeader);
						sequenceHeaderOk = true;
					}

					// try to send, igore when not connected.
					if (sequenceHeaderOk && bos != null) {
						sendFlvTag(bos, frame);
					}

					// cache the sequence header.
					if (frame.type == KSYCodecFlvTag.Video && frame.avc_aac_type == KSYCodecVideoAVCType.SequenceHeader) {
						videoSequenceHeader = frame;
					} else if (frame.type == KSYCodecFlvTag.Audio && frame.avc_aac_type == 0) {
						audioSequenceHeader = frame;
					}
				} catch (Exception e) {
					e.printStackTrace();
					Log.e(Constants.LOG_TAG, String.format("worker: send flv tag failed, e=%s", e.getMessage()));
					disconnect();
				}
			}
		};
		flv.setHandler(handler);

		Looper.loop();
	}

	private void sendFlvTag(BufferedOutputStream bos, KSYFlvFrame frame) throws IOException {

		if (frame == null) {
			return;
		}

		if (frame.tag.size <= 0) {
			return;
		}

		if (frame.is_video()) {
			nb_videos++;
		} else if (frame.is_audio()) {
			nb_audios++;
		}
		cache.add(frame);

		// always keep one audio and one videos in cache.
		if (nb_videos > 1 && nb_audios > 1) {
			sendCachedFrames();
		}
	}

	private void sendCachedFrames() throws IOException {

		Collections.sort(cache, new Comparator<KSYFlvFrame>() {

			@Override
			public int compare(KSYFlvFrame lhs, KSYFlvFrame rhs) {

				return lhs.dts - rhs.dts;
			}
		});

		while (nb_videos > 1 && nb_audios > 1) {
			KSYFlvFrame frame = cache.remove(0);

			if (frame.is_video()) {
				nb_videos--;
			} else if (frame.is_audio()) {
				nb_audios--;
			}

			if (frame.is_keyframe()) {
				Log.i(Constants.LOG_TAG, String.format("worker: got frame type=%d, dts=%d, size=%dB, videos=%d, audios=%d",
						frame.type, frame.dts, frame.tag.size, nb_videos, nb_audios));
			} else {
				// Log.i(TAG,
				// String.format("worker: got frame type=%d, dts=%d, size=%dB, videos=%d, audios=%d",
				// frame.type, frame.dts, frame.tag.size, nb_videos,
				// nb_audios));
			}

			// write the 11B flv tag header
			ByteBuffer th = ByteBuffer.allocate(11);
			// Reserved UB [2]
			// Filter UB [1]
			// TagType UB [5]
			// DataSize UI24
			int tag_size = (frame.tag.size & 0x00FFFFFF) | ((frame.type & 0x1F) << 24);
			th.putInt(tag_size);
			// Timestamp UI24
			// TimestampExtended UI8
			int time = (frame.dts << 8) & 0xFFFFFF00 | ((frame.dts >> 24) & 0x000000FF);
			th.putInt(time);
			// StreamID UI24 Always 0.
			th.put((byte) 0);
			th.put((byte) 0);
			th.put((byte) 0);
			bos.write(th.array());

			// write the flv tag data.
			byte[] data = frame.tag.frame.array();
			bos.write(data, 0, frame.tag.size);

			// write the 4B previous tag size.
			// @remark, we append the tag size, this is different to SRS which
			// write RTMP packet.
			ByteBuffer pps = ByteBuffer.allocate(4);
			pps.putInt(frame.tag.size + 11);
			bos.write(pps.array());

			if (frame.is_keyframe()) {
				Log.i(Constants.LOG_TAG, String.format("worker: send frame type=%d, dts=%d, size=%dB, tag_size=%#x, time=%#x",
						frame.type, frame.dts, frame.tag.size, tag_size, time
						));
			}
		}

		bos.flush();
	}

	/**
	 * the supported output format for muxer.
	 */
	class OutputFormat {

		public final static int MUXER_OUTPUT_HTTP_FLV = 0;
	}

	/**
	 * the type of message to process.
	 */
	static class SrsMessageType {

		public final static int FLV = 0x100;
	}

}

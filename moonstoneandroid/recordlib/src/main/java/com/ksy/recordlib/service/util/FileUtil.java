package com.ksy.recordlib.service.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by eflakemac on 15/6/19.
 */
public class FileUtil {

    public static String getOutputMediaFile(Context context, int type) {
        // Check sdcard exist
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            Log.d(Constants.LOG_TAG, "sdcard not found");
//            return null;
        }
        // Use default picture location
        File mediaStorageDir = new File(context.getFilesDir(), "CameraSample");
        // Create if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(Constants.LOG_TAG, "failed to create picture directory");
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == Constants.MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == Constants.MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else if (type == Constants.MEDIA_TYPE_TXT) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "Log_" + timeStamp + ".txt");
        } else {
            return null;
        }
        return mediaFile.getAbsolutePath();
    }
}

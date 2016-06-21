package com.ksy.recordlib.service.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * Created by hansentian on 8/3/15.
 */
public class NetworkMonitor {

    private static String TAG = "NetworkMonitor";

    private static final int NETWORK_NONE = -1;
    private static int mNetworkType = NETWORK_NONE;
    private static Context context;

    private static BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateNetwork();
            }
        }
    };

    /**
     * start network monitor
     *
     * @param pContext should be application context
     */
    public static void start(Context pContext) {
        context = pContext;
        IntentFilter networkFilter = new IntentFilter();
        networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(mReceiver, networkFilter);
        updateNetwork();
    }

    public static boolean networkConnected() {
        return (mNetworkType != NETWORK_NONE);
    }

    public static boolean mobileNetwork() {
        return (mNetworkType == ConnectivityManager.TYPE_MOBILE);
    }

    public static boolean wifiNetwork() {
        return (mNetworkType == ConnectivityManager.TYPE_WIFI);
    }

    public static void updateNetwork() {
        int oldType = mNetworkType;
        mNetworkType = NETWORK_NONE;
        do {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                break;
            }

            NetworkInfo networkInfo = manager.getActiveNetworkInfo();
            if (networkInfo == null) {
                break;
            }

            int type = networkInfo.getType();
            boolean connected = networkInfo.isConnected();
            Log.e(TAG, "network [type] " + type + " [connected] " + connected);
            if (connected) {
                mNetworkType = type;
            }
        } while (false);
        if (mNetworkType != oldType) {
            Intent intent = new Intent(Constants.NETWORK_STATE_CHANGED);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    public interface OnNetworkPoorListener {

        static final int CACHE_QUEUE_MAX = 11;
        static final int FRAME_SEND_TOO_LONG = 21;

        void onNetworkPoor(int source);

    }

}

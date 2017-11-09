package com.via.avi;

import com.via.avi.utils.AndroidInternetChecker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ConnectivityReceiver extends BroadcastReceiver{
  private static String TAG = "ConnectivityReceiver";
  private AviInterface mApp;
  private AndroidInternetChecker mConnChecker;

  private boolean flagPreviousConnectivityStatus = false;

  public ConnectivityReceiver(AviInterface app, 
      AndroidInternetChecker connChecker) {
    mApp = app;
    mConnChecker = connChecker;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "in onReceive");
    Log.d(TAG, "previousConnectivityStatus = "
        + flagPreviousConnectivityStatus);

    boolean flagCurrentConnectivityStatus = mConnChecker.isInternetConnected();
    Log.d(TAG, "currentConnectivityStatus = "
        + flagCurrentConnectivityStatus);
    if (!flagCurrentConnectivityStatus) {
      flagPreviousConnectivityStatus = false;
      mApp.onDisconnect();
    }
    if (flagCurrentConnectivityStatus && !flagPreviousConnectivityStatus) {
      // if reconnected, clear message queue
      flagPreviousConnectivityStatus = true;
      mApp.onConnect();
    }
  }
}

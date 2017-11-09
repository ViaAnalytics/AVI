package com.via.avi;

import com.via.avi.utils.Util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ForegroundCheckReceiver extends BroadcastReceiver{
  private static String TAG = "ForegroundCheckReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "Performing foreground check");
    Intent i = new Intent();
    i.setClass(context, AviActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Log.i(TAG, "Intent to start activity: " + i);
    // no need to check whether activity already on foreground, because
    // launchMode is set to singleInstance in the manifest
    if (!Util.Debug) {
      // only bring activity to foreground if we're not in debug mode
      context.getApplicationContext().startActivity(i);
    }
  }
}

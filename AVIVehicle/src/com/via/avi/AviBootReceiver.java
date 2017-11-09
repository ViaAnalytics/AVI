package com.via.avi;

import com.via.avi.utils.Util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AviBootReceiver extends BroadcastReceiver{
  String TAG = "AviBootReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "onReceive ACTION_BOOT_COMPLETED");
    if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

      // Start AviActivity
      if (!Util.Debug) {
        Intent i2 = new Intent(context, AviActivity.class);
        i2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i2);
      }
    }
  }
}

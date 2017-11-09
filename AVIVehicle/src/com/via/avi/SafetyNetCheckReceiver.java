package com.via.avi;

import com.stericson.RootTools.RootTools;
import com.via.avi.utils.Util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SafetyNetCheckReceiver extends BroadcastReceiver {
  private static String TAG = "SafetyNetCheckReceiver";
  private boolean haveRoot;

  public SafetyNetCheckReceiver() {
    haveRoot = RootTools.isAccessGiven();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "Ensuring that SafetyNet is active at " 
        + Util.getCurrentTimeWithGpsOffset() + ".");

    ComponentName cn = new ComponentName(Util.SafetyNetPackage,
        Util.SafetyNetPackage + "." + Util.SafetyNetService);
    String cnStr = cn.flattenToString();

    boolean mServiceActive = false;
    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    if (am == null) {
      // this is a bug -- should be temporary
      return;
    }

    for (RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
      if (cnStr.equals(service.service.flattenToString())) {
        Log.d(TAG, "SafetyNet service running.");
        mServiceActive = true;
      }
    }
    if (!mServiceActive) {
      if (!haveRoot) {
        Log.w(TAG, "SafetyNet service not active, start it without root");
        Intent i = new Intent();
        i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        i.setComponent(cn);
        context.startService(i);
      } else {
        Log.d(TAG,"SafetyNet service not active, starting it with root");
        String cmd = "am startservice " + cnStr;
        executeSudoCmd(cmd, false);
      }
    }
  }


  private boolean executeSudoCmd(String cmd, boolean waitFor) {
    try {
      Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
      if (waitFor) proc.waitFor();
    } catch (Exception e) {
      Log.e(TAG, "Failed to execute sudo command!", e);
      return false;
    }
    return true;
  }

}

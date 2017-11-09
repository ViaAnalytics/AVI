package com.via.avi.utils;

import android.util.Log;

import com.via.avi.DeviceManager;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;

public class InternetManager extends DefaultThreadLooper {
  private static String TAG = "InternetManager";
  public static long CheckMillis = 5000;

  private AndroidInternetChecker icc;
  private DeviceManager dm;
  private long lastCheckTime = 0l;
  private long lastCommTime = 0l;
  private long commRebootAge;

  public InternetManager(AndroidInternetChecker icc, DeviceManager dm,
      long commRebootAge) {
    super(TAG, CheckMillis);
    this.icc = icc;
    this.dm = dm;
    this.commRebootAge = commRebootAge;
  }

  @Override
  public void runOnce() {
    DeviceState ds = UpdatableGlobalState.getInstance().getDeviceState();
    if (icc.isInternetConnected()) {
      ds.setLastCommTime(Util.getCurrentTimeWithGpsOffset());
    }

    long tNow = Util.getCurrentTimeWithGpsOffset();
    long realLastCommTime = ds.getLastCommTime();
    if (tNow - lastCheckTime > CheckMillis*5) {
      // just woke up -- don't penalize for "asleep" time
      lastCommTime = tNow;
      Log.i(TAG, "Initializing lastCommTime to " + lastCommTime);
    } else if (realLastCommTime > lastCommTime) {
      lastCommTime = realLastCommTime;
    }

    long commAge = tNow - lastCommTime;
    if (commAge > commRebootAge) {
      // GPS too old! Reboot.
      Log.w(TAG, "Conn age " + commAge/1000 + " sec too old! Rebooting.");
      dm.receivedRebootOrder();
    }

    lastCheckTime = tNow;
  }
}

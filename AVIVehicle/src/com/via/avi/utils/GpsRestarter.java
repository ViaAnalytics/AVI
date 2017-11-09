package com.via.avi.utils;

import android.util.Log;

import com.via.avi.DeviceManager;
import com.via.avi.gs.UpdatableGlobalState;

public class GpsRestarter extends DefaultThreadLooper {
  private static String TAG = "GpsRestarter";
  public static long CheckMillis = 5000l;

  private DeviceManager dm;
  private long lastCheckTime = 0l;
  private long lastGpsTime = 0l;
  private long gpsRebootAge;

  public GpsRestarter(DeviceManager dm, long gpsRebootAge) {
    super(TAG, CheckMillis);
    this.dm = dm;
    this.gpsRebootAge = gpsRebootAge;
  }

  @Override
  public void runOnce() {      
    long tNow = Util.getCurrentTimeWithGpsOffset();
    long realLastGpsTime = UpdatableGlobalState.getInstance().getDeviceState().getLastGpsTime();
    if (tNow - lastCheckTime > CheckMillis*5) {
      // just woke up -- don't penalize for "asleep" time
      lastGpsTime = tNow;
      Log.i(TAG, "Initializing lastGpsTime to " + lastGpsTime);
    } else if (realLastGpsTime > lastGpsTime) {
      lastGpsTime = realLastGpsTime;
    }

    long gpsAge = tNow - lastGpsTime;
    if (gpsAge > gpsRebootAge) {
      // GPS too old! Reboot.
      Log.w(TAG, "GPS age " + gpsAge/1000 + " sec too old! Rebooting.");
      dm.receivedRebootOrder();
    }

    lastCheckTime = tNow;
  }
}

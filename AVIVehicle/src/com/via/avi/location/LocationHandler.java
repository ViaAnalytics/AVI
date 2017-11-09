package com.via.avi.location;

import android.location.Location;
import android.util.Log;

import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.utils.Util;

public class LocationHandler implements LocationHandlerInterface {
  private static String TAG = "LocationHandler";
  private UpdatableGlobalState globalState;

  public LocationHandler(UpdatableGlobalState globalState) {
    Log.d(TAG, "Initializing LocationHandler.");
    this.globalState = globalState;
  }

  public void onLocationChangedWrap(Location location) {
    // Record the Location from GPS
    // timestamp, lat, lon, accuracy, speed, bearing
    Long time = location.getTime(); 

    // Update global "last GPS time"
    DeviceState devState = globalState.getDeviceState();
    Long currGpsTime = devState.getLastGpsTime();
    if (currGpsTime == null || time > currGpsTime) {
      devState.setLastGpsTime(time);
    }

    Util.setGPSTimeOffset(System.currentTimeMillis() - time);
    Log.d(TAG, "Received new location at time " + location.getTime());

    globalState.getDeviceState().setCurrentLocation(location);
  }
}

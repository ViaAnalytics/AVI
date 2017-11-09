package com.via.avi.location;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class OldStyleLocationService implements LocationService {
  private static String TAG = "OldStyleLocationService";
  private LocationManager mLocationManager;
  private LocationListener mLocationListener;

  long minTime;
  float minDist;

  public OldStyleLocationService(Context context, 
      final LocationHandlerInterface lh, long minTime, float minDist) {
    this.minTime = minTime;
    this.minDist = minDist;
    Log.d(TAG, "Initializing old-style LocationService.");

    mLocationManager = (LocationManager) context
        .getSystemService(Context.LOCATION_SERVICE);

    // old-style location listener
    mLocationListener = new LocationListener() {
      @Override
      public void onStatusChanged(String provider, int status, Bundle extras) {
      }
      @Override
      public void onProviderEnabled(String provider) {
      }
      @Override
      public void onProviderDisabled(String provider) {
      }
      @Override
      public void onLocationChanged(Location location) {
        lh.onLocationChangedWrap(location);
      }
    };

  }

  @Override
  public void stopGPS() {
    mLocationManager.removeUpdates(mLocationListener);
  }

  @Override
  public void startGPS() {
    Log.d(TAG, "Requesting old-style GPS location updates.");
    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 
        minTime, minDist,
        mLocationListener);
  }

}

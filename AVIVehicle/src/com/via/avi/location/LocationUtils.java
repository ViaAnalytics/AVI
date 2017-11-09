package com.via.avi.location;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class LocationUtils {
  private LocationUtils() {}

  public static boolean googlePlayServicesConnected(Context context) {
    // Check that Google Play services is available
    int resultCode =
        GooglePlayServicesUtil.
        isGooglePlayServicesAvailable(context);
    // If Google Play services is available
    if (resultCode == ConnectionResult.SUCCESS) {
      // In debug mode, log the status
      Log.d("Location Updates",
          "Google Play services is available.");
      // Continue
      return true;
      // Google Play services was not available for some reason
    } else {
      return false;
    }
  }
}

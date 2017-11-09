package com.via.avi.location;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;

public class LocationClientConnectTask extends AsyncTask<Void, Void, Void> {
  private static String TAG = "LocationClientConnectTask";
  private GoogleApiClient googleApiClient;
  private boolean connect = false;

  public LocationClientConnectTask(boolean connect, GoogleApiClient googleApiClient) {
    this.connect = connect;
    this.googleApiClient = googleApiClient;
  }

  protected Void doInBackground(Void... args) {
    if (connect) {
      Log.i(TAG, "Connecting LocationClient");
      googleApiClient.connect();
    } else {
      Log.i(TAG, "Disconnecting LocationClient");
      googleApiClient.disconnect();
    }

    return null;
  }
}
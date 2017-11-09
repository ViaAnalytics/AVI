package com.via.avi.location;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class NewStyleLocationService implements LocationService, 
GoogleApiClient.ConnectionCallbacks, 
GoogleApiClient.OnConnectionFailedListener,
LocationListener {
  private static String TAG = "NewStyleLocationService";

  private GoogleApiClient mGoogleApiClient;
  private LocationHandlerInterface lh;
  private LocationRequest mLocationRequest;
  private long minTime;

  public NewStyleLocationService(Context context, long minTime,
      final LocationHandlerInterface lh) {

    Log.d(TAG, "Initializing the Google Play Services LocationService");

    this.lh = lh;
    this.minTime = minTime;

    mGoogleApiClient = new GoogleApiClient.Builder(context)
    .addApi(LocationServices.API)
    .addConnectionCallbacks(this)
    .addOnConnectionFailedListener(this)
    .build();
  }

  @Override
  public void stopGPS() {
    Log.d(TAG, "Cancelling GPS location updates.");
    LocationClientConnectTask task = new LocationClientConnectTask(false, mGoogleApiClient);
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void []) null);
  }

  @Override
  public void startGPS() {
    Log.d(TAG, "Requesting new-style GPS location updates.");
    LocationClientConnectTask task = new LocationClientConnectTask(true, mGoogleApiClient);
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void []) null);
  }

  @Override
  public void onConnectionFailed(ConnectionResult result) {
    Log.i(TAG, "GoogleApiClient connection has failed");
  }

  @Override
  public void onConnected(Bundle connectionHint) {
    mLocationRequest = LocationRequest.create();
    mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    mLocationRequest.setInterval(minTime);
    mLocationRequest.setFastestInterval(minTime);

    LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, 
        mLocationRequest, this);
  }

  @Override
  public void onConnectionSuspended(int cause) {
    Log.i(TAG, "GoogleApiClient connection has been suspended");
  }

  @Override
  public void onLocationChanged(Location location) {
    lh.onLocationChangedWrap(location);
  }

}

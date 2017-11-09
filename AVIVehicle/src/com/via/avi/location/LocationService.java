package com.via.avi.location;

public interface LocationService {
  /**
   * Start requesting GPS location updates. Should be idempotent.
   */
  public void startGPS();

  /**
   * Stop requesting GPS location updates. Should be idempotent.
   */
  public void stopGPS();
}

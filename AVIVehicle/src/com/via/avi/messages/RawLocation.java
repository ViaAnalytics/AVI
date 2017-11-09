package com.via.avi.messages;

import android.location.Location;

public class RawLocation {
  private String deviceId;
  private Long time;
  private Location location;

  public RawLocation() {

  }

  public String getDeviceId() {
    return deviceId;
  }

  public RawLocation setDeviceId(String deviceId) {
    this.deviceId = deviceId;
    return this;
  }

  public Long getTime() {
    return time;
  }

  public RawLocation setTime(Long time) {
    this.time = time;
    return this;
  }

  public Location getLocation() {
    return location;
  }

  public RawLocation setLocation(Location location) {
    this.location = location;
    return this;
  }

  public boolean initialized() {
    return (deviceId != null && time != null && location != null);
  }
}

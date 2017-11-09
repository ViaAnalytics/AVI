package com.via.avi.gs;

import android.location.Location;

public class DeviceState {

  private String deviceId;
  private String appVersion;

  private int batteryLevel;
  private int batteryChargingStatus;
  private int batteryPlugStatus;
  private int batteryScale;
  private int batteryTemperature;
  private int batteryHealthStatus;

  private long lastGpsTime = 0l;
  private long lastCommTime = 0l;

  private Location currentLocation;

  public DeviceState() {
  }

  public DeviceState(DeviceState deviceStateGiven) {
    this.deviceId = deviceStateGiven.getDeviceId();
    this.appVersion = deviceStateGiven.getAppVersion();

    this.batteryLevel = deviceStateGiven.getBatteryLevel();
    this.batteryChargingStatus = deviceStateGiven.getBatteryChargingStatus();
    this.batteryHealthStatus = deviceStateGiven.getBatteryHealthStatus();
    this.batteryPlugStatus = deviceStateGiven.getBatteryPlugStatus();
    this.batteryScale = deviceStateGiven.getBatteryScale();
    this.batteryTemperature = deviceStateGiven.getBatteryTemperature();

    this.lastGpsTime = deviceStateGiven.getLastGpsTime();
    this.lastCommTime = deviceStateGiven.getLastCommTime();

    this.currentLocation = deviceStateGiven.getCurrentLocationCopy();
  }

  public void clear() {
    synchronized (UpdatableGlobalState.class) {
      deviceId = null;
      appVersion = null;

      batteryLevel = 0;
      batteryChargingStatus = 0;
      batteryPlugStatus = 0;
      batteryHealthStatus = 0;
      batteryScale = 0;
      batteryTemperature = 0;

      currentLocation = null;
    }
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    synchronized (UpdatableGlobalState.class) {
      this.deviceId = deviceId;
    }
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    synchronized (UpdatableGlobalState.class) {
      this.appVersion = appVersion;
    }
  }

  public int getBatteryLevel() {
    return batteryLevel;
  }

  public void setBatteryLevel(int batteryLevel) {
    synchronized (UpdatableGlobalState.class) {
      this.batteryLevel = batteryLevel;
    }
  }

  public int getBatteryChargingStatus() {
    return batteryChargingStatus;
  }

  public void setBatteryChargingStatus(int batteryChargingStatus) {
    synchronized (UpdatableGlobalState.class) {
      this.batteryChargingStatus = batteryChargingStatus;
    }
  }

  public int getBatteryPlugStatus() {
    return batteryPlugStatus;
  }

  public void setBatteryPlugStatus(int batteryPlugStatus) {
    synchronized (UpdatableGlobalState.class) {
      this.batteryPlugStatus = batteryPlugStatus;
    }
  }

  public int getBatteryHealthStatus() {
    return batteryHealthStatus;
  }

  public void setBatteryHealthStatus(int batteryHealthStatus) {
    synchronized (UpdatableGlobalState.class) {
      this.batteryHealthStatus = batteryHealthStatus;
    }
  }

  public int getBatteryScale() {
    return batteryScale;
  }

  public void setBatteryScale(int batteryScale) {
    synchronized (UpdatableGlobalState.class) {
      this.batteryScale = batteryScale;
    }
  }

  public int getBatteryTemperature() {
    return batteryTemperature;
  }

  public void setBatteryTemperature(int batteryTemperature) {
    synchronized (UpdatableGlobalState.class) {
      this.batteryTemperature = batteryTemperature;
    }
  }

  public long getLastGpsTime() {
    return lastGpsTime;
  }

  public void setLastGpsTime(long lastGpsTime) {
    synchronized (UpdatableGlobalState.class){
      this.lastGpsTime = lastGpsTime;
    }
  }

  public long getLastCommTime() {
    return lastCommTime;
  }

  public void setLastCommTime(long lastCommTime) {
    synchronized (UpdatableGlobalState.class){
      this.lastCommTime = lastCommTime;
    }
  }

  public Location getCurrentLocation() {
    return currentLocation;
  }

  public Location getCurrentLocationCopy() {
    if (currentLocation == null) {
      return null;
    } else {
      return new Location(currentLocation);
    }
  }

  public void setCurrentLocation(Location currentLocation) {
    synchronized (UpdatableGlobalState.class){
      this.currentLocation = currentLocation;
    }
  }
}

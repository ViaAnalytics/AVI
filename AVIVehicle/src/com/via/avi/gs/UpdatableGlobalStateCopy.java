package com.via.avi.gs;

/**
 * This UpdatableGlobalStateCopy class keeps track of the global states that are
 * updated by listeners or broadcasters. UpdatableGlobalState (its subclass)
 * enforces a singleton instance. UpdatableGlobalStateCopy is passed to the main
 * event loop for processing.
 * 
 * @author EthanXuan
 * 
 */
public class UpdatableGlobalStateCopy {

  private DeviceState deviceState;

  public UpdatableGlobalStateCopy() {
    deviceState = new DeviceState();
  }

  public DeviceState getDeviceState() {
    return deviceState;
  }

  public DeviceState getDeviceStateCopy() {
    return new DeviceState(deviceState);
  }

  public void setDeviceState(DeviceState deviceState) {
    this.deviceState = deviceState;
  }

  public void clear() {
    this.deviceState.clear();
  }
}

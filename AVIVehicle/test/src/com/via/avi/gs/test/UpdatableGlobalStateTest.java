package com.via.avi.gs.test;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.gs.UpdatableGlobalStateCopy;

import android.location.Location;
import android.os.BatteryManager;

@RunWith(RobolectricTestRunner.class)
public class UpdatableGlobalStateTest extends TestCase {

  UpdatableGlobalState instance;
  UpdatableGlobalStateCopy copy, copy2;

  long time = System.currentTimeMillis();
  double latitude = 37.871667;
  double longitude = -122.272778;
  float speed = 10.5f;
  float bearing = 180.0f;

  String appVersion = "3.1";
  String deviceId = "1234567890abcdef";
  int batteryChargingStatus = BatteryManager.BATTERY_STATUS_CHARGING;
  int batteryLevel = 80;
  int batteryPlugStatus = BatteryManager.BATTERY_PLUGGED_AC;
  int batteryScale = 100;

  @Before
  public void setUp() {
    instance = UpdatableGlobalState.getInstance();
    setGlobalStateContent(instance);
  }

  @After
  public void tearDown() {
    instance.getDeviceState().clear();
    instance = null;
  }

  @Test
  public final void testSingletonInstance() {
    UpdatableGlobalState instance2 = UpdatableGlobalState.getInstance();
    assertTrue(instance == instance2);

    String appVersion = "fake_version";
    instance.getDeviceState().setAppVersion(appVersion);
    assertEquals(appVersion, instance2.getDeviceStateCopy().getAppVersion());
  }

  @Test
  public final void testClone() {
    copy = instance.clone();

    // same content
    assertEquals(instance.getDeviceStateCopy().getDeviceId(), copy.getDeviceStateCopy().getDeviceId());
    assertEquals(instance.getDeviceStateCopy().getAppVersion(), copy.getDeviceStateCopy().getAppVersion());
    assertEquals(instance.getDeviceStateCopy().getBatteryLevel(), copy.getDeviceStateCopy().getBatteryLevel());
    assertEquals(instance.getDeviceStateCopy().getBatteryChargingStatus(), copy.getDeviceStateCopy().getBatteryChargingStatus());
    assertEquals(instance.getDeviceStateCopy().getBatteryPlugStatus(), copy.getDeviceStateCopy().getBatteryPlugStatus());
    assertEquals(instance.getDeviceStateCopy().getBatteryScale(), copy.getDeviceStateCopy().getBatteryScale());

    // different physical address
    assertTrue(instance != copy);
    assertTrue(instance.getDeviceState() != copy.getDeviceState());
    assertTrue(instance.getDeviceState().getCurrentLocation() != copy.getDeviceState().getCurrentLocation());

    // test with setting different values
    String appVersionNew = "4.0";
    instance.getDeviceState().setAppVersion(appVersionNew);
    assertEquals(appVersion, copy.getDeviceStateCopy().getAppVersion());

    // test with another copy
    copy2 = instance.clone();
    assertTrue(instance != copy2);
    assertTrue(instance.getDeviceState() != copy2.getDeviceState());
    assertTrue(instance.getDeviceState().getCurrentLocation() != copy2.getDeviceState().getCurrentLocation());		
    assertTrue(copy != copy2);
    assertTrue(copy.getDeviceState() != copy2.getDeviceState());
    assertTrue(copy.getDeviceState().getCurrentLocation() != copy2.getDeviceState().getCurrentLocation());		
  }

  @Test
  public final void testDefensiveCopy_DeviceState() {
    DeviceState device = instance.getDeviceStateCopy();

    String appVersionNew = "4.0";
    device.setAppVersion(appVersionNew);
    assertEquals(appVersion, instance.getDeviceStateCopy().getAppVersion());
  }

  private void setGlobalStateContent(UpdatableGlobalState instance) {
    Location location = new Location("fake_provider");
    location.setTime(time);
    location.setLatitude(latitude);
    location.setLongitude(longitude);
    location.setSpeed(speed);
    location.setBearing(bearing);
    instance.getDeviceState().setCurrentLocation(location);

    instance.getDeviceState().setAppVersion(appVersion);
    instance.getDeviceState().setDeviceId(deviceId);
    instance.getDeviceState().setBatteryChargingStatus(batteryChargingStatus);
    instance.getDeviceState().setBatteryLevel(batteryLevel);
    instance.getDeviceState().setBatteryPlugStatus(batteryPlugStatus);
    instance.getDeviceState().setBatteryScale(batteryScale);
  }

}

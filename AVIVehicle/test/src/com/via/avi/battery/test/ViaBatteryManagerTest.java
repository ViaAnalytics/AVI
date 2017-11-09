package com.via.avi.battery.test;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import android.content.Intent;
import android.os.BatteryManager;

import com.via.avi.AviInterface;
import com.via.avi.DeviceManager;
import com.via.avi.battery.ViaBatteryManager;
import com.via.avi.config.ConfigValues;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.config.ConfigValues.CVKey;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.utils.Util;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class ViaBatteryManagerTest extends TestCase {
  private ViaBatteryManager batteryManager;

  private ConfigValues cv;
  private AviInterface app;
  private DeviceManager dm;

  private int status = BatteryManager.BATTERY_STATUS_DISCHARGING;
  private int plugged = BatteryManager.BATTERY_PLUGGED_AC;
  private int health = BatteryManager.BATTERY_HEALTH_GOOD;
  private int level;
  private int scale = 100;
  private int temperature = 350;

  private long initialTime = System.currentTimeMillis();
  
  private ConfigValues buildDefaultCv() {
    Properties prop = new Properties();
    prop.setProperty(CVKey.AGENCY.getKey(), "fakeagency");
    prop.setProperty(CVKey.TIME_ZONE.getKey(), "America/Los_Angeles");
    prop.setProperty(CVKey.PUSH_LINK_API_KEY.getKey(), "fake_key");
    try {
      return new ConfigValues(prop);
    } catch (MissingCVProperty e) {
      System.out.println("Shouldn't happen!");
    }
    return null;
  }

  @Before
  public void setUp() {
    UpdatableGlobalState.getInstance().clear();
    cv = buildDefaultCv();
    app = Mockito.mock(AviInterface.class);
    dm = Mockito.mock(DeviceManager.class);
    batteryManager = new ViaBatteryManager(cv, app, dm);

    level = (int) (cv.DischargingBatteryThreshold()*scale) - 2;
  }

  @After
  public void tearDown() {
    UpdatableGlobalState.getInstance().clear();
  }

  @Test
  public void testReboot() {
    long t1 = 1400000000000l;
    Util.setGPSTimeOffset(initialTime - t1);

    Intent i = prepareBatteryIntent(status, plugged, health, level, scale,
        temperature);
    batteryManager.processBatteryIntent(i);

    long dt = cv.LowBatteryDischargingRateTimeThreshold() + 1000l;
    double dischargeRate = cv.LowBatteryDischargingRateLimit();
    int newLevel = (int) (level + dischargeRate*dt) - 1;

    Util.setGPSTimeOffset(initialTime - (t1 + dt));

    Intent i2 = prepareBatteryIntent(status, plugged, health, newLevel, scale,
        temperature);
    batteryManager.processBatteryIntent(i2);

    Mockito.verify(dm).receivedRebootOrder();
  }

  @Test
  public void testSetGlobalValues() {
    Intent i = prepareBatteryIntent(status, plugged, health, level, scale,
        temperature);
    batteryManager.processBatteryIntent(i);

    DeviceState devState = UpdatableGlobalState.getInstance().getDeviceState();
    assertEquals(devState.getBatteryChargingStatus(), status);
    assertEquals(devState.getBatteryPlugStatus(), plugged);
    assertEquals(devState.getBatteryHealthStatus(), health);
    assertEquals(devState.getBatteryLevel(), level);
    assertEquals(devState.getBatteryScale(), scale);
    assertEquals(devState.getBatteryTemperature(), temperature);
  }

  private Intent prepareBatteryIntent(int status, int plugged, int health,
      int level, int scale, int temperature) {
    Intent intent = new Intent();
    intent.putExtra(BatteryManager.EXTRA_STATUS, status);
    intent.putExtra(BatteryManager.EXTRA_PLUGGED, plugged);
    intent.putExtra(BatteryManager.EXTRA_HEALTH, health);
    intent.putExtra(BatteryManager.EXTRA_LEVEL, level);
    intent.putExtra(BatteryManager.EXTRA_SCALE, scale);
    intent.putExtra(BatteryManager.EXTRA_TEMPERATURE, temperature);
    return intent;
  }
}

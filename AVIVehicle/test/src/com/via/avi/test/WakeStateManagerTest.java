package com.via.avi.test;

import java.util.Properties;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import android.os.BatteryManager;
import android.util.Log;

import com.via.avi.AviActivity;
import com.via.avi.WakeStateManager;
import com.via.avi.config.ConfigValues;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.config.ConfigValues.CVKey;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.gs.UpdatableGlobalStateCopy;

@RunWith(RobolectricTestRunner.class)
public class WakeStateManagerTest extends TestCase {
  private static String TAG = "WakeStateManagerTest";

  WakeStateManager wakeStateManager;
  AviActivity mockedApp;
  ConfigValues cv;
  String timeZone = "Europe/Amsterdam";
  
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
    //    ShadowLog.stream = System.out;
    cv = buildDefaultCv();
    UpdatableGlobalState.getInstance().clear();
  }

  @After
  public void tearDown() {
    wakeStateManager = null;
    mockedApp = null;
  }

  @Test
  public void wakeUp() {
    Log.d(TAG, "wakeUp()");
    int level0 = 100;
    int plug = BatteryManager.BATTERY_PLUGGED_AC;
    int status = BatteryManager.BATTERY_STATUS_CHARGING;
    int scale = 100;

    UpdatableGlobalStateCopy gS = UpdatableGlobalState.getInstance();
    DeviceState devState = gS.getDeviceState();
    devState.setBatteryLevel(level0);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    mockedApp = Mockito.mock(AviActivity.class);
    wakeStateManager = new WakeStateManager(mockedApp, cv, "");

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).disableAirplaneMode();

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).enableWakeTasks();

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).enableLocations();
  }

  @Test
  public void wakeUpAndSleep() {
    Log.d(TAG, "wakeUpAndSleep()");
    // start test above sleep threshold
    int level0 = (int) (cv.DischargingBatteryThreshold()*100) + 1;
    int plug = 0;
    int status = BatteryManager.BATTERY_STATUS_DISCHARGING;
    int scale = 100;

    UpdatableGlobalStateCopy gS = UpdatableGlobalState.getInstance();
    DeviceState devState = gS.getDeviceState();
    devState.setBatteryLevel(level0);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    mockedApp = Mockito.mock(AviActivity.class);
    wakeStateManager = new WakeStateManager(mockedApp, cv, timeZone);

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).disableAirplaneMode();
    Mockito.verify(mockedApp).disableLocations();
    Mockito.verify(mockedApp).disableWakeTasks();

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).enableWakeTasks();

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).enableLocations();

    // reduce battery level to below discharging threshold to test sleeping
    devState.setBatteryLevel(level0-2);
    devState.setBatteryPlugStatus(plug);
    devState.setBatteryChargingStatus(status);
    devState.setBatteryScale(scale);

    wakeStateManager.manageInteractions();

    // this is called twice because it was already called on the initial change from unknown
    Mockito.verify(mockedApp, Mockito.times(2)).disableLocations();

    wakeStateManager.manageInteractions();

    // this is called twice because it was already called on the initial change from unknown
    Mockito.verify(mockedApp, Mockito.times(2)).disableWakeTasks();

    wakeStateManager.manageInteractions();

    Mockito.verify(mockedApp).enableAirplaneMode();
  }

}

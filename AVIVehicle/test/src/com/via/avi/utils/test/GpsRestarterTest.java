package com.via.avi.utils.test;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.DeviceManager;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.utils.GpsRestarter;
import com.via.avi.utils.Util;

@RunWith(RobolectricTestRunner.class)
public class GpsRestarterTest extends TestCase {
  private GpsRestarter gr;

  private DeviceManager dm;
  private long gpsRebootAge = 10*60*1000l;

  @Before
  public void setUp() {
    dm = Mockito.mock(DeviceManager.class);
    gr = new GpsRestarter(dm, gpsRebootAge);
    UpdatableGlobalState.getInstance().clear();
    Util.setGPSTimeOffset(0l);
  }

  @After
  public void tearDown() {
    UpdatableGlobalState.getInstance().clear();
    Util.setGPSTimeOffset(0l);
  }

  @Test
  public void restartRequested() {
    long tStart = Util.getCurrentTimeWithGpsOffset();
    UpdatableGlobalState.getInstance().getDeviceState().setLastGpsTime(tStart);
    gr.runOnce();

    long maxGpsOffset = gpsRebootAge + GpsRestarter.CheckMillis;
    long gpsOffset = 0l;
    while (gpsOffset < maxGpsOffset) {
      gpsOffset += GpsRestarter.CheckMillis;
      Util.setGPSTimeOffset(-gpsOffset);
      gr.runOnce();
    }
    Mockito.verify(dm, Mockito.atLeast(1)).receivedRebootOrder();
  }

  @Test
  public void restartNotRequested() {
    long tStart = Util.getCurrentTimeWithGpsOffset();
    UpdatableGlobalState.getInstance().getDeviceState().setLastGpsTime(tStart);
    gr.runOnce();

    long maxGpsOffset = gpsRebootAge - GpsRestarter.CheckMillis;
    long gpsOffset = 0l;
    while (gpsOffset < maxGpsOffset) {
      gpsOffset += GpsRestarter.CheckMillis;
      Util.setGPSTimeOffset(-gpsOffset);
      gr.runOnce();
    }
    Mockito.verify(dm, Mockito.times(0)).receivedRebootOrder();
  }
}

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
import com.via.avi.utils.AndroidInternetChecker;
import com.via.avi.utils.InternetManager;
import com.via.avi.utils.Util;

@RunWith(RobolectricTestRunner.class)
public class InternetManagerTest extends TestCase {
  private InternetManager im;
  private AndroidInternetChecker icc;
  private DeviceManager dm;
  private long connRebootAge = 10*60*1000l;

  @Before
  public void setUp() {
    icc = Mockito.mock(AndroidInternetChecker.class);
    dm = Mockito.mock(DeviceManager.class);
    im = new InternetManager(icc, dm, connRebootAge);
    UpdatableGlobalState.getInstance().clear();
    Util.setGPSTimeOffset(0l);
  }

  @After
  public void tearDown() {
    UpdatableGlobalState.getInstance().clear();
    Util.setGPSTimeOffset(0l);
  }

  @Test
  public void commTimeIsntUpdated() {
    Mockito.when(icc.isInternetConnected()).thenReturn(true);
    im.runOnce();
    long tStart = 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime();

    Mockito.when(icc.isInternetConnected()).thenReturn(false);
    long maxConnOffset = 60*1000l;
    long connOffset = 0l;
    while (connOffset < maxConnOffset) {
      connOffset += InternetManager.CheckMillis;
      Util.setGPSTimeOffset(-connOffset);
      im.runOnce();
    }
    assertEquals(tStart, 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime(), 50l);
    Mockito.verify(dm, Mockito.times(0)).receivedRebootOrder();
  }

  @Test
  public void commTimeIsUpdated() {
    Mockito.when(icc.isInternetConnected()).thenReturn(true);
    im.runOnce();
    long tStart = 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime();

    Mockito.when(icc.isInternetConnected()).thenReturn(true);
    long maxConnOffset = 60*1000l;
    long connOffset = 0l;
    while (connOffset < maxConnOffset) {
      connOffset += InternetManager.CheckMillis;
      Util.setGPSTimeOffset(-connOffset);
      im.runOnce();
    }
    assertEquals(tStart + connOffset, 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime(), 50l);
    Mockito.verify(dm, Mockito.times(0)).receivedRebootOrder();
  }

  @Test
  public void restartRequested() {
    Mockito.when(icc.isInternetConnected()).thenReturn(true);
    im.runOnce();
    long tStart = 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime();

    Mockito.when(icc.isInternetConnected()).thenReturn(false);
    long maxConnOffset = connRebootAge + InternetManager.CheckMillis;
    long connOffset = 0l;
    while (connOffset < maxConnOffset) {
      connOffset += InternetManager.CheckMillis;
      Util.setGPSTimeOffset(-connOffset);
      im.runOnce();
    }
    assertEquals(tStart, 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime(), 50l);
    Mockito.verify(dm, Mockito.atLeast(1)).receivedRebootOrder();
  }

  @Test
  public void restartNotRequested() {
    Mockito.when(icc.isInternetConnected()).thenReturn(true);
    im.runOnce();
    long tStart = 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime();

    Mockito.when(icc.isInternetConnected()).thenReturn(false);
    long maxConnOffset = connRebootAge - InternetManager.CheckMillis;
    long connOffset = 0l;
    while (connOffset < maxConnOffset) {
      connOffset += InternetManager.CheckMillis;
      Util.setGPSTimeOffset(-connOffset);
      im.runOnce();
    }
    assertEquals(tStart, 
        UpdatableGlobalState.getInstance().getDeviceState().getLastCommTime(), 50l);
    Mockito.verify(dm, Mockito.times(0)).receivedRebootOrder();
  }
}

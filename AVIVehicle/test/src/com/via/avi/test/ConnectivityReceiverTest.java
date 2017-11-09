package com.via.avi.test;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import com.via.avi.AviInterface;
import com.via.avi.ConnectivityReceiver;
import com.via.avi.utils.AndroidInternetChecker;

import android.content.Context;
import android.content.Intent;

@RunWith(RobolectricTestRunner.class)
public class ConnectivityReceiverTest extends TestCase {
  private AndroidInternetChecker connChecker;
  private AviInterface app;
  private Intent intent;
  private Context context;

  private ConnectivityReceiver connReceiver;

  @Before
  public void setUp() {
    connChecker = Mockito.mock(AndroidInternetChecker.class);
    app = Mockito.mock(AviInterface.class);
    intent = Mockito.mock(Intent.class);
    context = Mockito.mock(Context.class);

    connReceiver = new ConnectivityReceiver(app, connChecker);
  }

  @Test
  public void testConnectivityGained() {
    // setup
    Mockito.when(connChecker.isInternetConnected()).thenReturn(true);

    // test
    connReceiver.onReceive(context, intent);

    // verify
    Mockito.verify(app).onConnect();
  }

  @Test
  public void testConnectivityLost() {
    // setup
    Mockito.when(connChecker.isInternetConnected()).thenReturn(true);

    // test
    connReceiver.onReceive(context, intent);
    Mockito.when(connChecker.isInternetConnected()).thenReturn(false);
    connReceiver.onReceive(context, intent);

    // verify
    Mockito.verify(app).onDisconnect();
  }
}

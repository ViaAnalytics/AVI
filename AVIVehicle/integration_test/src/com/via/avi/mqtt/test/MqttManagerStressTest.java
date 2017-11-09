package com.via.avi.mqtt.test;

import java.util.Properties;
import java.util.Random;

import junit.framework.TestCase;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.via.avi.config.ConfigValues;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.config.ConfigValues.CVKey;
import com.via.avi.mqtt.MqttCallbackHandler;
import com.via.avi.mqtt.MqttListener;
import com.via.avi.mqtt.MqttManager;
import com.via.avi.mqtt.MqttSubscription;
import com.via.avi.mqtt.MqttSubscriptionTopic;
import com.via.avi.mqtt.MqttUnsentQueue;
import com.via.avi.mqtt.MqttManager.Builder;
import com.via.avi.mqtt.config.MissingMCVProperty;
import com.via.avi.mqtt.config.MqttConfigValues;
import com.via.avi.mqtt.config.MqttConfigValues.MCVKey;
import com.via.avi.utils.AndroidInternetChecker;
import com.via.mqtt.service.MqttClientAndroidService;

@RunWith(RobolectricTestRunner.class)
public class MqttManagerStressTest extends TestCase {

  MqttManager mqttManager;
  Context context;
  MqttClientAndroidService client;
  Handler handler;
  MqttUnsentQueue mqttQueue;
  MqttSubscription mqttSub;
  MqttSubscriptionTopic mqttSubTopic;
  MqttConnectOptions conOpt;
  AndroidInternetChecker connChecker;

  final IntHolder connCounter = new IntHolder(0);

  int connectTimeout = 10;
  int keepAlive = 20;
  String clientId = "fakeDevice";
  String password = "fakePassword";
  String host = "fakeHost";
  int port = 1;
  int qos = 1;
  String topic = "fakeTopic";
  String mqttSubId = "fakeId";
  String topic2 = "fakeTopic2";
  String mqttSubId2 = "fakeId2";
  
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
  
  private MqttConfigValues buildDefaultMcv() {
    Properties prop = new Properties();
    prop.setProperty(MCVKey.MQTT_HOST.getKey(), "fakehost");
    try {
      return new MqttConfigValues(prop);
    } catch (MissingMCVProperty e) {
      System.out.println("Shouldn't happen!");
    }
    return null;
  }

  @Before
  public void setUp() throws MqttException {
    context = Mockito.mock(Context.class);
    client = Mockito.mock(MqttClientAndroidService.class);
    mqttQueue = Mockito.mock(MqttUnsentQueue.class);
    mqttSub = Mockito.mock(MqttSubscription.class);
    mqttSubTopic = Mockito.mock(MqttSubscriptionTopic.class);
    connChecker = Mockito.mock(AndroidInternetChecker.class);

    // use default connection options
    conOpt = new MqttConnectOptions();
    conOpt.setConnectionTimeout(connectTimeout);
    conOpt.setUserName(clientId);
    conOpt.setPassword(password.toCharArray());
    conOpt.setKeepAliveInterval(keepAlive);

    handler = new Handler(Looper.getMainLooper());
    Builder mmB = new MqttManager
        .Builder(clientId, conOpt, context, buildDefaultCv(),
            buildDefaultMcv());
    mmB.client(client).connectHandler(handler).mqttQueue(mqttQueue);
    mmB.connChecker(connChecker);
    mqttManager = mmB.build();
    connCounter.reset(0);

    Mockito.when(connChecker.isInternetConnected()).thenReturn(true);

    // ensure that proper connection action happens
    Mockito.when(client.connect(Mockito.eq(conOpt), Mockito.eq(context), Mockito.any(MqttListener.class)))
    .thenAnswer(new Answer<Object>() {
      public Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        MqttListener mqttListener = (MqttListener) args[2];
        mqttListener.onSuccess(null);
        connCounter.increment();
        return null;
      }
    });

    // ensure that proper disconnect action happens
    Mockito.when(client.disconnect(Mockito.eq(conOpt.getConnectionTimeout()),
        Mockito.eq(context), Mockito.any(MqttListener.class)))
        .thenAnswer(new Answer<Object>() {
          public Object answer(InvocationOnMock invocation) {
            Object[] args = invocation.getArguments();
            MqttListener mqttListener = (MqttListener) args[2];
            mqttListener.onSuccess(null);
            return null;
          }
        });
  }

  @After
  public void tearDown() {
    mqttManager = null;
  }

  @Test
  public void testLoadConnectivitySwitchingEvent() throws InterruptedException, MqttException {

    Random randGen = new Random();
    long maxMillisDelay = 2l;
    int nFlips = 500;
    int n = 0;
    boolean connected = false;
    while (n < nFlips) {
      long nextDelay = Math.round(randGen.nextDouble()*maxMillisDelay);
      Thread.sleep(nextDelay);
      if (!connected) {
        mqttManager.createConnection(0);
      } else {
        mqttManager.disconnect();
      }
      connected = !connected;
      //      System.out.println(mqttManager.getConnectionStatus());
      n++;
    }

    // verify

    // make sure the loop didn't end early
    assertEquals(nFlips, n);

    assertTrue(!mqttManager.isConnectedOrConnecting());
    assertEquals(n/2, connCounter.heldInt);
  }

  @Test
  public void testLoadConnectivitySwitchingOdd() throws InterruptedException, MqttException {

    Random randGen = new Random();
    long maxMillisDelay = 2l;
    int nFlips = 501;
    int n = 0;
    boolean connected = false;
    while (n < nFlips) {
      long nextDelay = Math.round(randGen.nextDouble()*maxMillisDelay);
      Thread.sleep(nextDelay);
      if (!connected) {
        mqttManager.createConnection(0);
      } else {
        mqttManager.disconnect();
      }
      connected = !connected;
      //      System.out.println(mqttManager.getConnectionStatus());
      n++;
    }

    // verify:

    // make sure the loop didn't end early
    assertEquals(nFlips, n);

    // assert that we are in "connecting" state if we've undergone the appropriate number of flips
    assertTrue(mqttManager.isConnected());
    assertEquals(n/2+1, connCounter.heldInt);
  }

  @Test
  public void testLoadConnectivityFlaking() throws InterruptedException, MqttException {
    Random randGen = new Random();
    long maxMillisDelay = 2l;
    int nEvents = 500;
    int n = 0;
    boolean connected = false;
    while (n < nEvents) {
      long nextDelay = Math.round(randGen.nextDouble()*maxMillisDelay);
      Thread.sleep(nextDelay);
      double connectOrDisconnect = randGen.nextDouble();
      if (connectOrDisconnect > 0.5) {
        mqttManager.createConnection(0);
        connected = true;
      } else {
        mqttManager.disconnect();
        connected = false;
      }
      n++;
    }

    // verify:

    // make sure the loop didn't end early
    assertEquals(nEvents, n);

    // assert that we are in "connecting" state if we've undergone the appropriate number of flips
    if (connected) {
      assertTrue(mqttManager.isConnected());
    } else {
      assertFalse(mqttManager.isConnected());
    }
  }

  @Test
  public void testConnectionLost() {
    mqttManager.createConnection(0);
    // verify connected
    assertTrue(mqttManager.isConnected());

    ArgumentCaptor<MqttCallbackHandler> arg = 
        ArgumentCaptor.forClass(MqttCallbackHandler.class);
    Mockito.verify(client).setCallback(arg.capture());
    MqttCallbackHandler cH = arg.getValue();
    cH.connectionLost(null);

    // verify that we've disconnected
    assertFalse(mqttManager.isConnected());
    // verify that we're starting the reconnect cycle
    assertTrue(mqttManager.isConnecting());
  }

  @Test
  public void testPublishFailed() throws MqttPersistenceException, MqttException {
    // simulate failed publish message
    Mockito.when(client.publish(Mockito.anyString(), Mockito.any(byte[].class),
        Mockito.anyInt(), Mockito.anyBoolean(), Mockito.eq(context), 
        Mockito.any(MqttListener.class)))
        .thenAnswer(new Answer<Object>() {
          public Object answer(InvocationOnMock invocation) {
            Object[] args = invocation.getArguments();
            MqttListener mqttListener = (MqttListener) args[2];
            mqttListener.onFailure(null, null);
            return null;
          }
        });

    mqttManager.createConnection(0);
    // verify connected
    assertTrue(mqttManager.isConnected());

    ArgumentCaptor<MqttCallbackHandler> arg = 
        ArgumentCaptor.forClass(MqttCallbackHandler.class);
    Mockito.verify(client).setCallback(arg.capture());
    MqttCallbackHandler cH = arg.getValue();
    cH.connectionLost(null);

    // verify that we've disconnected
    assertFalse(mqttManager.isConnected());
    // verify that we're starting the reconnect cycle
    assertTrue(mqttManager.isConnecting());
  }

  @Test
  public void testSubscribeFailed() throws MqttException {
    // simulate failed subscription
    Mockito.when(client.subscribe(Mockito.anyString(), 
        Mockito.anyInt(), Mockito.eq(context), 
        Mockito.any(MqttListener.class)))
        .thenAnswer(new Answer<Object>() {
          public Object answer(InvocationOnMock invocation) {
            Object[] args = invocation.getArguments();
            MqttListener mqttListener = (MqttListener) args[3];
            mqttListener.onFailure(null, null);
            return null;
          }
        });

    // simulate subscription
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);
    // properly handle increment behavior for mocked MqttSubscription interface
    final IntHolder failureCount = new IntHolder(0);
    Mockito.when(mqttSub.getFailures()).thenAnswer(new Answer<Object>() {
      public Object answer(InvocationOnMock invocation) {
        return failureCount.heldInt;
      }
    });
    Mockito.doAnswer(new Answer<Void>() {
      public Void answer(InvocationOnMock invocation) {
        failureCount.increment();
        return null;
      }
    }).when(mqttSub).incrementFailures();

    mqttManager.createConnection(0);
    mqttManager.subscribe(mqttSub);
    Mockito.verify(client, Mockito.times(mqttManager.maxSubscriptionAttempts))
    .subscribe(Mockito.eq(topic), Mockito.eq(qos), Mockito.eq(context), 
        Mockito.any(MqttListener.class));
  }

  class IntHolder {
    int heldInt;
    public IntHolder(int initVal) {
      heldInt = initVal;
    }
    public void increment() {
      heldInt ++;
    }
    public void reset(int initVal) {
      heldInt = initVal;
    }
  }
}

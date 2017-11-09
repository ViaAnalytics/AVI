package com.via.avi.mqtt.test;

import java.util.Properties;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import android.content.Context;
import android.os.Handler;

import com.via.avi.config.ConfigValues;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.config.ConfigValues.CVKey;
import com.via.avi.mqtt.MqttManager;
import com.via.avi.mqtt.MqttManager.Builder;
import com.via.avi.mqtt.MqttManagerInterface.ConnectionStatus;
import com.via.avi.mqtt.MqttSubscription;
import com.via.avi.mqtt.MqttSubscriptionTopic;
import com.via.avi.mqtt.MqttUnsentQueue;
import com.via.avi.mqtt.config.MissingMCVProperty;
import com.via.avi.mqtt.config.MqttConfigValues;
import com.via.avi.mqtt.config.MqttConfigValues.MCVKey;
import com.via.avi.utils.AndroidInternetChecker;
import com.via.mqtt.service.MqttClientAndroidService;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class MqttManagerTest extends TestCase {
  MqttManager mqttManager;
  Context context;
  MqttClientAndroidService client;
  Handler handler;
  MqttUnsentQueue mqttQueue;
  MqttSubscription mqttSub;
  MqttSubscriptionTopic mqttSubTopic;
  AndroidInternetChecker connChecker;

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
  public void setUp() {
    context = Mockito.mock(Context.class);
    handler = Mockito.mock(Handler.class);
    client = Mockito.mock(MqttClientAndroidService.class);
    mqttQueue = Mockito.mock(MqttUnsentQueue.class);
    mqttSub = Mockito.mock(MqttSubscription.class);
    mqttSubTopic = Mockito.mock(MqttSubscriptionTopic.class);
    connChecker = Mockito.mock(AndroidInternetChecker.class);

    // use default connection options
    MqttConnectOptions conOpt = new MqttConnectOptions();
    conOpt.setConnectionTimeout(connectTimeout);
    conOpt.setUserName(clientId);
    conOpt.setPassword(password.toCharArray());
    conOpt.setKeepAliveInterval(keepAlive);
    Builder mmB = new MqttManager
        .Builder(clientId, conOpt, context, buildDefaultCv(), 
            buildDefaultMcv());
    mmB.client(client).connectHandler(handler).mqttQueue(mqttQueue);
    mmB.connChecker(connChecker);

    mqttManager = mmB.build();
  }

  @After
  public void tearDown() {
    mqttManager = null;
  }

  @Test
  public void testCreateConnectionImmediately() {
    Mockito.when(connChecker.isInternetConnected()).thenReturn(true);
    mqttManager.createConnection(0);
    assertTrue(mqttManager.isConnecting());

    ArgumentCaptor<Runnable> arg = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(handler).postDelayed(arg.capture(), Mockito.eq(0l));
  }

  @Test
  public void testSubscribe() {
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);

    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    mqttManager.subscribe(mqttSub);

    ArgumentCaptor<IMqttActionListener> arg = 
        ArgumentCaptor.forClass(IMqttActionListener.class);
    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }
  }

  @Test
  public void testSubscribeThenUnsubscribeSubMethod() {
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);

    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    mqttManager.subscribe(mqttSub);

    ArgumentCaptor<IMqttActionListener> arg = 
        ArgumentCaptor.forClass(IMqttActionListener.class);
    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    mqttManager.unsubscribe(mqttSub.getTopic());

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }
  }

  @Test
  public void testSubscribeThenUnsubscribeTopicMethod() {
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);

    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    mqttManager.subscribe(mqttSub);

    ArgumentCaptor<IMqttActionListener> arg = 
        ArgumentCaptor.forClass(IMqttActionListener.class);
    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    mqttManager.unsubscribe(mqttSubTopic);

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }
  }

  @Test
  public void testSubscribeThenUnsubscribeAll() {
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);

    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    mqttManager.subscribe(mqttSub);

    ArgumentCaptor<IMqttActionListener> arg = 
        ArgumentCaptor.forClass(IMqttActionListener.class);
    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    mqttManager.unsubscribeAll();

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }
  }

  @Test
  public void testSubscribeMultipleThenUnsubscribeAll() {
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);

    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    mqttManager.subscribe(mqttSub);

    MqttSubscriptionTopic mst2 = Mockito.mock(MqttSubscriptionTopic.class);
    MqttSubscription ms2 = Mockito.mock(MqttSubscription.class);
    Mockito.when(mst2.toString()).thenReturn(topic2);
    Mockito.when(ms2.getTopic()).thenReturn(mst2);
    Mockito.when(ms2.getQos()).thenReturn(qos);

    mqttManager.subscribe(ms2);

    ArgumentCaptor<IMqttActionListener> arg = 
        ArgumentCaptor.forClass(IMqttActionListener.class);
    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic2), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    mqttManager.unsubscribeAll();

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic2),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }
  }

  @Test
  public void testSubscribeMultipleThenUnsubscribeIndividually() {
    Mockito.when(mqttSubTopic.toString()).thenReturn(topic);
    Mockito.when(mqttSub.getTopic()).thenReturn(mqttSubTopic);
    Mockito.when(mqttSub.getQos()).thenReturn(qos);

    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    mqttManager.subscribe(mqttSub);

    MqttSubscriptionTopic mst2 = Mockito.mock(MqttSubscriptionTopic.class);
    MqttSubscription ms2 = Mockito.mock(MqttSubscription.class);
    Mockito.when(mst2.toString()).thenReturn(topic2);
    Mockito.when(ms2.getTopic()).thenReturn(mst2);
    Mockito.when(ms2.getQos()).thenReturn(qos);

    mqttManager.subscribe(ms2);

    ArgumentCaptor<IMqttActionListener> arg = 
        ArgumentCaptor.forClass(IMqttActionListener.class);
    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    try {
      Mockito.verify(client).subscribe(Mockito.eq(topic2), Mockito.eq(qos),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    mqttManager.unsubscribe(mst2);

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic2),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }

    mqttManager.unsubscribe(mqttSubTopic);

    try {
      Mockito.verify(client).unsubscribe(Mockito.eq(topic),
          Mockito.eq((Object) context), arg.capture());
    } catch (MqttException e) {
      fail("Subscribe should not throw an error.");
    }
  }

  @Test
  public void testCreateConnectionWithDelay() {
    Mockito.when(connChecker.isInternetConnected()).thenReturn(true);
    int delay = 10000;
    mqttManager.createConnection(delay);
    assertTrue(mqttManager.isConnecting());

    ArgumentCaptor<Runnable> arg = ArgumentCaptor.forClass(Runnable.class);
    Mockito.verify(handler).postDelayed(arg.capture(), Mockito.eq((long)delay));
  }

}

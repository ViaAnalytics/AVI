package com.via.avi.messages.test;

import java.util.ArrayList;
import java.util.Properties;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import android.location.Location;

import com.google.protobuf.InvalidProtocolBufferException;
import com.via.avi.config.ConfigValues;
import com.via.avi.config.ConfigValues.CVKey;
import com.via.avi.config.MissingCVProperty;
import com.via.avi.messages.AviMessages.ExistMessage;
import com.via.avi.messages.Exist;
import com.via.avi.messages.Exist.UninitializedExistException;
import com.via.avi.messages.ExistDataSource;
import com.via.avi.messages.MqttMessageManager;
import com.via.avi.messages.RawLocation;
import com.via.avi.messages.RawLocationConverter;
import com.via.avi.messages.RawLocationConverter.UninitializedRawLocationException;
import com.via.avi.mqtt.MqttManagerInterface;
import com.via.avi.mqtt.MqttMessageTopic;
import com.via.avi.utils.AndroidInternetChecker;

import junit.framework.TestCase;

@RunWith(RobolectricTestRunner.class)
public class MqttMessageManagerTest extends TestCase {
  MqttMessageManager messageManager;
  AndroidInternetChecker mockedAIC;

  MqttManagerInterface mqttManager;

  ExistDataSource existQueue;

  private static final Integer id = -1;
  private static final String deviceId = "fakeDevice";
  private static final String agency = "fakeAgency";

  private static final double vehLat = 0.0;
  private static final double vehLon = 0.0;
  private static final float vehSpeed = 5.0f;
  private static final float vehBearing = 5.0f;
  private static final int vehAccuracy = 16;

  private static Location vehicleLocation;
  private static Long time;

  private ConfigValues cv;
  
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

    vehicleLocation = new Location("fake_provider");
    vehicleLocation.setLatitude(vehLat);
    vehicleLocation.setLongitude(vehLon);
    vehicleLocation.setSpeed(vehSpeed);
    vehicleLocation.setBearing(vehBearing);
    vehicleLocation.setAccuracy(vehAccuracy);

    time = System.currentTimeMillis();

    mockedAIC = Mockito.mock(AndroidInternetChecker.class);
    mqttManager = Mockito.mock(MqttManagerInterface.class);
    existQueue = Mockito.mock(ExistDataSource.class);

    cv = buildDefaultCv();
    System.out.println(cv);
    System.out.println(cv.MessageBufferClearingCadence());

    messageManager = new MqttMessageManager(mockedAIC,
        mqttManager, existQueue, agency, deviceId, cv);
  }

  @After
  public void tearDown() {
    messageManager = null;
    mockedAIC = null;
    mqttManager = null;

    vehicleLocation = null;
  }

  @Test
  public void sendExistMessageConnected() {
    // tell app to pretend we have connectivity
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(true);

    // initialize exist message
    Exist testExist = generateExist();
    byte[] existByteMessage = null;
    try {
      existByteMessage = testExist.getByteMessage();
    } catch (UninitializedExistException e) {
      fail("Exist threw error on acceptable byte[].");
    }
    // send it
    messageManager.sendExistMessage(testExist);


    ArgumentCaptor<MqttMessageTopic> topicArg = 
        ArgumentCaptor.forClass(MqttMessageTopic.class);
    ArgumentCaptor<MqttMessage> msgArg = 
        ArgumentCaptor.forClass(MqttMessage.class);
    // assert that a message was sent
    Mockito.verify(mqttManager).sendMessage(
        topicArg.capture(), msgArg.capture());
    // assert that the topic was correct
    assertEquals(agency + "/pb/" + deviceId + "/exist", 
        topicArg.getValue().toString());
    // assert that the contents were correct
    Assert.assertArrayEquals(existByteMessage, 
        msgArg.getValue().getPayload());
  }

  @Test
  public void sendRawLocationMessageConnected() {
    // tell app to pretend we have connectivity
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(true);

    // initialize raw location message
    RawLocation testRl = generateRawLocation();
    RawLocationConverter c = new RawLocationConverter(testRl);
    byte[] rlByteMessage = null;
    try {
      rlByteMessage = c.getByteMessage();
    } catch (UninitializedRawLocationException e1) {
      fail("RawLocation threw error on acceptable byte[].");
    }

    // send it
    messageManager.sendRawLocationMessage(testRl);

    ArgumentCaptor<MqttMessageTopic> topicArg = 
        ArgumentCaptor.forClass(MqttMessageTopic.class);
    ArgumentCaptor<MqttMessage> msgArg = 
        ArgumentCaptor.forClass(MqttMessage.class);
    // assert that a message was sent
    Mockito.verify(mqttManager).sendMessage(
        topicArg.capture(), msgArg.capture());
    // assert that the topic was correct
    assertEquals(agency + "/pb/" + deviceId + "/raw_location", 
        topicArg.getValue().toString());
    // assert that the contents were correct
    Assert.assertArrayEquals(rlByteMessage, msgArg.getValue().getPayload());
  }

  @Test
  public void sendRawLocationMessageDisconnected() {
    // tell app to pretend we have connectivity
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(false);

    // initialize raw location message
    RawLocation testRl = generateRawLocation();
    RawLocationConverter c = new RawLocationConverter(testRl);
    byte[] rlByteMessage = null;
    try {
      rlByteMessage = c.getByteMessage();
    } catch (UninitializedRawLocationException e1) {
      fail("RawLocation threw error on acceptable byte[].");
    }

    // send it
    messageManager.sendRawLocationMessage(testRl);
    // ensure that our message wasn't sent
    Mockito.verify(mqttManager, Mockito.never())
    .sendMessage(Mockito.any(MqttMessageTopic.class), 
        Mockito.any(MqttMessage.class));

    // now pretend we're connected
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(true);

    // start clearing messages, and ensure our message was sent
    messageManager.startClearing();
    messageManager.stopClearing();

    // TODO: think about this
    // This hack is to ensure that we don't have a problem with the
    // (async, background-thread) message clearing. This isn't really optimal.
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      fail("Some kind of InterruptedException");
    }

    ArgumentCaptor<MqttMessageTopic> topicArg = 
        ArgumentCaptor.forClass(MqttMessageTopic.class);
    ArgumentCaptor<MqttMessage> msgArg = 
        ArgumentCaptor.forClass(MqttMessage.class);
    // assert that a message was sent
    Mockito.verify(mqttManager).sendMessage(
        topicArg.capture(), msgArg.capture());
    // assert that the topic was correct
    assertEquals(agency + "/pb/" + deviceId + "/raw_location", 
        topicArg.getValue().toString());
    // assert that the contents were correct
    Assert.assertArrayEquals(rlByteMessage, msgArg.getValue().getPayload());
  }

  @Test
  public void sendExistMessageDisconnected() {
    // tell app to pretend we don't have connectivity
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(false);


    // initialize exist message
    Exist testExist = generateExist();
    // instead of using actual db, just pretend that the messages have been queued
    ArrayList<Exist> msgs = new ArrayList<Exist>();
    msgs.add(testExist);
    Mockito.when(existQueue.getOldestUnsentMessages(cv.MessagesPerSend())).thenReturn(msgs);
    // send it
    messageManager.sendExistMessage(testExist);
    // ensure that our message wasn't sent
    Mockito.verify(mqttManager, Mockito.never())
    .sendMessage(Mockito.any(MqttMessageTopic.class), 
        Mockito.any(MqttMessage.class));

    // now pretend we're connected
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(true);

    // start clearing messages, and ensure our message was sent
    messageManager.startClearing();
    messageManager.stopClearing();

    // TODO: think about this
    // This hack is to ensure that we don't have a problem with the
    // (async, background-thread) message clearing. This isn't really optimal.
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      fail("Some kind of InterruptedException");
    }

    ArgumentCaptor<MqttMessageTopic> topicArg = 
        ArgumentCaptor.forClass(MqttMessageTopic.class);
    ArgumentCaptor<MqttMessage> msgArg = 
        ArgumentCaptor.forClass(MqttMessage.class);
    // assert that a message was sent
    Mockito.verify(mqttManager).sendMessage(
        topicArg.capture(), msgArg.capture());
    // assert that the topic was correct
    assertEquals(agency + "/pb/" + deviceId + "/exist", 
        topicArg.getValue().toString());
    ExistMessage sentMessage = null;
    try {
      sentMessage = ExistMessage.parseFrom(msgArg.getValue().getPayload());
    } catch (InvalidProtocolBufferException e) {
      fail("Failed to parse sent exist message.");
    }
    assertEquals(deviceId, sentMessage.getDeviceId());
    assertEquals(time, Long.valueOf(sentMessage.getTs()));
  }

  @Test
  public void sendMessagesThrottled() {
    // tell app to pretend we don't have connectivity
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(false);


    ArrayList<Exist> msgs = new ArrayList<Exist>();
    for (int i = 0; i < cv.MessagesPerSend()*5; i++) {
      // initialize exist message
      Exist testExist = generateExist();
      // send it
      messageManager.sendExistMessage(testExist);
      if (i < cv.MessagesPerSend()) msgs.add(testExist);
    }

    // ensure that our messages weren't sent
    Mockito.verify(mqttManager, Mockito.never())
    .sendMessage(Mockito.any(MqttMessageTopic.class), 
        Mockito.any(MqttMessage.class));

    // now pretend we're connected
    Mockito.when(mockedAIC.isMqttConnected()).thenReturn(true);

    // fake throttling
    Mockito.when(existQueue.getOldestUnsentMessages(cv.MessagesPerSend())).thenReturn(msgs);

    // start clearing messages, and ensure our message was sent
    messageManager.startClearing();
    messageManager.stopClearing();

    // This hack is to ensure that we don't have a problem with the
    // (async, background-thread) message clearing
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      fail("Some kind of InterruptedException");
    }

    // ensure that we went through one message-sending cadence
    Mockito.verify(mqttManager, Mockito.times(cv.MessagesPerSend()))
    .sendMessage(Mockito.any(MqttMessageTopic.class), 
        Mockito.any(MqttMessage.class));
  }

  private Exist generateExist() {
    Exist testExist = new Exist();
    testExist.setId(id);
    testExist.setDeviceId(deviceId);
    testExist.setTime(time);
    return testExist;
  }

  private RawLocation generateRawLocation() {
    RawLocation testRL = new RawLocation();
    testRL.setDeviceId(deviceId);
    testRL.setTime(time);
    testRL.setLocation(vehicleLocation);
    return testRL;
  }
}

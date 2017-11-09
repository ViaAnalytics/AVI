/*
 * Licensed Materials - Property of IBM
 *
 * 5747-SM3
 *
 * (C) Copyright IBM Corp. 1999, 2012 All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 *
 */
package com.via.avi.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.via.avi.mqtt.MqttManagerInterface.ConnectionStatus;

import android.annotation.SuppressLint;
import android.util.Log;

/**
 * Handles call backs from the MQTT Client
 *
 */
public class MqttCallbackHandler implements MqttCallback {

  private static String TAG = "MqttCallbackHandler";

  private MqttManagerInterface mqttManager;

  /**
   * Creates an <code>MqttCallbackHandler</code> object
   * @param context The application's context
   */
  public MqttCallbackHandler(MqttManagerInterface mqttManager)
  {
    this.mqttManager = mqttManager;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#connectionLost(java.lang.Throwable)
   */
  @SuppressLint("StringFormatMatches")
  @Override
  public void connectionLost(Throwable cause) {
    if (mqttManager.isConnectedOrConnecting()) {
      if (cause != null) {
        Log.e(TAG,"Mqtt connection lost!", cause);
      } else {
        Log.e(TAG,"Mqtt connection lost with null exception!");
      }
    }

    if (!mqttManager.isConnecting()) {
      mqttManager.changeConnectionStatus(ConnectionStatus.ERROR);
    }

    mqttManager.connectionFailure();
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#messageArrived(java.lang.String, org.eclipse.paho.client.mqttv3.MqttMessage)
   */
  @SuppressLint("StringFormatMatches")
  @Override
  public void messageArrived(String topic, MqttMessage message) throws Exception {

    MqttMessageTopic mqttTopic = new MqttMessageTopic(topic);

    Log.d(TAG,"Received a message on topic " + topic);
    mqttManager.handleMessage(mqttTopic, message);
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.MqttCallback#deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken)
   */
  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    // Do nothing
  }

}

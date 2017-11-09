/*
============================================================================ 
Licensed Materials - Property of IBM

5747-SM3
 
(C) Copyright IBM Corp. 1999, 2012 All Rights Reserved.
 
US Government Users Restricted Rights - Use, duplication or
disclosure restricted by GSA ADP Schedule Contract with
IBM Corp.
============================================================================
 */
package com.via.mqtt.service;

/**
 * Various strings used to identify operations or data in the Android MQTT
 * service, mainly used in Intents passed between Activities and the Service.
 */
public interface MqttServiceConstants {

  /*
   * Attibutes of messages <p> Used for the column names in the database
   */
  static final String DUPLICATE = "duplicate";
  static final String RETAINED = "retained";
  static final String QOS = "qos";
  static final String PAYLOAD = "payload";
  static final String DESTINATION_NAME = "destinationName";
  static final String CLIENT_HANDLE = "clientHandle";
  static final String MESSAGE_ID = "messageId";

  /* Tags for actions passed between the Activity and the Service */
  static final String SEND_ACTION = "send";
  static final String UNSUBSCRIBE_ACTION = "unsubscribe";
  static final String SUBSCRIBE_ACTION = "subscribe";
  static final String DISCONNECT_ACTION = "disconnect";
  static final String CONNECT_ACTION = "connect";
  static final String MESSAGE_ARRIVED_ACTION = "messageArrived";
  static final String MESSAGE_DELIVERED_ACTION = "messageDelivered";
  static final String ON_CONNECTION_LOST_ACTION = "onConnectionLost";
  static final String TRACE_ACTION = "trace";

  /* Identifies an Intent which calls back to the Activity */
  static final String CALLBACK_TO_ACTIVITY = MqttService.TAG
                                             + ".callbackToActivity";

  /* Identifiers for extra data on Intents broadcast to the Activity */
  static final String CALLBACK_ACTION = MqttService.TAG + ".callbackAction";
  static final String CALLBACK_STATUS = MqttService.TAG + ".callbackStatus";
  static final String CALLBACK_CLIENT_HANDLE = MqttService.TAG + "."
                                               + CLIENT_HANDLE;
  static final String CALLBACK_ERROR_MESSAGE = MqttService.TAG
                                               + ".errorMessage";
  static final String CALLBACK_EXCEPTION_STACK = MqttService.TAG
                                                 + ".exceptionStack";
  static final String CALLBACK_INVOCATION_CONTEXT = MqttService.TAG + "."
                                                    + "invocationContext";
  static final String CALLBACK_ACTIVITY_TOKEN = MqttService.TAG + "."
                                                + "activityToken";
  static final String CALLBACK_DESTINATION_NAME = MqttService.TAG + '.'
                                                  + DESTINATION_NAME;
  static final String CALLBACK_MESSAGE_ID = MqttService.TAG + '.'
                                            + MESSAGE_ID;
  static final String CALLBACK_MESSAGE_PARCEL = MqttService.TAG + ".PARCEL";
  static final String CALLBACK_TRACE_SEVERITY = MqttService.TAG
                                                + ".traceSeverity";
  static final String CALLBACK_ERROR_NUMBER = MqttService.TAG
                                              + ".ERROR_NUMBER";

  static final String CALLBACK_EXCEPTION = MqttService.TAG + ".exception";

  //exception code for non mqttexceptions
  static final int NON_MQTT_EXCEPTION = -1;

}
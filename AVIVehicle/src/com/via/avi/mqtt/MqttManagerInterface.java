package com.via.avi.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttManagerInterface {

  enum MessageType {
    /** Connect Action **/
    EVENT,
    /** Disconnect Action **/
    DISCONNECT,
    /** Subscribe Action **/
    SUBSCRIBE,
    /** Unubscribe Action **/
    UNSUBSCRIBE,
    /** Publish Action **/
    PUBLISH
  }

  /**
   * Connections status for a connection
   */
  public enum ConnectionStatus {

    /** Client is Connecting **/
    CONNECTING,
    /** Client is Connected **/
    CONNECTED,
    /** Client is Disconnecting **/
    DISCONNECTING,
    /** Client is Disconnected **/
    DISCONNECTED,
    /** Client has encountered an Error **/
    ERROR,
    /** Status is unknown **/
    NONE
  }

  /**
   * Method to run upon failure of MQTT connection.
   */
  public void connectionFailure();

  /**
   * Run after successful MQTT subscription.
   */
  public void subscribeSuccess(MqttSubscriptionTopic topic);

  /**
   * Run after successful MQTT unsubscription.
   */
  public void unsubscribeSuccess(MqttSubscriptionTopic topic);

  /**
   * Subscribe to given MqttSubscription.
   * 
   * @param mqttSub
   *     Target MqttSubscription.
   */
  public void subscribe(MqttSubscription mqttSub);

  /**
   * Actions to take when a subscribe attempt has failed.
   * 
   * @param mqttSub
   *     Target MqttSubscription.
   */
  public void subscribeFailure(String topic);

  /**
   * Actions to take when an unsubscribe attempt has failed.
   * 
   * @param mqttSub
   *     Target MqttSubscription.
   */
  public void unsubscribeFailure(String topic);

  /**
   * Unsubscribe from any topics that match the given topic.
   * 
   * @param mqttTopic
   *      topic to match on.
   */
  public void unsubscribe(MqttSubscriptionTopic mqttTopic);

  public void unsubscribeAll();

  /**
   * Actions to take after a successful connection.
   */
  public void connectionSuccess();

  /**
   * Handles arrived MQTT messages of all types, processing as appropriate.
   * 
   * @param mqttTopic
   *		Topic of received MQTT message.
   * @param message
   * 		Body of received MQTT message.
   */
  public void handleMessage(MqttMessageTopic mqttTopic, MqttMessage message);

  /**
   * Establishes actual MQTT connection to the broker. (To be run 
   * after successfully initializing the MqttService.)
   * 
   * @param delay Wait time (in milliseconds) before attempting to connect.
   */
  public void createConnection(int delay);

  /**
   * Cancel any pending connection attempts.
   */
  public void cancelConnectAttempts();

  /**
   * Disconnect the underlying MQTT client (typically upon connectivity
   * failure).
   */
  public void disconnect();

  //	/**
  //	 * If there are any undelivered messages upon a connectivity 
  //	 * failure, resend them here after a successful reconnect.
  //	 */
  //	public void deliverUndelivered();

  /**
   * Publish MqttMessage via MQTT w/ explicit topic.
   * 
   * @param topic
   *    Topic of message to publish.
   * @param messageType
   *    Body of message to send.
   */
  public void sendMessage(MqttMessageTopic topic, MqttMessage mqttMessage);

  /**
   * Set the Connect Options according to the given variable
   * 
   * @param newConOpt
   */
  public void setConnectOptions(MqttConnectOptions newConOpt);

  /**
   * Set the connection status according to the given variable
   * 
   * @param connectionStatus
   *      New connection status.
   */
  public void changeConnectionStatus(ConnectionStatus connectionStatus);

  /**
   * Get the current connection status.
   */
  public ConnectionStatus getConnectionStatus();

  /**
   * Check the connection status of MQTT
   * 
   * @return true/false
   */
  public boolean isConnected();

  /**
   * Check the 'connecting' status of MQTT
   * 
   * @return true/false
   */
  public boolean isConnecting();

  /**
   * Check whether MQTT connection is connecting or connected
   * 
   * @return true/false
   */
  public boolean isConnectedOrConnecting();

  /**
   * Call after failed publish attempt to queue message for future 
   * send attempt.
   * 
   * @param topic
   * 		Topic to publish queued message to.
   * @param unsentMessage
   * 		Contents of queued message.
   */
  public void queueUnsentMessage(String topic, MqttMessage unsentMessage);

  /**
   * Call frequently to work through the backlog of unsent MQTT messages.
   */
  public void clearUnsentMessages();

}

package com.via.avi.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttSubscription {
  /**
   * @return
   *      Topic to subscribe to.
   */
  public MqttSubscriptionTopic getTopic();

  /**
   * @return
   *      QOS of subscription (0, 1, or 2).
   */
  public int getQos();

  /**
   * Function called when new message is received for this subscription.
   * 
   * @param mqttMessage
   *      Received message to be processed.
   */     
  public void onMessage(MqttMessageTopic topic, MqttMessage mqttMessage);
  /**
   * @return
   *      Number of times this subscription has been tried and failed.
   */
  public int getFailures();

  /**
   * Add one failure to the count.
   */
  public void incrementFailures();


  /**
   * Clear subscription failure count.
   */
  public void clearFailures();
}

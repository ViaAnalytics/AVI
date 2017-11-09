package com.via.avi.mqtt;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.via.avi.config.ConfigValues;
import com.via.avi.mqtt.MqttUnsentMessage.MqttUnsentMessageException;

import android.util.Log;

public class MqttUnsentQueue {
  private static String TAG = "MqttUnsentQueue";

  private ConcurrentLinkedQueue<MqttUnsentMessage> messages;
  private MqttManager mqttManager;
  private ConfigValues cv;

  public MqttUnsentQueue(MqttManager mqttManager, ConfigValues cv) {
    messages = new ConcurrentLinkedQueue<MqttUnsentMessage>();
    this.mqttManager = mqttManager;
    this.cv = cv;
  }

  public void queueUnsentMessage(MqttMessageTopic topic, MqttMessage unsentMessage) {
    MqttUnsentMessage queueMessage;
    try {
      queueMessage = new MqttUnsentMessage(topic, unsentMessage);
      Log.d(TAG,"Queueing unsent message on topic " + topic);
      messages.add(queueMessage);
    } catch (MqttUnsentMessageException e) {
      Log.e(TAG,"Failed to store unsent message!");
    }
  }

  public void clearUnsentMessages() {
    for (int i = 0; i < cv.MessagesPerSend(); i++) {
      MqttUnsentMessage message = messages.poll();
      if (message == null) {
        // end of messages
        break;
      }
      Log.d(TAG, "Attemping to clear an unsent message on topic " + message.getTopic());
      mqttManager.sendMessage(message.getTopic(),
          message.getMqttMessage());
    }
  }

  public boolean hasUnsentMessages() {
    return messages.size() > 0;
  }
}

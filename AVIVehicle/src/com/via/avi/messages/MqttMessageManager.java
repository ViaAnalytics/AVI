package com.via.avi.messages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import android.os.Handler;
import android.util.Log;

import com.via.avi.config.ConfigValues;
import com.via.avi.messages.MessageSender;
import com.via.avi.messages.RawLocation;
import com.via.avi.messages.Exist.UninitializedExistException;
import com.via.avi.messages.MqttSimpleMessageTopic.MqttSimpleMessageType;
import com.via.avi.messages.RawLocationConverter.UninitializedRawLocationException;
import com.via.avi.mqtt.MqttManagerInterface;
import com.via.avi.mqtt.MqttMessageTopic.MqttMessageTopicException;
import com.via.avi.mqtt.MqttSubscriptionTopic.MqttSubscriptionTopicException;
import com.via.avi.utils.AndroidInternetChecker;
import com.via.avi.utils.Util;

public class MqttMessageManager implements MessageSender {
  private static String TAG = "MqttMessageManager";
  private AndroidInternetChecker aic;
  private MqttManagerInterface mqttManager;

  // manage queue-clearing of old messages
  private Handler mHandlerQueueClearing;
  private Runnable mJobQueueClearing;

  // unsent exist message logging
  private ExistDataSource existQueue;

  private ConfigValues cv;

  // for constructing mqtt messages and topics
  private String agency;
  private String deviceId;
  private int qos = 0;

  // variables to manage message-sending cadence
  private Long prevRlSendTime = 0L;
  private float prevRlSpeed = 0.f;

  // normal message queues
  private ConcurrentLinkedQueue<RawLocation> rlMessageQueue =
      new ConcurrentLinkedQueue<RawLocation>();

  // thread pool for background tasks
  private static ExecutorService pool = Executors.newCachedThreadPool();

  public MqttMessageManager(AndroidInternetChecker aic,
      MqttManagerInterface mqttManager,
      ExistDataSource existQueue,
      String agency, String deviceId, ConfigValues cv) {
    this.aic = aic;
    this.mqttManager = mqttManager;
    this.existQueue = existQueue;

    this.agency = agency;
    this.deviceId = deviceId;
    this.cv = cv;

    final long mbcc = cv.MessageBufferClearingCadence();
    mJobQueueClearing = new Runnable() {

      @Override
      public void run() {
        Log.d(TAG,
            "Trying to clear message queues from the QueueClearing handler");
        clearMessageQueues();
        mHandlerQueueClearing.postDelayed(mJobQueueClearing, mbcc);
      }
    };
    mHandlerQueueClearing = new Handler();
  }

  private MqttSimpleMessageTopic generateSimpleMessageTopic(String agency,
      String deviceId, MqttSimpleMessageType mType) {
    MqttSimpleMessageTopic topic = null;
    try {
      topic = new MqttSimpleMessageTopic(agency, 
          deviceId, mType);
    } catch (MqttSubscriptionTopicException e) {
      Log.e(TAG, "Error creating simple message topic -- should " +
          "never get here!", e);
    } catch (MqttMessageTopicException e) {
      Log.e(TAG, "Error creating simple message topic -- should " +
          "never get here!", e);
    }
    return topic;
  }

  public void sendExistMessage(Exist exist) {
    byte[] byteMessage = null;
    try {
      byteMessage = exist.getByteMessage();
    } catch (UninitializedExistException e) {
      Log.e(TAG, "Tried to send uninitialized exist message!", e);
    }

    if (byteMessage != null) {
      if (aic.isMqttConnected()) {
        MqttSimpleMessageTopic topic = generateSimpleMessageTopic(agency, 
            deviceId, MqttSimpleMessageType.EXIST);
        actuallySendSimpleMessage(byteMessage, topic);
      } else {
        final Exist newEx = exist.copy();
        // start thread to store exist message in database:
        pool.execute(new Runnable() {
          @Override
          public void run() {
            existQueue.addNewExist(newEx);
          }
        });
      }
    }
  }

  /**
   * Only call this function once connectivity has been verified.
   * @param byteMessage
   *      Byte contents of message
   * @param mType
   *      Type of message (exist or rawloc)
   */
  private void actuallySendSimpleMessage(byte[] byteMessage,
      MqttSimpleMessageTopic topic) {
    MqttMessage message = new MqttMessage();
    message.setQos(topic.getType().getQos());
    message.setRetained(topic.getType().getRetained());
    message.setPayload(byteMessage);
    if (topic != null) {
      mqttManager.sendMessage(topic.getTopic(), message);
    }
  }

  public void sendRawLocationMessage(RawLocation rawLoc) {
    long t = rawLoc.getTime();
    float v = rawLoc.getLocation().getSpeed();
    float vT = cv.SpeedThreshold();
    
    boolean old = t - prevRlSendTime > cv.RawLocationSendingCadence();
    boolean vCrossedThresh = (v < vT && prevRlSpeed >= vT) ||
        (v >= vT && prevRlSpeed < vT);
    
    // Send raw location if it has been N seconds or if the velocity passed
    // the threshold that determines "low speed"
    if (old || vCrossedThresh) {
      byte[] byteMessage = null;
      RawLocationConverter rlc = new RawLocationConverter(rawLoc);
      try {
        byteMessage = rlc.getByteMessage();
      } catch (UninitializedRawLocationException e) {
        Log.e(TAG, "Error converting raw location");
      }

      if (byteMessage != null) {
        prevRlSendTime = t;
        prevRlSpeed = v;
        if (aic.isMqttConnected()) {
          MqttMessage message = new MqttMessage();
          message.setQos(qos);
          message.setRetained(false);
          message.setPayload(byteMessage);
          MqttSimpleMessageTopic topic = generateSimpleMessageTopic(agency, 
              deviceId, MqttSimpleMessageType.RAW_LOCATION);
          if (topic != null) {
            mqttManager.sendMessage(topic.getTopic(), message);
          }
        } else {
          rlMessageQueue.add(rawLoc);
        }
      }
    }
  }

  public void startClearing() {
    mHandlerQueueClearing.removeCallbacks(mJobQueueClearing);
    mHandlerQueueClearing.post(mJobQueueClearing);
  }

  public void stopClearing() {
    mHandlerQueueClearing.removeCallbacks(mJobQueueClearing);
  }

  private void clearMessageQueues() {
    if (aic.isMqttConnected()) {

      // start thread to send messages:
      pool.execute(new Runnable() {
        @Override
        public void run() {
          clearMessageQueuesTask();
        }
      });
    } else if (aic.isInternetConnected() && !aic.isMqttConnected()) {
      mqttManager.createConnection(0);
      Log.d(TAG,"App has connectivity but it is not connected to MQTT");
    } else {
      Log.d(TAG, "Connection not available");
      if (mqttManager != null) {
        Log.d(TAG, "MQTT connection status: " + 
            mqttManager.getConnectionStatus().toString());
      }
    }
  }


  /**
   * @author jacob
   * 
   * Background task to send messages.
   *
   */
  private void clearMessageQueuesTask() {

    clearRawLocationMessageQueue();
    clearExistMessageQueue();

    // also work on the backlog of previously unsent messages:
    mqttManager.clearUnsentMessages();

    return;
  }

  private void clearRawLocationMessageQueue() {
    if (rlMessageQueue.size() >= cv.RawLocationMessageBufferSize()) {
      Log.d(TAG, "# of messages in raw location queue ("
          + rlMessageQueue.size() + ") exceeds buffer. Discarding" +
          "older messages.");
      while (rlMessageQueue.size() >= cv.RawLocationMessageBufferSize()) {
        rlMessageQueue.poll();
      }
    }

    for (int i = 0; i < cv.MessagesPerSend(); i++) {
      RawLocation rawLoc = rlMessageQueue.poll();
      if (rawLoc == null) {
        // we reached the end of the message queue (fewer messages than NPerSend):
        break;
      } else {
        byte[] byteMessage = null;
        RawLocationConverter rlc = new RawLocationConverter(rawLoc);
        try {
          byteMessage = rlc.getByteMessage();
          MqttSimpleMessageTopic topic = generateSimpleMessageTopic(agency, 
              deviceId, MqttSimpleMessageType.RAW_LOCATION);
          actuallySendSimpleMessage(byteMessage, topic);
        } catch (UninitializedRawLocationException e) {
          Log.e(TAG, "Error converting raw location");
        }
      }
    }
  }

  // requires DB operations -- don't execute on UI thread!
  private void clearExistMessageQueue() {
    Log.d(TAG, "Clearing messages from exist queue");
    List<Exist> msgs = existQueue.getOldestUnsentMessages(cv.MessagesPerSend());
    if (msgs.size() == 0) {
      Log.d(TAG, "No messages to send.");
      return;
    }

    // there unsent exist messages to send
    if (aic.isMqttConnected()) {
      List<Integer> ids = new ArrayList<Integer>();
      for (Exist message : msgs) {
        long sentTime = Util.getCurrentTimeWithGpsOffset();
        try {
          byte[] byteMessage = message.getByteMessage();
          byte[] fixedMessage = Util.changeExistSentTime(
              byteMessage, sentTime);
          message.setByteMessage(fixedMessage);
          message.setSentTime(sentTime);
          MqttSimpleMessageTopic topic = generateSimpleMessageTopic(agency, 
              deviceId, MqttSimpleMessageType.EXIST);
          actuallySendSimpleMessage(fixedMessage, topic);

          if (message.getId() != null) {
            ids.add(message.getId());
          }
        } catch (UninitializedExistException e) {
          Log.e(TAG, "Failed to change sent time in exist message!", e);
        }
      }

      // now remove the sent messages from the queue
      existQueue.removeSentMessages(ids);
    }
  }
}

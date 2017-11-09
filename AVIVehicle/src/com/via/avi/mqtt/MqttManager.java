package com.via.avi.mqtt;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.via.avi.config.ConfigValues;
import com.via.avi.mqtt.MqttListener.Action;
import com.via.avi.mqtt.MqttMessageTopic.MqttMessageTopicException;
import com.via.avi.mqtt.MqttSubscriptionTopic.MqttSubscriptionTopicException;
import com.via.avi.mqtt.config.MqttConfigValues;
import com.via.avi.utils.AndroidInternetChecker;
import com.via.mqtt.service.MqttClientAndroidService;

/**
 * @author Jacob Lynn
 *
 */
public class MqttManager implements MqttManagerInterface {

  private static String TAG = "MqttManager";

  /**
   * {@link ConnectionStatus} of the {@link MqttClientAndroidService}
   * represented by this <code>MqttManager</code> object. Default value is
   * {@link ConnectionStatus#NONE}
   **/
  private ConnectionStatus status = ConnectionStatus.NONE;

  /** The {@link MqttClientAndroidService} instance this class represents **/
  private MqttClientAndroidService client = null;

  private int reconnectAttempts = 0;
  private final int maxReconnectAttempts;
  private final int shortDelay;
  private final int longDelay;

  private Integer lastDelay;
  private Long lastConnectTime;
  private Integer lastConnectTimeout;

  public final int maxSubscriptionAttempts;

  private MqttConnectOptions conOpt = null;
  private ConfigValues cv;
  private MqttConfigValues mcv;

  private Context context;
  private final String clientId;

  private Handler connectHandler;
  private Runnable connectRunnable;
  private AndroidInternetChecker connChecker = null;

  private MqttUnsentQueue mqttQueue = null;
  private List<MqttSubscription> subList = new ArrayList<MqttSubscription>();

  public static class Builder {
    // MQTT required connection options
    private final String clientId;
    private final MqttConnectOptions conOpt;
    private Context context = null;
    private ConfigValues cv;
    private MqttConfigValues mcv;

    // Optional scope variables
    private MqttClientAndroidService client = null;
    private MqttUnsentQueue mqttQueue = null;
    private AndroidInternetChecker connChecker = null;

    // MQTT optional reconnection management options (w/ defaults)
    private Handler connectHandler = null;
    private int shortDelay = 10*1000;
    private int longDelay = 60*1000;
    private int maxReconnectAttempts = 60;
    private int maxSubscriptionAttempts = 5;

    public Builder(String clientId,
        MqttConnectOptions conOpt, Context context, ConfigValues cv,
        MqttConfigValues mcv) {
      this.clientId = clientId;
      this.conOpt = conOpt;
      this.context = context;
      this.cv = cv;
      this.mcv = mcv;
    }
    public Builder client(MqttClientAndroidService client){
      this.client = client; return this;
    }
    public Builder mqttQueue(MqttUnsentQueue mqttQueue){
      this.mqttQueue = mqttQueue; return this;
    }
    public Builder connectHandler(Handler connectHandler){
      this.connectHandler = connectHandler; return this;
    }
    public Builder shortDelay(int shortDelay){
      this.shortDelay = shortDelay; return this;
    }
    public Builder longDelay(int longDelay){
      this.longDelay = longDelay; return this;
    }
    public Builder maxReconnectAttempts(int maxReconnectAttempts){
      this.maxReconnectAttempts = maxReconnectAttempts; return this;
    }
    public Builder maxSubscriptionAttempts(int maxSubscriptionAttempts){
      this.maxSubscriptionAttempts = maxSubscriptionAttempts; return this;
    }
    public Builder connChecker(AndroidInternetChecker connChecker){
      this.connChecker = connChecker; return this;
    }
    public MqttManager build() {
      MqttManager mqttManager = new MqttManager(this);
      return mqttManager;
    }
  }

  private MqttManager(Builder builder) {
    clientId = builder.clientId;
    conOpt = builder.conOpt;
    context = builder.context;
    cv = builder.cv;
    mcv = builder.mcv;

    client = builder.client;
    mqttQueue = builder.mqttQueue;
    connectHandler = builder.connectHandler;
    connChecker = builder.connChecker;

    shortDelay = builder.shortDelay;
    longDelay = builder.longDelay;
    maxReconnectAttempts = builder.maxReconnectAttempts;
    maxSubscriptionAttempts = builder.maxSubscriptionAttempts;

    lastDelay = null;
    lastConnectTime = null;
    lastConnectTimeout = null;

    // handle complicated optional parameters
    if (client == null) {
      String uri = "tcp://" + mcv.Hostname() + ":" + mcv.Port();
      client = new MqttClientAndroidService(context,
          uri, clientId);
    }
    if (this.mqttQueue == null) {
      mqttQueue = new MqttUnsentQueue(this, cv);
    }
    if (this.connectHandler == null) {
      connectHandler = new Handler(Looper.getMainLooper());
    }
    if (this.connChecker == null) {
      connChecker = new AndroidInternetChecker(context);
    }

    MqttListener connCallback = new MqttListener(this, Action.CONNECT, 
        clientId);
    connectRunnable = new ConnectRunnable(conOpt, context, connCallback);
  }

  private class ConnectRunnable implements Runnable {
    private MqttConnectOptions conOpt;
    private Context context;
    private MqttListener connCallback;

    public ConnectRunnable(MqttConnectOptions conOpt, Context context, 
        MqttListener connCallback) {
      this.conOpt = conOpt;
      this.connCallback = connCallback;
      this.context = context;
    }

    @Override
    public void run() {
      try {
        client.connect(conOpt, context, connCallback);
      } catch (MqttException e) {
        Log.e(this.getClass().getCanonicalName(),
            "MqttException Occured on connnect", e);
      } catch (Exception e){
        Log.e(TAG,"Unexpected error on the connectRunnable",e);
      }
    }
  }

  /**
   * Cancel any pending connection attempts.
   */
  public void cancelConnectAttempts() {
    if (connectHandler != null) {
      connectHandler.removeCallbacksAndMessages(null);
    }
  }


  /**
   * Establishes actual MQTT connection to the broker. (To be run 
   * after successfully initializing the MqttService.)
   * 
   * @param delay Wait time (in milliseconds) before attempting to connect.
   */
  public void createConnection(int delay) {
    boolean doConnect = true;
    if (!connChecker.isInternetConnected()) {
      Log.i(TAG, "No internet connectivity. Aborting createConnection cycle.");
      return;
    }
    if (isConnecting()) {
      doConnect = false;
      long tNow = System.currentTimeMillis();
      Long lct = lastConnectTime;
      Integer ld = lastDelay;
      Integer lcto = lastConnectTimeout;
      if (lct != null) {
        doConnect = tNow > lct + ld + lcto + 20*1000l && !isConnected();
        if (doConnect) {
          Log.i(TAG, "Spent too much time waiting for a connection. Presumably"
              + " something bad happened. Trying again.");
          Log.i(TAG, "lastConnectTime: " + lct);
          Log.i(TAG, "lastDelay: " + ld);
          Log.i(TAG, "lastConnectTimeout: " + lcto);
        }
      }
    }

    if (doConnect) {
      try {
        Log.d(TAG,"Trying to create MQTT connection " +
            "in " + delay/1000 + " seconds...");

        changeConnectionStatus(ConnectionStatus.CONNECTING);
        lastConnectTime = System.currentTimeMillis();
        // convert from seconds to millis:
        lastConnectTimeout = conOpt.getConnectionTimeout()*1000;
        lastDelay = delay;

        client.setCallback(new MqttCallbackHandler(this));

        // do connect in separate thread in order to set delay 
        // without holding up main thread
        connectHandler.removeCallbacksAndMessages(null);
        connectHandler.postDelayed(connectRunnable, delay);
      } catch (Exception e) {
        Log.e(TAG,"Unexpected error in createConnection",e);

        // generally, this will be handled in the connection process.
        changeConnectionStatus(ConnectionStatus.ERROR);
      }
    } else {
      Log.d(TAG,"Already trying to connect to MQTT. Connection attempt ignored.");
    }

  }

  /**
   * Disconnect the underlying MQTT client (typically upon connectivity
   * failure).
   */
  public void disconnect() {
    if (isConnected()) {
      changeConnectionStatus(ConnectionStatus.DISCONNECTING);
      try {
        MqttListener disconnectListener = new MqttListener(
            this, Action.DISCONNECT, clientId);
        client.disconnect(conOpt.getConnectionTimeout(), context,
            disconnectListener);
      } catch (MqttException e) {
        Log.e(TAG, "MqttException Occured on disconnect", e);
      }
    } else {
      Log.d(TAG,"MqttManager isn't connected, so it can't disconnect." +
          "Clearing the connect handler.");
      connectHandler.removeCallbacksAndMessages(null);
    }
  }

  @Override
  public void connectionSuccess() {
    reconnectAttempts = 0;
    mqttQueue.clearUnsentMessages();

    // reset variables managing connection lifecycle
    lastConnectTime = null;
    lastConnectTimeout = null;
    lastDelay = null;

    // Always try to reconnect to the subscriptions that we ought to be 
    // subscribed to. MqttService may automatically resubscribe upon reconnect
    // in some cases (not sure), but this is safe because Mosquitto is 
    // configured to ignore duplicate subscriptions.
    for (MqttSubscription s : subList) {
      actuallySubscribe(s.getTopic().toString(), s.getQos());
    }
  }

  @Override
  public void connectionFailure() {
    if (!isConnecting()) {
      reconnectAttempts += 1;
      Log.d(TAG,"consecutive reconnect attempt number " + reconnectAttempts);
      if (reconnectAttempts < maxReconnectAttempts) {
        this.createConnection(shortDelay);
      } else {
        this.createConnection(longDelay);
      }
    }
  }

  @Override
  public void subscribeFailure(String topic) {
    if (isConnected()) {
      Log.i(TAG, "Attempting to resubscribe to " + topic);
      // Find corresponding mqtt subscription
      for (MqttSubscription mqttSub : subList) {
        if (mqttSub.getTopic().toString().equals(topic)) {
          // found a match -- resubscribe
          mqttSub.incrementFailures();
          if (mqttSub.getFailures() < maxSubscriptionAttempts) {
            actuallySubscribe(topic, mqttSub.getQos());
          }

          // break out of the loop, to make sure we don't subscribe multiple
          // times
          break;
        }
      }
    } else {
      Log.i(TAG, "Not connected -- not attempting to resubscribe to " + topic);
    }
  }

  private boolean actuallySubscribe(String topic, int qos) {
    if (!isConnected()) {
      return false;
    }
    try {
      String[] args = new String[1];
      args[0] = topic;
      Log.d(TAG, "Attempting to subscribe to topic " + topic
          + " on qos " + qos);
      client.subscribe(topic.toString(), qos, context,
          new MqttListener(this, Action.SUBSCRIBE, args));
    } catch (MqttException e) {
      Log.e(TAG, "Subscribe to topic " + topic + " with qos "
          + qos + " failed.", e);
      return false;
    }

    return true;
  }

  @Override
  public void sendMessage(MqttMessageTopic topic, MqttMessage mqttMessage) {
    sendMessage(topic.toString(), mqttMessage);
  }

  private void sendMessage(String topic, MqttMessage mqttMessage) {

    try {
      try {
        if (client.getPendingDeliveryTokens().length < mcv.MaxInFlight()) {
          Log.d(TAG,"Trying to publish an MqttMessage on topic " + topic);

          byte[] message = mqttMessage.getPayload();
          int qos = mqttMessage.getQos();
          boolean retained = mqttMessage.isRetained();
          client.publish(topic, message, qos, retained, null, 
              new MqttListener(this, Action.PUBLISH,
                  topic,
                  new String(message, MqttConfigValues.BYTE_ENCODING),
                  String.valueOf(qos),
                  String.valueOf(retained)));
        } else {
          // too many in-flight messages: queue the message for later
          Log.d(TAG,"Too many in-flight messages: queueing message.");
          queueUnsentMessage(topic, mqttMessage);
        }
      } catch (UnsupportedEncodingException e) {
        Log.e(TAG, "Bad encoding", e);
      }
    } catch (MqttPersistenceException e) {
      Log.e(TAG,"MqttPersistenceException!",e);
    } catch (MqttException e) {
      Log.e(TAG,"MqttException!",e);
    }
  }

  /**
   * Handles arrived MQTT messages of all types, processing as appropriate.
   * 
   * @param mqttTopic
   *		Topic of received MQTT message.
   * @param message
   * 		Body of received MQTT message.
   */
  public void handleMessage(MqttMessageTopic mqttTopic, MqttMessage message) {
    // Go through subscriptions, match on topics, and pass the message through.

    for (int i = subList.size()-1; i>=0; i--) {
      MqttSubscription sub = subList.get(i);
      if (mqttTopic.matchesSubscriptionTopic(sub.getTopic())) {
        // matching subscription.
        sub.onMessage(mqttTopic, message);
      }
    }
  }

  public void setConnectOptions(MqttConnectOptions newConOpt) {
    conOpt = newConOpt;
  }

  @Override
  public void subscribeSuccess(MqttSubscriptionTopic topic) {
    for (MqttSubscription mqttSub : subList) {
      if (mqttSub.getTopic().toString().equals(topic)) {
        // found a match -- resubscribe
        mqttSub.clearFailures();

        // break out of the loop, to make sure we don't subscribe multiple
        // times
        break;
      }
    }
  }

  @Override
  public void unsubscribeSuccess(MqttSubscriptionTopic topic) {
  }

  @Override
  public void unsubscribeFailure(String topic) {
    // Can get ourselves into dangerous infinite loops this way, just ignore
    // for now.
    //	  if (isConnected()) {
    //	    Log.i(TAG, "Attempting to unsubscribe again from " + topic);
    //	    actuallyUnsubscribe(topic);
    //	  } else {
    //      Log.i(TAG, "Not connected -- not attempting to unsubscribe again from " + topic);
    //	  }
  }

  @Override
  public void queueUnsentMessage(String topic, MqttMessage unsentMessage) {
    Log.d(TAG,"Queueing unsent message w/ topic " + topic);
    try {
      MqttMessageTopic mTopic = new MqttMessageTopic(topic);
      mqttQueue.queueUnsentMessage(mTopic, unsentMessage);
    } catch (MqttSubscriptionTopicException e) {
      Log.e(TAG, "Failed to parse topic string into MqttMessageTopic", e);
      Log.e(TAG, "topic: " + topic);
    } catch (MqttMessageTopicException e) {
      Log.e(TAG, "Failed to parse topic string into MqttMessageTopic", e);
      Log.e(TAG, "topic: " + topic);
    }
  }

  @Override
  public void clearUnsentMessages() {
    mqttQueue.clearUnsentMessages();
  }

  @Override
  public void subscribe(MqttSubscription mqttSub) {
    // check first to see if we need to actually subscribe -- if we already
    // have a subscription to this topic, no need to duplicate.
    boolean match = false;
    for (MqttSubscription mqttSubPrev : subList) {
      if (mqttSub.getTopic().equals(mqttSubPrev.getTopic())) {
        match = true;
        subList.add(mqttSub);
        break;
      }
    }
    String topic = mqttSub.getTopic().toString();
    if (!match) {
      subList.add(mqttSub);
      actuallySubscribe(topic, mqttSub.getQos());
    }
  }

  @Override
  public void unsubscribe(MqttSubscriptionTopic mqttTopic) {
    for (int i = subList.size()-1; i>=0; i--) {
      MqttSubscription sub = subList.get(i);
      // exact equality of message topic string
      if (mqttTopic.toString().equals(sub.getTopic().toString())) {
        // matching subscription. Unsubscribe.
        String topic = sub.getTopic().toString();
        unsubscribeTopic(topic, i);
      }
    }
  }

  @Override
  public void unsubscribeAll() {
    // unsubscribe from all subscriptions.
    for (int i = subList.size()-1; i>=0; i--) {
      MqttSubscription sub = subList.get(i);
      String topic = sub.getTopic().toString();
      unsubscribeTopic(topic, i);
    }
  }

  private void unsubscribeTopic(String topic, int subIndex) {
    subList.remove(subIndex);
    actuallyUnsubscribe(topic);
  }

  private boolean actuallyUnsubscribe(String topic) {
    try {
      String[] args = new String[1];
      args[0] = topic;
      client.unsubscribe(topic, context,
          new MqttListener(this, Action.UNSUBSCRIBE, args));
    } catch (MqttException e) {
      Log.e(TAG, "Unsubscribe from topic " + topic + " failed.", e);
      return false;
    }
    return true;
  }

  public boolean isConnected() {
    return status == ConnectionStatus.CONNECTED;
  }

  public boolean isConnecting() {
    return status == ConnectionStatus.CONNECTING;
  }

  public boolean isConnectedOrConnecting() {
    return (status == ConnectionStatus.CONNECTED)
        || (status == ConnectionStatus.CONNECTING);
  }

  @Override
  public void changeConnectionStatus(ConnectionStatus connectionStatus) {
    status = connectionStatus;
  }

  @Override
  public ConnectionStatus getConnectionStatus() {
    return status;
  }
}

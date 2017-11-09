package com.via.avi.mqtt;

import java.io.UnsupportedEncodingException;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import android.annotation.SuppressLint;
import android.util.Log;

import com.via.avi.mqtt.MqttManagerInterface.ConnectionStatus;
import com.via.avi.mqtt.MqttSubscriptionTopic.MqttSubscriptionTopicException;
import com.via.avi.mqtt.config.MqttConfigValues;
import com.via.mqtt.service.MqttClientAndroidService;

/**
 * This Class handles receiving information from the
 * {@link MqttClientAndroidService} and updating the {@link Connection}
 * associated with the action
 */
public class MqttListener implements IMqttActionListener {

  /**
   * Actions that can be performed Asynchronously <strong>and</strong>
   * associated with a {@link ActionListener} object
   * 
   */
  enum Action {
    /** Connect Action **/
    CONNECT,
    /** Disconnect Action **/
    DISCONNECT,
    /** Subscribe Action **/
    SUBSCRIBE,
    /** Unubscribe Action **/
    UNSUBSCRIBE,
    /** Publish Action **/
    PUBLISH
  }

  public static String TAG = "MqttListener";

  /**
   * The {@link Action} that is associated with this instance of
   * <code>ActionListener</code>
   **/
  private Action action;
  /** The arguments passed to be used for formatting strings **/
  private String[] additionalArgs;
  private MqttManagerInterface mqttManager;

  /**
   * Creates a generic action listener for actions performed form any activity
   * 
   * @param context
   *          The application context
   * @param action
   *          The action that is being performed
   * @param additionalArgs
   *          Used for as arguments for string formating
   */
  public MqttListener(MqttManagerInterface mqttManager, Action action, 
      String... additionalArgs) {
    this.mqttManager = mqttManager;
    this.action = action;
    this.additionalArgs = additionalArgs;
  }

  /**
   * The action associated with this listener has been successful.
   * 
   * @param asyncActionToken
   *          This argument is not used
   */
  @Override
  public void onSuccess(IMqttToken asyncActionToken) {
    switch (action) {
    case CONNECT:
      connect();
      break;
    case DISCONNECT:
      disconnect();
      break;
    case SUBSCRIBE:
      subscribe();
      break;
    case UNSUBSCRIBE:
      unsubscribe();
      break;
    case PUBLISH:
      publish();
      break;
    }

  }

  /**
   * A publish action has been successfully completed, update connection object
   * associated with the client this action belongs to, then notify the user of
   * success
   */
  @SuppressLint("StringFormatMatches")
  private void publish() {
    //		Log.d(TAG,"publish success");
  }

  /**
   * A subscribe action has been successfully completed, update the connection
   * object associated with the client this action belongs to and then notify
   * the user of success
   */
  protected void subscribe() {
    Log.d(TAG,"subscribe success");

    MqttSubscriptionTopic topic = null;
    try {
      topic = new MqttSubscriptionTopic(
          MqttSubscriptionTopic.parseElementsFromTopicString(additionalArgs[0]));

      Log.d(TAG,"to topic " + topic.toString());
    } catch (MqttSubscriptionTopicException e) {
      Log.e(TAG,"Failed to parse topic string into MqttSubscriptionTopic.",e);
    }
    this.mqttManager.subscribeSuccess(topic);
  }

  /**
   * An unsubscribe action has been successfully completed, update the connection
   * object associated with the client this action belongs to and then notify
   * the user of success
   */
  protected void unsubscribe() {
    Log.d(TAG,"unsubscribe success");

    MqttSubscriptionTopic topic = null;
    try {
      topic = new MqttSubscriptionTopic(
          MqttSubscriptionTopic.parseElementsFromTopicString(additionalArgs[0]));
    } catch (MqttSubscriptionTopicException e) {
      Log.e(TAG,"Failed to parse topic string into MqttSubscriptionTopic.",e);
    }
    this.mqttManager.unsubscribeSuccess(topic);
  }

  /**
   * A disconnection action has been successfully completed, update the
   * connection object associated with the client this action belongs to and
   * then notify the user of success.
   */
  private void disconnect() {
    mqttManager.changeConnectionStatus(ConnectionStatus.DISCONNECTED);
    Log.d(TAG,"disconnect success");
  }

  /**
   * A connection action has been successfully completed, update the connection
   * object associated with the client this action belongs to and then notify
   * the user of success.
   */
  private void connect() {
    mqttManager.changeConnectionStatus(ConnectionStatus.CONNECTED);
    Log.d(TAG,"connect success");
    mqttManager.connectionSuccess();
  }

  /**
   * The action associated with the object was a failure
   * 
   * @param token
   *          This argument is not used
   * @param exception
   *          The exception which indicates why the action failed
   */
  @Override
  public void onFailure(IMqttToken token, Throwable exception) {
    switch (action) {
    case CONNECT:
      connect(exception);
      break;
    case DISCONNECT:
      disconnect(exception);
      break;
    case SUBSCRIBE:
      subscribe(exception);
      break;
    case UNSUBSCRIBE:
      unsubscribe(exception);
      break;
    case PUBLISH:
      publish(exception);
      break;
    }

  }

  /**
   * A publish action was unsuccessful, notify user and update client history
   * 
   * @param exception
   *          This argument is not used
   */
  @SuppressLint("StringFormatMatches")
  private void publish(Throwable exception) {

    if (exception == null || exception.getMessage() == null || !exception.getMessage().equals("Too many publishes in progress")) {

      if (!mqttManager.isConnecting()) {
        mqttManager.changeConnectionStatus(ConnectionStatus.ERROR);
      }
      if (exception != null && exception.getMessage() != null){
        Log.e(TAG,"MQTT client failed to publish for unknown reason.", exception);
      } else if (exception == null){
        Log.e(TAG,"MQTT client failed to publish and sent a null Exception");
      } else {
        Log.e(TAG,"MQTT client failed to publish and sent an exception with Null message");
      }

      mqttManager.connectionFailure();
    } else {
      Log.e(TAG,"MQTT client failed to publish because too many messages are in flight.", exception);
    }

    Log.e(TAG, "Queueing message.");

    if (additionalArgs.length == 4) {
      try {
        String topic = additionalArgs[0];

        MqttMessage unsentMessage = new MqttMessage();
        unsentMessage.setPayload(additionalArgs[1].getBytes(MqttConfigValues.BYTE_ENCODING));

        unsentMessage.setQos(Integer.parseInt(additionalArgs[2]));
        unsentMessage.setRetained(Boolean.parseBoolean(additionalArgs[3]));

        mqttManager.queueUnsentMessage(topic, unsentMessage);
        Log.d(TAG,"Queueing unsent message.");
      }
      catch (UnsupportedEncodingException e) {
        Log.e(TAG,"Couldn't queue unsent message due to bad encoding.", e);
      }
    }
  }

  /**
   * A subscribe action was unsuccessful, notify user and update client history
   * 
   * @param exception
   *          This argument is not used
   */
  protected void subscribe(Throwable exception) {
    Log.e(TAG, "MQTT client failed to subscribe.", exception);

    String topic = additionalArgs[0];
    mqttManager.subscribeFailure(topic);
  }

  /**
   * An unsubscribe action was unsuccessful, notify user and update client history
   * 
   * @param exception
   *          This argument is not used
   */
  protected void unsubscribe(Throwable exception) {
    Log.e(TAG,"MQTT client failed to unsubscribe.", exception);
    String topic = additionalArgs[0];
    mqttManager.unsubscribeFailure(topic);
  }

  /**
   * A disconnect action was unsuccessful, notify user and update client history
   * 
   * @param exception
   *          This argument is not used
   */
  private void disconnect(Throwable exception) {
    if (!mqttManager.isConnecting()) {
      mqttManager.changeConnectionStatus(ConnectionStatus.ERROR);
    }

    Log.e(TAG,"MQTT client failed on disconnect.", exception);
  }

  /**
   * A connect action was unsuccessful, notify the user and update client
   * history
   * 
   * @param exception
   *          This argument is not used
   */
  private void connect(Throwable exception) {		
    mqttManager.changeConnectionStatus(ConnectionStatus.ERROR);

    Log.e(TAG,"MQTT connection failed.", exception);
    mqttManager.connectionFailure();
  }

}

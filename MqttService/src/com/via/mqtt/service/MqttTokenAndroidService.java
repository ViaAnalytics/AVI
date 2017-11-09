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

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttWireMessage;

/**
 * <p>
 * Implementation of the IMqttToken interface for use from within the
 * MqttClientAndroidService implementation
 */

class MqttTokenAndroidService implements IMqttToken {

  private IMqttActionListener listener;

  private volatile boolean isComplete;

  private volatile MqttException lastException;

  private Object waitObject = new Object();

  private MqttClientAndroidService client;

  private Object userContext;

  private String[] topics;

  private IMqttToken delegate; // specifically for getMessageId

  private MqttException pendingException;

  /**
   * standard constructor
   * 
   * @param client
   * @param userContext
   * @param listener
   */
  MqttTokenAndroidService(MqttClientAndroidService client,
      Object userContext, IMqttActionListener listener) {
    this(client, userContext, listener, (String[]) null);
  }

  /**
   * constructor for use with subscribe operations
   * 
   * @param client
   * @param userContext
   * @param listener
   * @param topics
   */
  MqttTokenAndroidService(MqttClientAndroidService client,
      Object userContext, IMqttActionListener listener, String[] topics) {
    this.client = client;
    this.userContext = userContext;
    this.listener = listener;
    this.topics = topics;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#waitForCompletion()
   */
  @Override
  public void waitForCompletion() throws MqttException, MqttSecurityException {
    synchronized (waitObject) {
      try {
        waitObject.wait();
      }
      catch (InterruptedException e) {
        // do nothing
      }
    }
    if (pendingException != null) {
      throw pendingException;
    }
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#waitForCompletion(long)
   */
  @Override
  public void waitForCompletion(long timeout) throws MqttException,
      MqttSecurityException {
    synchronized (waitObject) {
      try {
        waitObject.wait(timeout);
      }
      catch (InterruptedException e) {
        // do nothing
      }
      if (!isComplete) {
        throw new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT);
      }
      if (pendingException != null) {
        throw pendingException;
      }
    }
  }

  /**
   * notify successful completion of the operation
   */
  void notifyComplete() {
    synchronized (waitObject) {
      isComplete = true;
      waitObject.notifyAll();
      if (listener != null) {
        listener.onSuccess(this);
      }
    }
  }

  /**
   * notify unsuccessful completion of the operation
   */
  void notifyFailure(Throwable exception) {
    synchronized (waitObject) {
      isComplete = true;
      if (exception instanceof MqttException) {
        pendingException = (MqttException) exception;
      }
      else {
        pendingException = new MqttException(exception);
      }
      waitObject.notifyAll();
      if (exception instanceof MqttException) {
        lastException = (MqttException) exception;
      }
      if (listener != null) {
        listener.onFailure(this, exception);
      }
    }

  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#isComplete()
   */
  @Override
  public boolean isComplete() {
    return isComplete;
  }

  void setComplete(boolean complete) {
    isComplete = complete;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#getException()
   */
  @Override
  public MqttException getException() {
    return lastException;
  }

  void setException(MqttException exception) {
    lastException = exception;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#getClient()
   */
  @Override
  public IMqttAsyncClient getClient() {
    return client;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#setActionCallback(IMqttActionListener)
   */
  @Override
  public void setActionCallback(IMqttActionListener listener) {
    this.listener = listener;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#getActionCallback()
   */
  @Override
  public IMqttActionListener getActionCallback() {
    return listener;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#getTopics()
   */
  @Override
  public String[] getTopics() {
    return topics;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#setUserContext(Object)
   */
  @Override
  public void setUserContext(Object userContext) {
    this.userContext = userContext;

  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#getUserContext()
   */
  @Override
  public Object getUserContext() {
    return userContext;
  }

  void setDelegate(IMqttToken delegate) {
    this.delegate = delegate;
  }

  /**
   * @see org.eclipse.paho.client.mqttv3.IMqttToken#getMessageId()
   */
  @Override
  public int getMessageId() {
    return (delegate != null) ? delegate.getMessageId() : 0;
  }

  @Override
  public int[] getGrantedQos() {
    return delegate.getGrantedQos();
  }

  @Override
  public MqttWireMessage getResponse() {
    return delegate.getResponse();
  }

  @Override
  public boolean getSessionPresent() {
    return delegate.getSessionPresent();
  }
}

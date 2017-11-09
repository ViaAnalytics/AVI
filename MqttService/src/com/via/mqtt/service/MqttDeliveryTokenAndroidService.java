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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * <p>
 * Implementation of the IMqttDeliveryToken interface for use from within the
 * MqttClientAndroidService implementation
 */
class MqttDeliveryTokenAndroidService extends MqttTokenAndroidService
		implements IMqttDeliveryToken {

	// The message which is being tracked by this token
	private MqttMessage message;

	MqttDeliveryTokenAndroidService(MqttClientAndroidService client,
			Object userContext, IMqttActionListener listener, MqttMessage message) {
		super(client, userContext, listener);
		this.message = message;
	}

	/**
	 * @see org.eclipse.paho.client.mqttv3.IMqttDeliveryToken#getMessage()
	 */
	@Override
	public MqttMessage getMessage() throws MqttException {
		return message;
	}

	void setMessage(MqttMessage message) {
		this.message = message;
	}

	void notifyDelivery(MqttMessage delivered) {
		message = delivered;
		super.notifyComplete();
	}

}

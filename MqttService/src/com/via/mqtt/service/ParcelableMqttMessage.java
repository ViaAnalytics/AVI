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

import org.eclipse.paho.client.mqttv3.MqttMessage;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * <p>
 * A way to flow MqttMessages via Bundles/Intents
 * </p>
 * 
 * <p>
 * An application will probably use this only when receiving a message from a
 * Service in a Bundle - the necessary code will be something like this :-
 * 
 * <pre>
 * <code>
 * 	private void messageArrivedAction(Bundle data) {
 * 		ParcelableMqttMessage message = (ParcelableMqttMessage) data
 * 			.getParcelable(MqttServiceConstants.CALLBACK_MESSAGE_PARCEL);
 *		<i>Use the normal {@link MqttMessage} methods on the the message object.</i>
 * 	}
 * 
 * </code>
 * </pre>
 * 
 * </p>
 * <p>
 * It is unlikely that an application will directly use the methods which are
 * specific to this class.
 * <p>
 */

// Relies on knowledge of MqttMessage internals, unfortunately

public class ParcelableMqttMessage extends MqttMessage implements Parcelable {

	ParcelableMqttMessage(MqttMessage original) {
		super(original.getPayload());
		setQos(original.getQos());
		setRetained(original.isRetained());
		setDuplicate(original.isDuplicate());
	}

	ParcelableMqttMessage(Parcel parcel) {
		super(parcel.createByteArray());
		setQos(parcel.readInt());
		boolean[] flags = parcel.createBooleanArray();
		setRetained(flags[0]);
		setDuplicate(flags[1]);
	}

	/**
	 * Describes the contents of this object
	 */
	@Override
	public int describeContents() {
		return 0;
	}

	/**
	 * Writes the contents of this object to a parcel
	 * 
	 * @param parcel
	 *            The parcel to write the data to.
	 * @param flags
	 *            this parameter is ignored
	 */
	@Override
	public void writeToParcel(Parcel parcel, int flags) {
		parcel.writeByteArray(getPayload());
		parcel.writeInt(getQos());
		parcel.writeBooleanArray(new boolean[] { isRetained(), isDuplicate() });
	}

	/**
	 * A creator which creates the message object from a parcel
	 */
	public static final Parcelable.Creator<ParcelableMqttMessage> CREATOR = new Parcelable.Creator<ParcelableMqttMessage>() {

		/**
		 * Creates a message from the parcel object
		 */
		@Override
		public ParcelableMqttMessage createFromParcel(Parcel parcel) {
			return new ParcelableMqttMessage(parcel);
		}

		/**
		 * creates an array of type {@link ParcelableMqttMessage}[]
		 * 
		 */
		@Override
		public ParcelableMqttMessage[] newArray(int size) {
			return new ParcelableMqttMessage[size];
		}
	};
}
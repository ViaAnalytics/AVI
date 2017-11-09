package com.via.avi.messages;

import com.via.avi.messages.RawLocation;

/**
 * @author jacob
 *
 * Interface to manage sending AVI-specific messages to the outside world.
 * Implementations of this interface will deal directly with messaging 
 * libraries (e.g. MQTT).
 */
public interface MessageSender {

  /**
   * Send a raw location message now (or store for later sending, depending on 
   * connectivity). 
   * 
   * @param rawLoc
   *      Message to send.
   */
  public void sendRawLocationMessage(RawLocation rawLoc);

  /**
   * Start sending messages from the internal queue -- to be called when
   * e.g. connectivity is established.
   */
  public void startClearing();

  /**
   * Stop sending messages from the internal queue -- to be called when
   * e.g. connectivity is lost.
   */
  public void stopClearing();
}

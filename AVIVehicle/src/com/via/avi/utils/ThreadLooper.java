package com.via.avi.utils;

public interface ThreadLooper {
  public void runOnce();
  public void startLooping();
  public void stopLooping();
  public boolean isLooping();
  public void setLoopMillis(long loopMillis);
}

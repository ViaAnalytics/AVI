package com.via.avi.screen;

import com.via.avi.config.ConfigValues;
import com.via.avi.gs.DeviceState;
import com.via.avi.gs.UpdatableGlobalState;
import com.via.avi.utils.DefaultThreadLooper;
import com.via.avi.utils.Util;
import com.via.avi.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.BatteryManager;
import android.util.Log;
import android.view.WindowManager.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ScreenController {
  private static String TAG = "ScreenController";

  public static enum TextViewState {
    DEFAULT(R.string.transmittingData),

    // "device status" related text views
    GPS_ISSUE(R.string.inactiveGPS),
    CONNECTIVITY_ISSUE(R.string.inactiveComm),
    SLEEP_MODE(R.string.sleepMode),
    DISCHARGING(R.string.dischargingBattery);

    private int rLookup;
    private TextViewState(int rLookup) {this.rLookup = rLookup;}
    public String getText(Context context) {return context.getString(rLookup);}
  }

  private Activity mainActivity;

  private ConfigValues cv;

  // Tools for looping basic screen updates: text views, etc
  private UpdateLooper updateLooper;
  private long updateMillis = 1000;
  private boolean awake;

  /* variables related to text view */
  private TextViewState currentTextView;

  private Context mContext;

  /**
   * Instantiate object to control the view
   * 
   * @param mainActivity
   *      Main activity class that uses this view controller.
   * @param mArrowView
   *      Initialized object of arrow view type.
   * @param mSingleBarView
   *      Initialized object of "single bar view" type.
   * @param screenHeight
   *      Effective height of display, in pixels.
   * @param screenWidth
   *      Effective width of display, in pixels.
   */
  public ScreenController(Activity mainActivity, ConfigValues cv) {
    this.mainActivity = mainActivity;
    this.cv = cv;
    mainActivity.setRequestedOrientation(cv.ScreenOrientation());
    mContext = mainActivity.getApplicationContext();

    mainActivity.getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);

    // initialize variables related to text view management
    currentTextView = null;

    updateLooper = new UpdateLooper(updateMillis);
    updateLooper.runOnce();
  }

  private void drawBlackBackgroundView(final String viewText) {
    Runnable drawRunnable = new Runnable() {

      @Override
      public void run() {
        mainActivity.setContentView(R.layout.black_background_layout);
        RelativeLayout countDownLayout = (RelativeLayout) mainActivity.findViewById(R.id.countdownBackground);
        TextView timeLeft = (TextView) mainActivity.findViewById(R.id.blackBackgroundText);
        countDownLayout.setBackgroundColor(Color.BLACK);
        timeLeft.setTextSize(50);// in scaled pixel
        timeLeft.setText(viewText);
        Log.d(TAG,"Set Black Background view with text:" + viewText);
      }
    };

    mainActivity.runOnUiThread(drawRunnable);
  }

  /**
   * Set message to be displayed to operators (and stop animating). Must 
   * be manually ended with a call to startAnimating().
   * 
   * @param viewText
   *      Text to be displayed on screen.
   */
  private void startBlackBackgroundView(TextViewState textViewState) {
    if (textViewState != currentTextView) {
      drawBlackBackgroundView(textViewState.getText(mContext));
      currentTextView = textViewState;
    }
  }

  public void setAwake(boolean awake) {
    this.awake = awake;

    if (awake) {
      updateLooper.startLooping();
    } else {
      updateLooper.stopLooping();
    }

    // update screen once more, to ensure we set the "sleep" screen
    updateLooper.runOnce();
  }

  private class UpdateLooper extends DefaultThreadLooper {
    public UpdateLooper(long loopMillis) {
      super("UpdateLooper", loopMillis);
    }

    @Override
    public void runOnce() {
      // First, check for sleep mode screen.
      if (!awake) {
        startBlackBackgroundView(TextViewState.SLEEP_MODE);
        return;
      }

      // Next, check for out of date GPS.
      DeviceState ds = UpdatableGlobalState.getInstance().getDeviceState();
      long gpsAge = Util.getCurrentTimeWithGpsOffset() - ds.getLastGpsTime();

      // Finally, see if we need to set the bad GPS text state. 
      if (gpsAge > cv.MaxGpsAge()) {
        startBlackBackgroundView(TextViewState.GPS_ISSUE);
        return;
      }

      // Check for old connectivity.
      long commAge = Util.getCurrentTimeWithGpsOffset() - ds.getLastCommTime();
      if (commAge > cv.MaxConnectivityAge()) {
        startBlackBackgroundView(TextViewState.CONNECTIVITY_ISSUE);
        return;
      }

      // Check for discharging.
      if (isDischarging()) {
        startBlackBackgroundView(TextViewState.DISCHARGING);
        return;
      }

      // Default text mode if nothing else is appropriate
      startBlackBackgroundView(TextViewState.DEFAULT);
    }
  }

  private boolean isDischarging() {
    DeviceState ds = UpdatableGlobalState.getInstance().getDeviceState();
    return ds.getBatteryChargingStatus() == 
        BatteryManager.BATTERY_STATUS_DISCHARGING;
  }
}

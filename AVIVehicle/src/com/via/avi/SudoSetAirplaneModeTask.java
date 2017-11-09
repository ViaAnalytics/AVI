package com.via.avi;

import android.os.AsyncTask;
import android.util.Log;

public class SudoSetAirplaneModeTask extends AsyncTask<Void, Void, Void> {
  private static String TAG = "SudoSetAirplaneModeTask";
  private boolean targetMode = false;

  public SudoSetAirplaneModeTask(boolean targetMode) {
    this.targetMode = targetMode;
  }

  protected Void doInBackground(Void... args) {
    if (targetMode) {
      try {
        Log.w(TAG, "Setting AirplaneMode ON from AVI App.");
        Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "settings put global airplane_mode_on 1" });
        proc.waitFor();
        Process proc2 = Runtime.getRuntime().exec(new String[] { "su", "-c", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true" });
        proc2.waitFor();
      } catch (Exception ex) {
        Log.i(TAG, "Could not set AirplaneMode ON", ex);
      }
    } else {
      try {
        Log.w(TAG, "Setting AirplaneMode OFF from AVI App.");
        Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "settings put global airplane_mode_on 0" });
        proc.waitFor();
        Process proc2 = Runtime.getRuntime().exec(new String[] { "su", "-c", "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false" });
        proc2.waitFor();
      } catch (Exception ex) {
        Log.i(TAG, "Could not set AirplaneMode OFF", ex);
      }
    }

    Log.d(TAG, "Finished setting airplane mode to " + targetMode);

    return null;
  }
}
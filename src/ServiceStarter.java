package com.monmouth.monmouthtelecom;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.content.Context;
import androidx.core.content.ContextCompat;
import com.monmouth.fayePG.FayeService;
import com.monmouth.SIP.SipService;

import java.util.Map;


public class ServiceStarter extends BroadcastReceiver {
  public static final String LOG_TAG = "ServiceStarter";
  public static final String fayePrefix = "fayeIntentExtras_";
  public static final String sipPrefix = "sipIntentExtras_";
  private static final String offline = "Offline";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG,"ServiceStarter, context: " + context.toString() + " intent: " + intent.getAction());
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    boolean activeForward = preferences.getBoolean("activeForward", false);
    boolean activeVoipForward = preferences.getBoolean("activeVoipForward", false);
    boolean chatConnected = preferences.getBoolean("chatConnected", false);
    String chatStatus = preferences.getString("chatStatus", null);
    Log.d(LOG_TAG,"activeForward: " + activeForward + " activeVoipForward: " + activeVoipForward + " chatConnected: " + chatConnected + " chatStatus: " + chatStatus);
    if (activeForward || activeVoipForward || (chatConnected && chatStatus != null && !chatStatus.equals(offline))) {
      Intent fayeIntent = buildIntent(context, fayePrefix, preferences);
      if (fayeIntent != null) {
        Log.i(LOG_TAG, "Starting FayeService");
        fayeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ContextCompat.startForegroundService(context,fayeIntent);
      }
      if (activeVoipForward) {
        Intent sipIntent = buildIntent(context, sipPrefix, preferences);
        if (sipIntent != null) {
          Log.i(LOG_TAG, "Starting SipService");
          sipIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          ContextCompat.startForegroundService(context,sipIntent);
        }
      }
    }
  }

  private Intent buildIntent(Context context, String prefix, SharedPreferences preferences) {
    Intent intent = null;
    if (prefix.equals(fayePrefix))
      intent = new Intent(context, FayeService.class);
    else if (prefix.equals(sipPrefix))
      intent = new Intent(context, SipService.class);
    else {
      Log.e(LOG_TAG, "Error: no service found for " + prefix);
      return null;
    }
    if (addExtras(intent, prefix, preferences) == 0) {
      Log.e(LOG_TAG, "Error: no intent extras found for " + prefix);
      return null;
    }
    return intent;
  }

  private int addExtras(Intent intent, String prefix, SharedPreferences preferences) {
    int extrasAdded = 0;
    Map<String,?> extras = preferences.getAll();
    for(Map.Entry<String,?> e : extras.entrySet()){
      if (e.getKey().startsWith(prefix)){
        extrasAdded++;
        Object value = e.getValue();
        String className = value.getClass().getSimpleName();
        if (className.equals("String"))
          intent.putExtra(e.getKey().substring(prefix.length()), (String) e.getValue());
        else if (className.equals("Integer"))
          intent.putExtra(e.getKey().substring(prefix.length()),(Integer) e.getValue());
        else if (className.equals("Boolean"))
          intent.putExtra(e.getKey().substring(prefix.length()),(Boolean) e.getValue());
        else if (className.equals("Float"))
          intent.putExtra(e.getKey().substring(prefix.length()),(Float) e.getValue());
        else if (className.equals("Long"))
          intent.putExtra(e.getKey().substring(prefix.length()),(Long) e.getValue());
      }
    }
    return extrasAdded;
  }
}

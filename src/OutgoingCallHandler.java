package com.monmouth.monmouthtelecom;

import android.content.BroadcastReceiver;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.net.Uri;

import java.util.Iterator;
import java.util.Set;
import java.util.Calendar;
import android.os.Bundle;

/**
 * Created by ytam on 11/21/16.
 */
public class OutgoingCallHandler extends BroadcastReceiver {

  private static final String LOG_TAG = "OutgoingCallHandler";
  private static final long ACCESS_NUMBER_VALID_FOR = 50000;

  @Override
  public void onReceive(Context context, Intent intent) {
    String phoneNumber = getResultData();
    if (phoneNumber == null) {
      phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
    }
    Uri uri = Uri.parse("tel:"+phoneNumber);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    String accessNumber = prefs.getString("access_number", null);
    long accessNumberCreatedAt = prefs.getLong("access_number_created_at", 0);
    long currentTimeInMilllis = Calendar.getInstance().getTimeInMillis();
    boolean validAccessNumber = (currentTimeInMilllis - accessNumberCreatedAt) < OutgoingCallHandler.ACCESS_NUMBER_VALID_FOR;
    boolean defaultDialer = prefs.getBoolean("default_dialer", false);
    boolean inCall = prefs.getBoolean("inCall", false);
    boolean inVoipCall = prefs.getBoolean("inVoipCall", false);

    if (defaultDialer && !inCall && !inVoipCall) {
      if (accessNumber != null && validAccessNumber && accessNumber.equals(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER))) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("access_number");
        editor.remove("access_number_created_at");
        editor.apply();
      } else {
        // use cordova webintent plugin to retrieve uri
        intent.setData(uri);
        intent.setClass(context.getApplicationContext(), MonmouthTelecom.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        setResultData(null);
        return;
      }
      Intent bringDefaultPhoneAppToFront = new Intent(Intent.ACTION_CALL_BUTTON);
      bringDefaultPhoneAppToFront.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.getApplicationContext().startActivity(bringDefaultPhoneAppToFront);
      setResult(getResultCode(), getResultData(), getResultExtras(false));
    }
  }
}

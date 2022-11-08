/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.monmouth.monmouthtelecom;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.view.View;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.webkit.CookieManager;
import org.apache.cordova.*;
import android.view.WindowManager;
import android.content.Intent;
import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.monmouth.SIP.LinphoneClient;

public class MonmouthTelecom extends CordovaActivity implements SensorEventListener
{
  private static final String LOG_TAG = "MonmouthTelecom";
  private static boolean activityPaused = false;
  private static boolean activityResumed = false;
  private boolean proximityMonitoring = false;
  private SensorManager mSensorManager;
  private Sensor mProximity;
  private String chatUserName;
  private String chatFullName;
  private PhoneStateListener mTeleListener;
  private TelephonyManager telephonyMgr;
  private WakeLock proxWakeLock;
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    CookieManager.setAcceptFileSchemeCookies(true);
    super.onCreate(savedInstanceState);
    super.init();
    super.loadUrl(Config.getStartUrl());

    mSensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
    telephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    registerCallStateListener();
    if (mProximity == null)
      Log.e(LOG_TAG, "proximity sensor not supported!");
    else
      Log.i(LOG_TAG, "proximity sensor max range: " + mProximity.getMaximumRange());

    View mView = this.appView.getView();
    if (mView instanceof WebView) {
      WebView mWebView = (WebView) mView;
      mWebView.getSettings().setTextZoom(100);
    } else {
      Log.e(LOG_TAG, "WTF?!? not a webview?!?");
    }

    Bundle bundle = getIntent().getExtras();
    if(bundle == null)
      return;
    if(bundle.getCharSequence("category","none").toString().equals("chat")){
      chatUserName = bundle.getCharSequence("name").toString();
      chatFullName = bundle.getCharSequence("fullName").toString();
    }else{
      chatUserName = "";
      chatFullName = "";
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (intent.getBooleanExtra("launchAppForIncSIPCall", false)) {
      Log.i(LOG_TAG, "MonmouthTelecom setting launchApp for SIP calls flags");
      this.getWindow().addFlags(
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
              WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
              WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
              WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }
    if (intent.getBooleanExtra("launchAppForIncMTTCall", false)) {
      Log.i(LOG_TAG, "MonmouthTelecom setting launchApp for MTT calls flags");
      this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
      if (!isProximityMonitoring()) {
        startProximityMonitoring();
        if (mTeleListener == null)
          mTeleListener = new PhoneStateListener();
        telephonyMgr.listen(mTeleListener, PhoneStateListener.LISTEN_CALL_STATE);
      }
    }
    this.setIntent(intent);
  }

  @Override
  public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Do something here if sensor accuracy changes.
  }

  @Override
  public final void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
      Log.i(LOG_TAG, "onSensorChanged(), proximity sensor distance: " + event.values[0]);
    }/*
    if (isProximitySensorNearby(event))
      setScreenInvisible();
    else
      setScreenVisible();*/
  }

  private boolean isProximitySensorNearby(SensorEvent event) {
    float threshold = 4.001f; // <= 4 cm is near
    float distance = event.values[0];
    float max = event.sensor.getMaximumRange();
    if (max <= threshold) {
      // Case binary 0/1 and short sensors
      threshold = max;
    }
    return distance < threshold;
  }
  public void exitApplication() {
    Log.i(LOG_TAG, "monmouthtelecom exit remi"); 
  }
  @Override
  protected void onPause() {
    super.onPause();
    activityPaused = true;
    activityResumed = false;
    if (proximityMonitoring)
      mSensorManager.unregisterListener(this);
  }
  @Override
  protected void onResume() {
    super.onResume();
    activityPaused = false;
    activityResumed = true;
    if (proximityMonitoring)
      mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
  }

  @Override
  public void onDestroy() {
    Log.i(LOG_TAG,"Starting destroy Remi");
    if (proximityMonitoring)
      mSensorManager.unregisterListener(this);
    if (mTeleListener != null)
      telephonyMgr.listen(mTeleListener,PhoneStateListener.LISTEN_NONE);
    super.onDestroy();
    //if (false){
    //  Intent broadcastIntent = new Intent();
    //  broadcastIntent.setAction("servicestarter");
    //  broadcastIntent.setClass(this, ServiceStarter.class);
    //  this.sendBroadcast(broadcastIntent);
    //}
  }
  public static boolean isActivityPaused() {
    return activityPaused;
  }

  public static boolean isActivityResumed() { return activityResumed; }

  @SuppressLint("InvalidWakeLockTag")
  public void startProximityMonitoring() {
    proximityMonitoring = true;
    Log.i(LOG_TAG,"starting proxmity monitoring");
    //WindowManager.LayoutParams params = this.getWindow().getAttributes();
    PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
    proxWakeLock =  pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ProxyWake");
    proxWakeLock.acquire();
    /*this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
    this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
    */
  }
  public void stopProximityMonitoring() {
    proximityMonitoring = false;
    if( null != proxWakeLock ) {
      proxWakeLock.release();
      proxWakeLock = null;
    }
    /*this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
    setScreenVisible();
    mSensorManager.unregisterListener(this);*/
  }

  public boolean isProximityMonitoring() {
    return proximityMonitoring;
  }

  private void setScreenVisible() {
    Log.i(LOG_TAG, "setting screen visible");
    this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    WindowManager.LayoutParams params = this.getWindow().getAttributes();
    View view = this.getWindow().getDecorView().getRootView();
    int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
    view.setSystemUiVisibility(uiOptions);
    params.screenBrightness = -1;
    this.getWindow().setAttributes(params);
    view.setVisibility(View.VISIBLE);
  }

  private void setScreenInvisible() {
    Log.i(LOG_TAG, "setting screen invisible");
    this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    WindowManager.LayoutParams params = MonmouthTelecom.this.getWindow().getAttributes();
    View view = this.getWindow().getDecorView().getRootView();
    int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    view.setSystemUiVisibility(uiOptions);
    params.screenBrightness = 0;
    this.getWindow().setAttributes(params);

    // necessary to run this in 2 steps to maintain proper webview window size
    TimerTask t = new TimerTask() {
      @Override
      public void run() {
        MonmouthTelecom.this.runOnUiThread(new Runnable() {
          public void run() {
            View view = MonmouthTelecom.this.getWindow().getDecorView().getRootView();
            view.setVisibility(View.INVISIBLE);
          }
        });
      }
    };
    Timer tmr = new Timer();
    tmr.schedule(t, 20);
  }
  public String[] getChatRedirectInfo(){
    return new String[]{chatUserName,chatFullName};
  }

  private void registerCallStateListener() {
    if (!callStateListenerRegistered) {
      TelephonyManager telephonyManager = (TelephonyManager) this.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
          telephonyManager.registerTelephonyCallback(ContextCompat.getMainExecutor(this.getApplicationContext()), callStateListener);
          callStateListenerRegistered = true;
        }
      } else {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        callStateListenerRegistered = true;
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.S)
  private static abstract class CallStateListener extends TelephonyCallback implements TelephonyCallback.CallStateListener {
    @Override
    abstract public void onCallStateChanged(int state);
  }

  private boolean callStateListenerRegistered = false;

  private MonmouthTelecom.CallStateListener callStateListener = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ?
          new MonmouthTelecom.CallStateListener() {
            @Override
            public void onCallStateChanged(int state) {
              switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                  Log.i(LOG_TAG, "MonmouthTelecom.java telelistener GSM state idle");
                  if (isProximityMonitoring()) {
                    stopProximityMonitoring();
                    telephonyMgr.listen(mTeleListener,PhoneStateListener.LISTEN_NONE);
                  }
                  break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                  Log.i(LOG_TAG, "MonmouthTelecom.java telelistener GSM state offhook");
                  break;
                case TelephonyManager.CALL_STATE_RINGING:
                  Log.i(LOG_TAG, "MonmouthTelecom.java telelistener GSM state ringing");
                  break;
                default:
                  break;
              }

            }
          }
          : null;

  private PhoneStateListener phoneStateListener = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ?
          new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
              switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                  Log.i(LOG_TAG, "MonmouthTelecom.java telelistener GSM state idle");
                  if (isProximityMonitoring()) {
                    stopProximityMonitoring();
                    telephonyMgr.listen(mTeleListener,PhoneStateListener.LISTEN_NONE);
                  }
                  break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                  Log.i(LOG_TAG, "MonmouthTelecom.java telelistener GSM state offhook");
                  break;
                case TelephonyManager.CALL_STATE_RINGING:
                  Log.i(LOG_TAG, "MonmouthTelecom.java telelistener GSM state ringing");
                  break;
                default:
                  break;
              }

            }
          }
          : null;
}


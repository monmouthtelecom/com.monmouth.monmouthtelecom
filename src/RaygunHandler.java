package com.monmouth.monmouthtelecom;

import com.monmouth.callstate.CallState;
import com.raygun.raygun4android.CrashReportingOnBeforeSend;
import com.raygun.raygun4android.messages.crashreporting.RaygunMessage;
import com.raygun.raygun4android.RaygunClient;
import com.raygun.raygun4android.messages.crashreporting.RaygunMessageDetails;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.monmouth.SIP.LinphoneClient;

import org.json.JSONException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by ytam on 7/8/16.
 */
public class RaygunHandler implements CrashReportingOnBeforeSend {

  private static RaygunHandler instance;
  private static Context mContext;

  private static final String LOG_TAG = "Raygun";

  private RaygunHandler(Context context) {
    mContext = context;
  }

  public static synchronized RaygunHandler getInstance(Context context) {
    if (instance == null)
      instance = new RaygunHandler(context);
    else
      mContext = context;
    return instance;
  }


  private RaygunMessage mergeData(RaygunMessage message, Map data) {
    if (data == null)
      return message;
    RaygunMessageDetails details = message.getDetails();
    Map userCustomData = details.getCustomData();
    if (userCustomData != null) {
      Map merged = new HashMap(userCustomData);
      merged.putAll(data);
      details.setCustomData(merged);
    } else {
      details.setCustomData(data);
    }
    return message;
  }

  private RaygunMessage removeData(RaygunMessage message, Object[] keys) {
    if (keys == null || keys.length == 0)
      return message;
    RaygunMessageDetails details = message.getDetails();
    Map userCustomData = details.getCustomData();
    if (userCustomData != null) {
      Map data = new HashMap(userCustomData);
      for (Object key : keys) {
        data.remove(key);
      }
      details.setCustomData(data);
    }
    return message;
  }

  public static void sendRaygunException(Throwable throwable, Map data, List tags) {
    RaygunClient.send(throwable, tags, data);
  }
  public static void sendRaygunException(Throwable throwable, Map data, String... tags) {
    List<String> list = new ArrayList<String>();
    for (String string : tags) {
      list.add(string);
    }
    sendRaygunException(throwable,data,list);
  }
  public static void sendRaygunException(String msg, Map data, List tags) {
    try {
      throw new Exception(msg);
    } catch (Exception e) {
      sendRaygunException(e,data,tags);
    }
  }
  public static void sendRaygunException(String msg, Map data, String... tags) {
    List<String> list = new ArrayList<String>();
    for (String string : tags) {
      list.add(string);
    }
    sendRaygunException(msg,data,list);
  }


  @Override
  public RaygunMessage onBeforeSend(RaygunMessage message) {
    return null;
  }
}


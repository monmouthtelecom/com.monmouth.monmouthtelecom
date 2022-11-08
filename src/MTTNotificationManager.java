package com.monmouth.monmouthtelecom;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.renderscript.RenderScript;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.monmouth.SIP.SipService;
import com.monmouth.SIP.IncomingCallReceiver;
import com.monmouth.fayePG.FayeService;

/**
 * Created by ytam on 12/21/15.
 */
// todo: refractor all notification code
public class MTTNotificationManager {

  private static MTTNotificationManager instance;

  private static final String LOG_FILE                    = "mttlog.txt";
  private static final String APP_PACKAGE                 = "com.monmouth.monmouthtelecom";
  private static final String NOTIF_ICON                  = "icon_notification";
  private static final String INCOMING_CALL_NOTIF_DISMISS = "ic_close_black_24dp";
  private static final String INCOMING_CALL_NOTIF_ANSWER  = "ic_call_black_24dp";
  private static final String NOTIF_ICON_TYPE             = "drawable";
  private static final String NOTIF_TITLE_DEF             = "Monmouth Telecom";
  private static final String NOTIF_TEXT_DEF              = "Tap to open app.";
  public static final int NOTIF_ID_MAIN                   = 1;
  public static final int NOTIF_ID_INCOMING_CALL          = 101;
  public static final int NOTIF_ID_MISSEDCALLS            = 514;
  public static final int NOTIF_ID_VOICEMAIL              = 1341;
  public static final int DISMISS_INTENT_REQUEST_CODE     = 657;
  public static final int ANSWER_INTENT_REQUEST_CODE      = 453;
  public static final int CONTENT_INTENT_REQUEST_CODE     = 30;
  private static final String APP_ACTIVITY                = "com.monmouth.monmouthtelecom.MonmouthTelecom";
  private static final String LOG_TAG                     = "MTTNotificationManager";
  private static final String SERVICE_CHANNEL_ID          = "Service";
  private static final String VM_CHANNEL_ID               = "Voicemail";
  private SipService mSipService;
  private FayeService mFayeService;
  private boolean sipAlive;
  private boolean fayeAlive;
  private static Context mContext;

  private MTTNotificationManager(Context context) {
    mContext = context;
    currentTitle = NOTIF_TITLE_DEF;
    currentText = NOTIF_TEXT_DEF;
  }
  public void createServiceNotificationChannel() {
    Log.i("REMI", "Creating Service notification channels");
    CharSequence name = "Services";
    int importance = NotificationManager.IMPORTANCE_LOW;
    NotificationChannel channel = new NotificationChannel(SERVICE_CHANNEL_ID, name, importance);
    channel.setShowBadge(false);
    channel.setDescription("Display what services are running");
    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.createNotificationChannel(channel);

  }
  public void createSipNotificationChannel() {
    Log.i("REMI", "Creating Sip notification channels");
    CharSequence name = "Voice";
    int importance = NotificationManager.IMPORTANCE_HIGH;
    NotificationChannel channel = new NotificationChannel(SipService.CHANNEL_ID,name,importance);
    channel.setDescription("Notification related to voice services.");
    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.createNotificationChannel(channel);
  }
  public void createChatNotificationChannel() {
    Log.i("REMI", "Creating Chat notification channels");
    CharSequence name = "Chat";
    int importance = NotificationManager.IMPORTANCE_DEFAULT;
    NotificationChannel channel = new NotificationChannel(FayeService.CHANNEL_ID,name,importance);
    channel.setDescription("Notification related to chat services.");
    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.createNotificationChannel(channel);
  }
  public void createVMNotificationChannel() {
    Log.i("REMI", "Creating Voicemail notification channels");
    CharSequence name = "Voicemail";
    int importance = NotificationManager.IMPORTANCE_DEFAULT;
    NotificationChannel channel = new NotificationChannel(VM_CHANNEL_ID,name,importance);
    channel.setDescription("Notification related to Voicemail services.");
    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.createNotificationChannel(channel);
  }
  public void createVoiceNotificationChannel() {
    Log.i("REMI", "Creating Voicemail notification channels");
    CharSequence name = "Missed Calls";
    int importance = NotificationManager.IMPORTANCE_DEFAULT;
    NotificationChannel channel = new NotificationChannel(SipService.VOICE_CHANNEL_ID,name,importance);
    channel.setDescription("Notification related to missed calls.");
    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.createNotificationChannel(channel);
  }
  public static synchronized MTTNotificationManager getInstance(Context context) {
    if (instance == null)
      instance = new MTTNotificationManager(context);
    else
      mContext = context;
    return instance;
  }

  public void setSipService(SipService ss) {
    mSipService = ss;
  }

  public void setFayeService(FayeService f) {
    mFayeService = f;
  }

  public void setSipAlive(boolean alive) {
    sipAlive = alive;
    if (!sipAlive)
      onSipServiceDestroy();
  }

  public void setFayeAlive(boolean alive) {
    fayeAlive = alive;
    if (!fayeAlive)
      onFayeServiceDestroy();
  }

  private Class currentClazz;
  private String currentTitle;
  private String currentText;
  private String currentTicker;

  public Class getCurrentClazz() {
    return currentClazz;
  }

  public void setCurrentClazz(Class currentClazz) {
    this.currentClazz = currentClazz;
  }

  public String getCurrentTitle() {
    return currentTitle;
  }

  public void setCurrentTitle(String currentTitle) {
    this.currentTitle = currentTitle;
  }

  public String getCurrentText() {
    return currentText;
  }

  public void setCurrentText(String currentText) {
    this.currentText = currentText;
  }

  public String getCurrentTicker() {
    return currentTicker;
  }

  public void setCurrentTicker(String currentTicker) {
    this.currentTicker = currentTicker;
  }

  public Notification getNotification(Class clazz, String title, String text, String ticker) {
    currentClazz = clazz;
    currentTitle = title;
    currentText = text;
    currentTicker = ticker;
    Notification notif = null;
    Log.i("REMI", "inside getNotification " + clazz);
    int icon = mContext.getResources().getIdentifier(NOTIF_ICON, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (icon == 0)
      Log.i(LOG_TAG, "notification icon not found");

    Intent notifIntent = new Intent(mContext, clazz);
    notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notifIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

    if (title == null || title.length() == 0)
      title = NOTIF_TITLE_DEF;
    if (text == null || text.length() == 0)
      text = NOTIF_TEXT_DEF;

    NotificationCompat.Builder mBuilder =
      new NotificationCompat.Builder(mContext, SERVICE_CHANNEL_ID)
        .setSmallIcon(icon)
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .setContentTitle(title)
        .setContentText(text);
    if (ticker != null && ticker.length() > 0)
      mBuilder.setTicker(ticker);

    notif = mBuilder.build();
    return notif;
  }

  public void setNotification(Class clazz, String title, String text, String ticker) {
    Log.i(LOG_TAG,"Arban: setNotification");
    Log.i(LOG_TAG,"Class " + clazz + " Titile " + title + " Text " + text + " Ticker " + ticker);
    if (clazz == null) {
      Log.i(LOG_TAG, "MTTNotificationManager, setNotification, class is null");
      return;
    }
    if (sipAlive || fayeAlive) {
      setCurrentClazz(clazz);
      setCurrentText(title);
      setCurrentTitle(text);
      setCurrentTicker(ticker);
      NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
      mgr.notify(NOTIF_ID_MAIN, getNotification(clazz, title, text, ticker));
    } else {
      Log.i(LOG_TAG, "MTTNotificationManager setNotification(), received notification update but no service is running w/title"
        + title + " text: " + text + " ticker: " + ticker);
    }
  }

  public Notification getIncomingCallNotification(PendingIntent answerIntent, PendingIntent dismissIntent, PendingIntent contentIntent, String title, String text, String ticker) {
    Notification notif = null;
    int icon = mContext.getResources().getIdentifier(NOTIF_ICON, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (icon == 0)
      Log.e(LOG_TAG, "mtt notification icon not found");
    int dismissIcon = mContext.getResources().getIdentifier(INCOMING_CALL_NOTIF_DISMISS, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (dismissIcon == 0)
      Log.e(LOG_TAG, "dismiss icon not found");
    int answerIcon = mContext.getResources().getIdentifier(INCOMING_CALL_NOTIF_ANSWER, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (answerIcon == 0)
      Log.e(LOG_TAG, "answer icon not found");

    Log.i(LOG_TAG, "remi buiding notification");
    if (title == null || title.length() == 0)
      title = NOTIF_TITLE_DEF;
    if (text == null || text.length() == 0)
      text = "Incoming Call";

    long[] pattern = {500, 2000, 500};
    NotificationCompat.Builder mBuilder =
      new NotificationCompat.Builder(mContext,SipService.CHANNEL_ID)
        .setFullScreenIntent(contentIntent, true)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setSmallIcon(icon)
        .setOngoing(false)
        .setContentIntent(contentIntent)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true)
        .setVibrate(pattern)
        .addAction(dismissIcon, "DISMISS", dismissIntent)
        .addAction(answerIcon, "ANSWER", answerIntent);
    if (ticker != null && ticker.length() > 0)
      mBuilder.setTicker(ticker);

    notif = mBuilder.build();
    notif.flags |= Notification.FLAG_AUTO_CANCEL;
    return notif;
  }
  public Notification incomingCallNotificationBuilder(Intent notifIntent, String callerId, String callid){
    Intent dismissIntent = new Intent(mContext, IncomingCallReceiver.class);
    dismissIntent.putExtra("notifid", NOTIF_ID_INCOMING_CALL);
    dismissIntent.putExtra("action", "dismiss");
    dismissIntent.putExtra("callid", callid);
    dismissIntent.putExtra("launchedFromIncomingCallNotification", true);
    
    Intent answerIntent = new Intent(mContext, IncomingCallReceiver.class);
    answerIntent.putExtra("notifid", NOTIF_ID_INCOMING_CALL);
    answerIntent.putExtra("action", "answer");
    answerIntent.putExtra("callid", callid);
    answerIntent.putExtra("launchedFromIncomingCallNotification", true);
    
    notifIntent.putExtra("launchedFromIncomingCallNotification", true);
    PendingIntent answerPendingIntent = PendingIntent.getBroadcast(mContext, ANSWER_INTENT_REQUEST_CODE, answerIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(mContext, DISMISS_INTENT_REQUEST_CODE, dismissIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent contentPendingIntent = PendingIntent.getActivity(mContext, CONTENT_INTENT_REQUEST_CODE, notifIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

    return this.getIncomingCallNotification(answerPendingIntent, dismissPendingIntent, contentPendingIntent, callerId, "Incoming Call", null);

  }
  public Notification getOngoingCallNotification(PendingIntent contentIntent, String title, String text, String ticker) {
    Notification notif = null;
    int icon = mContext.getResources().getIdentifier(NOTIF_ICON, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (icon == 0)
      Log.e(LOG_TAG, "mtt notification icon not found");

    if (title == null || title.length() == 0)
      title = NOTIF_TITLE_DEF;
    if (text == null || text.length() == 0)
      text = "Ongoing Call";

    NotificationCompat.Builder mBuilder =
      new NotificationCompat.Builder(mContext,SipService.CHANNEL_ID)
        .setSmallIcon(icon)
        .setOngoing(false)
        .setContentIntent(contentIntent)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setAutoCancel(true);
    if (ticker != null && ticker.length() > 0)
      mBuilder.setTicker(ticker);

    notif = mBuilder.build();
    notif.flags |= Notification.FLAG_AUTO_CANCEL;
    return notif;
  }

  private void onSipServiceDestroy() {
    Log.i(LOG_TAG, "MTTNotificationManager onSipServiceDestroy()");
    if (fayeAlive && mFayeService != null)
      mFayeService.setNotificationTexts(currentTitle, currentText, currentTicker);
    else
      removeNotification(NOTIF_ID_MAIN);
  }

  private void onFayeServiceDestroy() {
    Log.i(LOG_TAG, "MTTNotificationManager onFayeServiceDestroy()");
    if (sipAlive && mSipService != null)
      mSipService.setNotificationTexts(currentTitle, currentText, currentTicker);
    else
      removeNotification(NOTIF_ID_MAIN);
  }

  public void displayNotification(int id, Notification notif) {
    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.notify(id, notif);
  }

  public void removeNotification(int id) {
    Log.i(LOG_TAG, "MTTNotificationManager removeNotification() w/id: " + id);
    NotificationManager mNotifyMgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mNotifyMgr.cancel(id);
  }

  // todo: instead of having 3 seperate android notifications (ongoing status info, missed calls, chat)
  // put all into 1 notification with "big view style"
  public void setMissedCallsNotification(int id, String title, String text) {
    int icon = mContext.getResources().getIdentifier(NOTIF_ICON, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (icon == 0)
      Log.i(LOG_TAG, "notification icon not found");

    Intent notifIntent;
    try {
      notifIntent = new Intent(mContext, Class.forName(APP_ACTIVITY));
    } catch (ClassNotFoundException e) {
      Log.e(LOG_TAG, "can't find class for " + APP_ACTIVITY);
      return;
    }
    notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    Bundle bundle = new Bundle();
    bundle.putBoolean("missedCall", true);
    notifIntent.putExtras(bundle);
    PendingIntent pendingIntent = PendingIntent.getActivity(mContext, id, notifIntent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder mBuilder =
      new NotificationCompat.Builder(mContext,SipService.VOICE_CHANNEL_ID)
        .setSmallIcon(icon)
        .setOngoing(false)
        .setContentIntent(pendingIntent)
        .setContentTitle(title)
        .setContentText(text);

    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.notify(id, mBuilder.build());
  }
  public void setVoicemailNotification(int id, String title, String text) {
    int icon = mContext.getResources().getIdentifier(NOTIF_ICON, NOTIF_ICON_TYPE, APP_PACKAGE);
    if (icon == 0)
      Log.i(LOG_TAG, "notification icon not found");

    Intent notifIntent;
    try {
      notifIntent = new Intent(mContext, Class.forName(APP_ACTIVITY));
    } catch (ClassNotFoundException e) {
      Log.e(LOG_TAG, "can't find class for " + APP_ACTIVITY);
      return;
    }
    notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(mContext, id, notifIntent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

    NotificationCompat.Builder mBuilder =
      new NotificationCompat.Builder(mContext,VM_CHANNEL_ID)
        .setSmallIcon(icon)
        .setOngoing(false)
        .setContentIntent(pendingIntent)
        .setContentTitle(title)
        .setContentText(text);

    NotificationManager mgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mgr.notify(id, mBuilder.build());
  }
}

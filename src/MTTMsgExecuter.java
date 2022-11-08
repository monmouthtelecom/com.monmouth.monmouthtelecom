package com.monmouth.monmouthtelecom;

import org.json.JSONObject;
import com.monmouth.monmouthtelecom.MobileCarrier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.net.Uri;
import java.util.ArrayList;
import android.content.ContentProviderOperation;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.os.RemoteException;
import android.content.OperationApplicationException;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.content.Context;
import org.json.JSONException;
import com.monmouth.fayePG.FayePG;
import com.monmouth.fayePG.FayeService;
import android.app.Activity;
import android.app.Service;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.widget.Button;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.FileOutputStream;

public class MTTMsgExecuter {

    private static final String LOG_TAG                     = "Faye";
    private static final String CONTACT_NOTE                = "Contact created by Monmouth Telecom App";

    private MobileCarrier carrier;
    public static Context context;
    private Class<?> className;
    private Handler mHandler;
    private ArrayList<ContentProviderOperation> ops;
    private FayePG fayePG;
    private static JSONObject currentCall;
    private static MTTMsgExecuter instance;
    private static int READ_PHONE_STATE_CODE = 100;
    private Button readState;

    // TODO remove context & class (get from fayeservice), remove handler?
    public MTTMsgExecuter(Service service, Handler mHandler, FayePG fayePG, MobileCarrier carrier, Class<?> activityClass) {
        this.carrier = carrier;
        this.context = service;
        this.className = activityClass;
        this.mHandler = mHandler;
        this.fayePG = fayePG;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void execute(JSONObject fayeMsg, String channel) {
        try {
            String command = fayeMsg.getString("command");
            Log.i(LOG_TAG, "fayemsg command: " + command);
            if (command.equals("AddContact")) {
                Log.d(LOG_TAG, "can't run javascript...");
                JSONObject contact = fayeMsg.getJSONObject("contact");
                String num = contact.getString("phoneNumber");
                editContact(contact);
                Log.i(LOG_TAG, "carrier info: mcc:" + carrier.getMcc() + " mnc: " + carrier.getMnc());
                if (carrier.getMcc() == 310 || carrier.getMcc() == 311) {
                    // t-mobile
                    if (carrier.getMnc() == 26 || carrier.getMnc() == 60 || carrier.getMnc() == 160 || carrier.getMnc() == 260 || carrier.getMnc() == 490 || carrier.getMnc() == 480 || carrier.getMnc() == 80 ) {
                        contact.put("phoneNumber", carrier.convertPhoneNumber(contact.getString("phoneNumber")));
                        Log.i(LOG_TAG, "fayemsg contact: " + contact.toString());
                        editContact(contact);
                    }
                }
            } else if (command.equals("IncomingCall")) {
                // todo: refractor so method name is not hardcoded
                FayeService fs;
                if (context instanceof FayeService)
                    fs = (FayeService) context;
                else {
                    Log.e(LOG_TAG, "MTTMsgExecuter, wtf not a fayeservice?!?");
                    return;
                }
                String num = fayeMsg.getString("from");
                currentCall = new JSONObject();
                try {
                    currentCall.put("to", fs.getUser());
                    // max length of ext is 6 digits + prefix length
                    if (num.startsWith("00") && num.length() <= 8) {
                        num = num.substring(2);
                    }
                    currentCall.put("from", num);
                    currentCall.put("type", "phone");
                    // todo: refractor so these values aren't hardcoded?
                    currentCall.put("dir", 1);
                    currentCall.put("status", 2);
                    long currentTime = System.currentTimeMillis();
                    currentCall.put("start_date_time", currentTime);
                    currentCall.put("call_id", currentTime);
                    currentCall.put("duration", 0);
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "MTTMsgExecuter, invalid json format");
                }
                // todo: refractor so js function name is hardcoded
                if (fayePG != null && fayePG.isActivityAlive())
                    fayePG.webView.sendJavascript("setCurrentInboundCall(" + currentCall.toString() + ");");
                else
                    fs.getJsQueue().offer("setCurrentInboundCall(" + currentCall.toString() + ");");
            } else if (command.equals("OpenApp")) {
                TelephonyManager telephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                int permissionCheck = ContextCompat.checkSelfPermission(MTTMsgExecuter.context, Manifest.permission.READ_PHONE_STATE);

                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    Log.i(LOG_TAG, "GRANTED PERMISSION");
                } else {
                    Log.i(LOG_TAG, "FAILED PERMISSion");
                }
                if (Build.VERSION.SDK_INT == 31){
                    if (telephonyMgr.getCallStateForSubscription() != TelephonyManager.CALL_STATE_OFFHOOK) {
                        Log.i(LOG_TAG, "received OpenApp command, but phone state isn't active so ignoring...");
                        return;
                    }
                }
                else{
                    if (telephonyMgr.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
                        Log.i(LOG_TAG, "received OpenApp command, but phone state isn't active so ignoring...");
                        return;
                    }
                }

                Log.i(LOG_TAG, "opening app...");
                if (fayePG != null) {
                    final Activity activity = fayePG.cordova.getActivity();
                    if (activity instanceof MonmouthTelecom) {
                        if (((MonmouthTelecom) activity).isActivityPaused()) {
                            Intent notifIntent = new Intent(context, className);
                            notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            notifIntent.putExtra("launchAppForIncMTTCall", true);
                            context.startActivity(notifIntent);
                        }
                    }
                    if (fayePG.getCommand(channel) != null) {
                        if (fayePG.isActivityAlive()) {
                            fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                        } else if (context instanceof FayeService) {
                            FayeService fs = (FayeService) context;
                            fs.getJsQueue().offer(fs.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                        }
                    }
                } else {
                    Intent notifIntent = new Intent(context, className);
                    notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    notifIntent.putExtra("launchAppForIncMTTCall", true);
                    context.startActivity(notifIntent);
                    if (context instanceof FayeService) {
                        FayeService fs = (FayeService) context;
                        if (fayePG.getCommand(channel) != null)
                            fs.getJsQueue().offer(fs.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                }
                // call now being handled by js
                if (currentCall != null) {
                    currentCall = null;
                }
            } else if (command.equals("Response")) {
                Log.i(LOG_TAG, "execute response... ");
                if (fayePG != null && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null)
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) +"("+fayeMsg.toString()+");");
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                }
            } else if (command.equals("UserHangup")) {
                String user = fayeMsg.getString("user");
                FayeService fs = null;
                boolean callEnded = false;
                if (currentCall != null && context instanceof FayeService) {
                    fs = (FayeService) context;
                    Log.i(LOG_TAG, "user: " + user + " fsUser: " + fs.getUser());
                    if (user.equals(fs.getUser())) {
                        callEnded = true;
                    }
                }
                if (fayePG != null && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null) {
                        Log.d(LOG_TAG, "sending UserHangup cmd to js...");
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                    // call was never answered and cant use js to save to logs
                    if (callEnded)
                        saveCallToLog(currentCall);
                }
                if (callEnded) {
                    currentCall = null;
                    fs.getJsQueue().clear();
                }
            } else if (command.equals("CallStatus")) {
                if (fayePG != null && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null) {
                        Log.d(LOG_TAG, "sending CallStatus cmd to js...");
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                }
            }else if(command.equals("receiveStatusUpdate")){
                if(fayePG != null)
                    Log.d(LOG_TAG,"Faye activity is alive " +fayePG.isActivityAlive());
                if ((fayePG != null) && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null) {
                        Log.d(LOG_TAG, "sending receiveStatusUpdate cmd to js...");
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                    String user = fayeMsg.getString("sender");
                    String status = fayeMsg.getString("status_name");
                    if (context instanceof FayeService) {
                        FayeService fs = (FayeService) context;
                        String fsUser = fs.getUser();
                        MTTNotificationManager mgr = MTTNotificationManager.getInstance(context);
                        if (user.equals(fsUser)) {
                            String currentText = mgr.getCurrentText();
                            if (currentText != null) {
                                Pattern p = Pattern.compile("^IM:\\s(.*?);");
                                Matcher m = p.matcher(currentText);
                                String newText = null;
                                while(m.find()) {
                                    newText = m.replaceFirst("IM: " + status + ";");
                                    break;
                                }
                                if (newText != null)
                                    fs.setNotificationTexts(mgr.getCurrentTitle(), newText, null);
                            }
                        }
                    }
                }
            }else if(command.equals("receiveMessage")){
                if(fayePG != null)
                    Log.d(LOG_TAG,"Faye activity is alive" +fayePG.isActivityAlive());
                if ((fayePG != null) && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null) {
                        Log.d(LOG_TAG, "sending receiveMessage cmd to js...");
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                    if(fayeMsg.has("category")){
                        String category = fayeMsg.getString("category");
                        if(category.equals("cancelChatNotification")){
                            String username = fayeMsg.getString("readChat");
                            Log.d(LOG_TAG,"[ZACH] Canceling chat notifications for "+username);
                            Log.d(LOG_TAG,"[ZACH] FayePG is null? "+(fayePG == null));
                            fayePG.cancelChatNotification(username);
                        }
                        Log.d(LOG_TAG, "[ZACH] category is not null, returning out of the function!!!!!!");
                        return;
                    }
                    if(fayePG != null)
                        fayePG.displayNotification(fayeMsg.getString("name"), fayeMsg.getString("text"),fayeMsg.getString("sender"),"chat");
                    else{
                        int notifId = 1000;
                        try{
                            String user = fayeMsg.getString("name");
                            String msg = fayeMsg.getString("text");
                            String sender = fayeMsg.getString("sender");
                            notifId += Integer.parseInt(sender.split("-")[1]);
                            ((FayeService)this.context).displayNotification(user,msg,notifId,sender,"chat");
                        }catch(Exception e){
                            return;
                        }
                    }
                }
            }else if(command.equals("receiveSms")){
                if(fayePG != null)
                    Log.d(LOG_TAG,"Faye activity is alive" +fayePG.isActivityAlive());
                if ((fayePG != null) && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null) {
                        Log.d(LOG_TAG, "sending receiveSms cmd to js..."+fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                    String from = fayeMsg.getString("from");
                    String body = fayeMsg.getString("body");
                    String displayName = getContactDisplayNameByNumber(from);
                    if(displayName == "" || displayName == null)
                        displayName = from;

                    if(fayePG != null){
                        fayePG.displayNotification(displayName,body,from,"sms");
                    }else{
                        int notifId = 1000 + Integer.parseInt(from.substring(3,from.length()));
                        ((FayeService)this.context).displayNotification(displayName,body,notifId,from,"chat");
                    }
                }
            } else if(command.equals("Mailbox Message Count") || command.equals("MessageWaiting")){
                if(fayePG != null)
                    Log.d(LOG_TAG,"Faye activity is alive" +fayePG.isActivityAlive());
                if ((fayePG != null) && fayePG.isActivityAlive()) {
                    // can use js here
                    if (fayePG.getCommand(channel) != null) {
                        Log.d(LOG_TAG, "sending receiveMessage cmd to js...");
                        fayePG.webView.sendJavascript(fayePG.getCommand(channel) + "(" + fayeMsg.toString() + ");");
                    }
                } else {
                    Log.d(LOG_TAG, "can't run javascript...");
                }
                try {
                    JSONObject data = fayeMsg.getJSONObject("data");
                    int newVm;
                    int urgVm = 0;
                    int totalVm = 0;
                    int currentVmCount; 
                    if(command.equals("Mailbox Message Count")) {
                        newVm = data.getInt("NewMessages");
                        urgVm = data.getInt("UrgMessages");
                        totalVm = newVm + urgVm;
                    } else {
                        newVm = data.getInt("New");
                        totalVm = newVm;
                    }
                    MTTNotificationManager mgr = MTTNotificationManager.getInstance(context);
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    currentVmCount = prefs.getInt("newVoicemailsCount",-1);
                    Log.d(LOG_TAG, "newVoicemailsCount: " + currentVmCount + " totalVm: " + totalVm);
                    if (totalVm > 0 && currentVmCount != totalVm) {
                        String msg = "";
                        if (totalVm > 1)
                            msg = totalVm + " new voicemails.";
                        else {
                            msg = totalVm + " new voicemail.";
                        }
                        if (totalVm > 0 && MonmouthTelecom.isActivityPaused()){
                            Log.d(LOG_TAG, "Sending Notification: " + msg);
                            mgr.setVoicemailNotification(MTTNotificationManager.NOTIF_ID_VOICEMAIL, "Monmouth Telecom", msg);
                        }
                        if (totalVm > 0 && MonmouthTelecom.isActivityResumed())
                            mgr.removeNotification(MTTNotificationManager.NOTIF_ID_VOICEMAIL);
                    } else if (totalVm == 0)
                        mgr.removeNotification(MTTNotificationManager.NOTIF_ID_VOICEMAIL);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("newVoicemailsCount", newVm);
                    editor.apply();
                } catch (JSONException ex) {
                    Log.e(LOG_TAG, "invalid json");
                    Log.e(LOG_TAG, ex.getMessage());
                }
            }
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "invalid json");
            Log.e(LOG_TAG, ex.getMessage());
        }
    }


    private void editContact(JSONObject contact) throws JSONException {
        Log.i(LOG_TAG, "in editContact() w/contact: " + contact.toString());
        boolean contactFound = false;
        ops = new ArrayList<ContentProviderOperation>();
        // get list of all data rows and display names containing phone #
        String phoneNum = contact.getString("phoneNumber");
        HashMap<Integer, String> existingContacts = getDataRowIds(phoneNum);
        String fullName = (contact.getString("firstName") + " " + contact.getString("lastName")).trim();
        Log.i(LOG_TAG, "contact name: " + fullName);

        // check if displaynames == caller id
        List<Integer> data = new ArrayList<Integer>();
        for (HashMap.Entry<Integer, String> entry : existingContacts.entrySet()) {
            Log.i(LOG_TAG, "Key = " + entry.getKey() + ", Value = " + entry.getValue());
            int id = entry.getKey();
            String name = entry.getValue();
            if (!name.equals(fullName)) {
                Log.i(LOG_TAG, "row id to delete: " + id + " name: " + name);
                data.add(id);
            } else
                contactFound = true;
        }

        if (!contactFound) {
            // search for contact name
            int contactID = getContactId(fullName);
            Log.i(LOG_TAG, "contact id: " + contactID);
            if (contactID != 0) {
                int rawContactID = getRawContactId(contactID);
                // add to existing contact
                addInsertPhoneNumberOp(rawContactID, phoneNum, Phone.TYPE_WORK);
            } else {
                // doesn't exist, insert new contact
                addInsertContactOp(fullName, phoneNum, Phone.TYPE_WORK);
            }
        }

        // if names differ, remove data rows, do deletes after inserts
        addRowsToDelete(data);
        executeOps();

    }

    private HashMap<Integer, String> getDataRowIds(String phoneNumber) {
        HashMap<Integer, String> result = new HashMap<Integer, String>();
        Cursor cursor = context.getContentResolver().query(Data.CONTENT_URI, null,
                Phone.NUMBER + "=?",
                new String[] {phoneNumber}, null);
        try {
            while(cursor.moveToNext()){
                int dataID = cursor.getInt(cursor.getColumnIndexOrThrow(Data._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.DISPLAY_NAME));
                Log.i(LOG_TAG, "dataid: " + dataID); // id of data row
                Log.i(LOG_TAG, "display name: " + name);
                result.put(dataID,name);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    private void addRowsToDelete(List<Integer> rows) {
        if (rows == null)
            return;
        for (Integer rowID : rows) {
            ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection(Data._ID + "=? and " + Data.MIMETYPE + "=?", new String[]{String.valueOf(rowID), Phone.CONTENT_ITEM_TYPE})
                    .build());
        }
    }

    private void addInsertPhoneNumberOp(int rawContactId, String phoneNumber, int phoneNumberType) {
        if (rawContactId == 0) {
            // new contact, add id with back reference
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phoneNumber)
                    .withValue(Phone.TYPE, phoneNumberType)
                    .build());
        } else {
            // adding to existing contact
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phoneNumber)
                    .withValue(Phone.TYPE, phoneNumberType)
                    .build());
        }
    }

    private void addInsertAccountInfoOp(String accountType, String accountName) {
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .build());
    }

    private void addInsertRawContactOp(String displayName) {
        ops.add(ContentProviderOperation.newInsert(
                ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, displayName)
                .build());
    }

    private void addInsertNoteOp(int rawContactId, String note) {
        if (rawContactId == 0) {
            // new contact, add id with back reference
            ops.add(ContentProviderOperation.newInsert(
                    ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                    .withValue(Note.NOTE, note)
                    .build());
        } else {
            // adding to existing contact
            ops.add(ContentProviderOperation.newInsert(
                    ContactsContract.Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                    .withValue(Note.NOTE, note)
                    .build());
        }
    }

    private void addInsertContactOp(String displayName, String phoneNumber, int phoneNumberType) {
        // insert caller id info
        addInsertAccountInfoOp(null, null);
        addInsertRawContactOp(displayName);
        addInsertPhoneNumberOp(0, phoneNumber, phoneNumberType);
        addInsertNoteOp(0, CONTACT_NOTE);
    }

    private void executeOps() {
        try {
            context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, e.getMessage());
        } catch (OperationApplicationException ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    private int getContactId(String displayName) {
        int id = 0;
        Cursor contactLookupCursor = context.getContentResolver().query(Contacts.CONTENT_URI,
                new String[] {Contacts._ID}, Data.DISPLAY_NAME + "=?",
                new String[] {displayName}, null);

        try {
            while (contactLookupCursor.moveToNext()) {
                id = Integer.parseInt(contactLookupCursor.getString(contactLookupCursor.getColumnIndexOrThrow(Contacts._ID)));
            }
        } finally {
            contactLookupCursor.close();
        }

        return id;
    }

    @SuppressLint("Range")
    private String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String name = "?";

        Cursor contactLookup = context.getContentResolver().query(uri, new String[] {BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME }, null, null, null);

        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                //String contactId = contactLookup.getString(contactLookup.getColumnIndex(BaseColumns._ID));
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return name;
    }

    @SuppressLint("Range")
    private int getRawContactId(int contactId) {
        int rawContactId = 0;
        Cursor c = context.getContentResolver().query(RawContacts.CONTENT_URI,
                new String[]{RawContacts._ID},
                RawContacts.CONTACT_ID+"=?",
                new String[]{String.valueOf(contactId)}, null);
        try {
            if (c.moveToFirst()) {
                rawContactId = c.getInt(c.getColumnIndex(RawContacts._ID));
            }
        } finally {
            c.close();
        }
        Log.i(LOG_TAG,"Contact Id: " + contactId + " Raw Contact Id: " + rawContactId);
        return rawContactId;
    }
    // todo: pass in dir path and filename as a variable?
    private void saveCallToLog(JSONObject call) {
        if (context == null)
            return;
        Log.i(LOG_TAG, "MTTMsgExecuter, saveCallToLog w/call: " + call.toString());
        //String dir = context.getFilesDir().getAbsolutePath();
        String data = jsonToCallLogString(call);
        String filename = "call_log";
        FileOutputStream outputStream;

        try {
            outputStream = context.openFileOutput(filename, Context.MODE_APPEND);
            outputStream.write(data.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(LOG_TAG,"MTTMsgExecuter saveCallToLog, error writing to file: " + e.getMessage());
        }
    }
    private String jsonToCallLogString(JSONObject json) {
        String str = null;
        try {
            str = "\n[call_log_" + json.getString("call_id") + "]\n" + json.toString();
            str = str.replaceAll(":","=");
            str = str.replaceAll(",","\n");
            str = str.replaceAll("[{}\"]","");
        } catch (JSONException e) {
            Log.e(LOG_TAG, "MTTMsgExecuter, jsonToCallLogString invalid json format " + e.getMessage());
            return null;
        }
        return str;
    }
}

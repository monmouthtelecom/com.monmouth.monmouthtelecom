package com.monmouth.monmouthtelecom;

import android.os.Bundle;
import org.apache.cordova.*;
import android.util.Log;
import android.app.Application;
import android.content.Context;
import android.app.NotificationManager;
import com.raygun.raygun4android.RaygunClient;
import com.raygun.raygun4android.messages.shared.RaygunUserInfo;

/**
 * Created by ytam on 4/20/15.
 */
public class MainApplication extends Application{

    private static final String LOG_TAG                     = "Faye";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "mainapplication onCreate");
        // Setup handler for uncaught exceptions.
        Thread.setDefaultUncaughtExceptionHandler (new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException (Thread thread, Throwable e)
            {
                handleUncaughtException (thread, e);
            }
        });
      RaygunClient.init(this);
      RaygunClient.enableCrashReporting();
      RaygunClient.setOnBeforeSend(RaygunHandler.getInstance(this));

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.i(LOG_TAG, "mainapplication onLowMemory()");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.i(LOG_TAG, "mainapplication onTerminate()");
    }

    public void handleUncaughtException (Thread thread, Throwable e)
    {
        Log.i(LOG_TAG, "main application handleUncaughtException()");
        Log.e(LOG_TAG, e.toString());
        Log.e(LOG_TAG, "stack:" , e);
        System.exit(1); // kill off the crashed app
    }

}

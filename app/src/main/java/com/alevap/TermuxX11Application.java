package com.alevap;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TermuxX11Application extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sw.toString();

            Log.e("TermuxX11", "Crash detected: " + stackTrace);

            Intent intent = new Intent(this, CrashActivity.class);
            intent.putExtra("error", stackTrace);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
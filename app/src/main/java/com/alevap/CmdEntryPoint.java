package com.alevap;

import static android.system.Os.getuid;
import static android.system.Os.getenv;

import android.annotation.SuppressLint;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Keep;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;

@Keep @SuppressLint({"StaticFieldLeak", "UnsafeDynamicallyLoadedCode"})
public class CmdEntryPoint extends ICmdEntryInterface.Stub {
    public static final String ACTION_START = "com.alevap.CmdEntryPoint.ACTION_START";
    static final Handler handler;
    public static Context ctx;
    private final Intent intent = createIntent();

    public static void main(String[] args) {
        handler.post(() -> new CmdEntryPoint(args));
        Looper.loop();
    }

    CmdEntryPoint(String[] args) {
        if (!start(args))
            System.exit(1);

        spawnListeningThread();
        sendBroadcastDelayed();
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private Intent createIntent() {
        String targetPackage = getenv("TERMUX_X11_OVERRIDE_PACKAGE");
        if (targetPackage == null)
            targetPackage = "com.alevap";
        Bundle bundle = new Bundle();
        bundle.putBinder(null, this);
        Intent intent = new Intent(ACTION_START);
        intent.putExtra(null, bundle);
        intent.setPackage(targetPackage);
        if (getuid() == 0 || getuid() == 2000)
            intent.setFlags(0x00400000 /* FLAG_RECEIVER_FROM_SHELL */);
        return intent;
    }

    private void sendBroadcast() {
        sendBroadcast(intent);
    }

    static void sendBroadcast(Intent intent) {
        try {
            ctx.sendBroadcast(intent);
        } catch (Exception e) {
            // Silently fallback if needed
        }
    }

    private void sendBroadcastDelayed() {
        if (!connected())
            sendBroadcast(intent);
        handler.postDelayed(this::sendBroadcastDelayed, 1000);
    }

    void spawnListeningThread() {
        new Thread(this::listenForConnections).start();
    }

    @SuppressLint("DiscouragedPrivateApi")
    public static Context createContext() {
        try {
            java.lang.reflect.Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            return ((android.app.ActivityThread) Class.forName("sun.misc.Unsafe").getMethod("allocateInstance", Class.class).invoke(unsafe, android.app.ActivityThread.class)).getSystemContext();
        } catch (Exception e) {
            return null;
        }
    }

    public static native boolean start(String[] args);
    public native ParcelFileDescriptor getXConnection();
    public native ParcelFileDescriptor getLogcatOutput();
    private static native boolean connected();
    private native void listenForConnections();

    static {
        try { if (Looper.getMainLooper() == null) Looper.prepareMainLooper(); } catch (Exception ignored) {}
        handler = new Handler();
        ctx = createContext();

        String apkPath = System.getProperty("java.class.path");
        if (apkPath != null) {
            String libDir = apkPath.substring(0, apkPath.lastIndexOf("/")) + "/lib/" + Build.SUPPORTED_ABIS[0] + "/libXlorie.so";
            java.io.File f = new java.io.File(libDir);
            if (!f.exists() && Build.SUPPORTED_ABIS[0].equals("arm64-v8a")) {
                libDir = apkPath.substring(0, apkPath.lastIndexOf("/")) + "/lib/arm64/libXlorie.so";
                f = new java.io.File(libDir);
            }
            if (f.exists()) {
                try { System.load(libDir); } catch (Throwable ignored) {}
            }
        }
    }
}
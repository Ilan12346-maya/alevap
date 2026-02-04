package com.alevap;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.KeyEvent.*;
import static android.view.WindowManager.LayoutParams.*;
import static com.alevap.CmdEntryPoint.ACTION_START;
import static com.alevap.LoriePreferences.ACTION_PREFERENCES_CHANGED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.math.MathUtils;
import androidx.viewpager.widget.ViewPager;

import com.alevap.input.InputEventSender;
import com.alevap.input.InputStub;
import com.alevap.input.TouchInputHandler;
import com.alevap.utils.FullscreenWorkaround;
import com.alevap.utils.KeyInterceptor;
import com.alevap.utils.SamsungDexUtils;
import com.alevap.utils.TermuxX11ExtraKeys;
import com.alevap.utils.X11ToolbarViewPager;

import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("ApplySharedPref")
@SuppressWarnings({"deprecation", "unused"})
public class MainActivity extends AppCompatActivity {
    public static final String ACTION_STOP = "com.alevap.ACTION_STOP";
    public static final String ACTION_CUSTOM = "com.alevap.ACTION_CUSTOM";

    public static Handler handler = new Handler();
    FrameLayout frm;
    private TouchInputHandler mInputHandler;
    protected ICmdEntryInterface service = null;
    public TermuxX11ExtraKeys mExtraKeys;
    private Notification mNotification;
    private final int mNotificationId = 7892;
    NotificationManager mNotificationManager;
    static InputMethodManager inputMethodManager;
    private static boolean showIMEWhileExternalConnected = true;
    private static boolean externalKeyboardConnected = false;
    private View.OnKeyListener mLorieKeyListener;
    private boolean filterOutWinKey = false;
    boolean useTermuxEKBarBehaviour = false;
    private boolean isInPictureInPictureMode = false;

    public static Prefs prefs = null;

    private static boolean oldFullscreen = false, oldHideCutout = false;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChangedListener = (__, key) -> onPreferencesChanged(key);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @Override
        public void onReceive(Context context, Intent intent) {
            prefs.recheckStoringSecondaryDisplayPreferences();
            if (ACTION_START.equals(intent.getAction())) {
                try {
                    Log.v("LorieBroadcastReceiver", "Got new ACTION_START intent");
                    onReceiveConnection(intent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Something went wrong while we extracted connection details from binder.", e);
                }
            } else if (ACTION_STOP.equals(intent.getAction())) {
                finishAffinity();
            } else if (ACTION_PREFERENCES_CHANGED.equals(intent.getAction())) {
                Log.d("MainActivity", "preference: " + intent.getStringExtra("key"));
                if (!"additionalKbdVisible".equals(intent.getStringExtra("key")))
                    onPreferencesChanged("");
            } else if (ACTION_CUSTOM.equals(intent.getAction())) {
                android.util.Log.d("ACTION_CUSTOM", "action " + intent.getStringExtra("what"));
                mInputHandler.extractUserActionFromPreferences(prefs, intent.getStringExtra("what")).accept(0, true);
            }
        }
    };

    ViewTreeObserver.OnPreDrawListener mOnPredrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if (LorieView.connected())
                handler.post(() -> findViewById(android.R.id.content).getViewTreeObserver().removeOnPreDrawListener(mOnPredrawListener));
            return false;
        }
    };

    @SuppressLint("StaticFieldLeak")
    private static MainActivity instance;

    public MainActivity() {
        instance = this;
    }

    public static Prefs getPrefs() {
        return prefs;
    }

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    @SuppressLint({"AppCompatMethod", "ObsoleteSdkInt", "ClickableViewAccessibility", "WrongConstant", "UnspecifiedRegisterReceiverFlag"})
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(getPackageName(), "Starting " + getPackageName() + " version " + BuildConfig.VERSION_NAME);
        super.onCreate(savedInstanceState);

        prefs = new Prefs(this);
        int modeValue = Integer.parseInt(prefs.touchMode.get()) - 1;
        if (modeValue > 2)
            prefs.touchMode.put("1");

        oldFullscreen = prefs.fullscreen.get();
        oldHideCutout = prefs.hideCutout.get();

        prefs.get().registerOnSharedPreferenceChangeListener(preferencesChangedListener);

        getWindow().setFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | FLAG_KEEP_SCREEN_ON | FLAG_TRANSLUCENT_STATUS, 0);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main_activity);

        frm = findViewById(R.id.frame);
        findViewById(R.id.preferences_button).setOnClickListener((l) -> startActivity(new Intent(this, LoriePreferences.class) {{ setAction(Intent.ACTION_MAIN); }}));
        findViewById(R.id.help_button).setOnClickListener((l) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alevap/alev"))));
        findViewById(R.id.startup_button).setOnClickListener(this::onStartupClick);
        findViewById(R.id.exit_button).setOnClickListener((l) -> finish());

        LorieView lorieView = findViewById(R.id.lorieView);
        View lorieParent = (View) lorieView.getParent();

        mInputHandler = new TouchInputHandler(this, new InputEventSender(lorieView));
        mLorieKeyListener = (v, k, e) -> {
            InputDevice dev = e.getDevice();
            boolean result = mInputHandler.sendKeyEvent(e);

            // Do not steal dedicated buttons from a full external keyboard.
            if (useTermuxEKBarBehaviour && mExtraKeys != null && (dev == null || dev.isVirtual()))
                mExtraKeys.unsetSpecialKeys();
            return result;
        };

        lorieParent.setOnTouchListener((v, e) -> {
            // Avoid batched MotionEvent objects and reduce potential latency.
            // For reference: https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features#rendering.
            if (e.getAction() == MotionEvent.ACTION_DOWN)
                lorieParent.requestUnbufferedDispatch(e);

            return mInputHandler.handleTouchEvent(lorieParent, lorieView, e);
        });
        lorieParent.setOnHoverListener((v, e) -> mInputHandler.handleTouchEvent(lorieParent, lorieView, e));
        lorieParent.setOnGenericMotionListener((v, e) -> mInputHandler.handleTouchEvent(lorieParent, lorieView, e));
        lorieView.setOnCapturedPointerListener((v, e) -> mInputHandler.handleTouchEvent(lorieView, lorieView, e));
        lorieParent.setOnCapturedPointerListener((v, e) -> mInputHandler.handleTouchEvent(lorieView, lorieView, e));
        lorieView.setOnKeyListener(mLorieKeyListener);

        lorieView.setCallback((surfaceWidth, surfaceHeight, screenWidth, screenHeight) -> {
            String name;
            int framerate = (int) ((lorieView.getDisplay() != null) ? lorieView.getDisplay().getRefreshRate() : 30);

            mInputHandler.handleHostSizeChanged(surfaceWidth, surfaceHeight);
            mInputHandler.handleClientSizeChanged(screenWidth, screenHeight);
            if (lorieView.getDisplay() == null || lorieView.getDisplay().getDisplayId() == Display.DEFAULT_DISPLAY)
                name = "builtin";
            else if (SamsungDexUtils.checkDeXEnabled(this))
                name = "dex";
            else
                name = "external";
            LorieView.sendWindowChange(screenWidth, screenHeight, framerate, name);
        });

        registerReceiver(receiver, new IntentFilter(ACTION_START) {{ 
            addAction(ACTION_PREFERENCES_CHANGED);
            addAction(ACTION_STOP);
            addAction(ACTION_CUSTOM);
        }}, SDK_INT >= VERSION_CODES.TIRAMISU ? RECEIVER_EXPORTED : 0);

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // Taken from Stackoverflow answer https://stackoverflow.com/questions/7417123/android-how-to-adjust-layout-in-full-screen-mode-when-softkeyboard-is-visible/7509285#
        FullscreenWorkaround.assistActivity(this);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotification = buildNotification();
        mNotificationManager.notify(mNotificationId, mNotification);

        if (tryConnect()) {
            final View content = findViewById(android.R.id.content);
            content.getViewTreeObserver().addOnPreDrawListener(mOnPredrawListener);
            handler.postDelayed(() -> content.getViewTreeObserver().removeOnPreDrawListener(mOnPredrawListener), 500);
        }
        onPreferencesChanged("");

        toggleExtraKeys(false, false);

        initStylusAuxButtons();
        initMouseAuxButtons();

        if (SDK_INT >= VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 0);
        }

        onReceiveConnection(getIntent());
        findViewById(android.R.id.content).addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> makeSureHelpersAreVisibleAndInScreenBounds());

        setupDebugInfo();
        autoInit();
    }

    private void autoInit() {
        new Thread(() -> {
            try {
                Thread.sleep(1000); // UI settle
                
                File usrBinDir = new File(getFilesDir(), "usr/bin");
                logToDebug("Check usr/bin: exists=" + usrBinDir.exists() + " isDir=" + usrBinDir.isDirectory());

                if (usrBinDir.exists() && !usrBinDir.isDirectory()) {
                    logToDebug("CRITICAL: usr/bin is a file! Nuking...");
                    deleteRecursive(new File(getFilesDir(), "usr"));
                }

                // Ensure directory structure exists before anything else
                String[] dirs = {"usr/bin", "usr/tmp", "usr/lib", "usr/var/htop", "home"};
                for (String d : dirs) {
                    File dir = new File(getFilesDir(), d);
                    if (!dir.exists() || !dir.isDirectory()) {
                        if (dir.exists()) dir.delete();
                        boolean ok = dir.mkdirs();
                        logToDebug("Created dir " + d + ": " + ok);
                    } else {
                        logToDebug("Dir " + d + " already exists.");
                    }
                }

                extractAssets();
                runSelfTest();
            } catch (Exception e) {
                logToDebug("Auto-Init Error: " + e.getMessage());
            }
        }).start();
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    @SuppressLint("SetTextI18n")
    private void setupDebugInfo() {
        TextView debugText = findViewById(R.id.debug_info_text);
        Button copyButton = findViewById(R.id.copy_debug_button);

        if (debugText == null || copyButton == null) return;

        // Static info (Comprehensive)
        StringBuilder sb = new StringBuilder();
        sb.append("Package: ").append(getPackageName()).append("\n");
        sb.append("Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("ApplicationID: ").append(BuildConfig.APPLICATION_ID).append("\n");
        sb.append("Commit: ").append(BuildConfig.COMMIT).append("\n");
        sb.append("SDK: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS[0]).append("\n");
        sb.append("Native Lib Dir: ").append(getApplicationInfo().nativeLibraryDir).append("\n");
        
        java.io.File lib = new java.io.File(getApplicationInfo().nativeLibraryDir, "libXlorie.so");
        sb.append("libXlorie.so: ").append(lib.exists() ? "EXISTS (" + lib.length() + " bytes)" : "MISSING").append("\n");
        
        sb.append("UID: ").append(android.os.Process.myUid()).append("\n");
        sb.append("TMPDIR: ").append(System.getenv("TMPDIR")).append("\n");
        
        File internalUsrTmp = new File(getFilesDir(), "usr/tmp");
        if (!internalUsrTmp.exists()) internalUsrTmp.mkdirs();

        // Share current APK path with the terminal script
        try {
            java.io.File pathFile = new java.io.File(internalUsrTmp, "alevjd.path");
            java.io.FileWriter writer = new java.io.FileWriter(pathFile);
            writer.write(getApplicationInfo().sourceDir);
            writer.close();
            sb.append("Shared path updated: SUCCESS (").append(pathFile.getAbsolutePath()).append(")\n");
        } catch (Exception e) {
            sb.append("Shared path updated: FAILED (" + e.getMessage() + ")\n");
        }

        sb.append("\n--- LIVE LOGS ---\n");
        debugText.setText(sb.toString());

        // Add Floating Copy Button (Top Right)
        addFloatingCopyButton(debugText);

        // Start log monitoring thread
        new Thread(() -> {
            java.io.File logFile = new java.io.File(internalUsrTmp, "alevjd.log");
            long lastPointer = 0;
            while (!isFinishing()) {
                if (logFile.exists()) {
                    try (java.io.RandomAccessFile reader = new java.io.RandomAccessFile(logFile, "r")) {
                        if (logFile.length() < lastPointer) lastPointer = 0; // Log rotated/cleared
                        reader.seek(lastPointer);
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String finalLine = line;
                            runOnUiThread(() -> {
                                debugText.append(finalLine + "\n");
                                // Auto-scroll if it's in a scrollview (not yet, but good for future)
                            });
                        }
                        lastPointer = reader.getFilePointer();
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }).start();

        copyButton.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Alevap Debug Info", debugText.getText());
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(this, "Debug info copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    //Register the needed events to handle stylus as left, middle and right click
    @SuppressLint("ClickableViewAccessibility")
    private void initStylusAuxButtons() {
        final ViewPager pager = getTerminalToolbarViewPager();
        boolean stylusMenuEnabled = prefs.showStylusClickOverride.get() && LorieView.connected();
        final float menuUnselectedTrasparency = 0.66f;
        final float menuSelectedTrasparency = 1.0f;
        Button left = findViewById(R.id.button_left_click);
        Button right = findViewById(R.id.button_right_click);
        Button middle = findViewById(R.id.button_middle_click);
        Button visibility = findViewById(R.id.button_visibility);
        LinearLayout overlay = findViewById(R.id.mouse_helper_visibility);
        LinearLayout buttons = findViewById(R.id.mouse_helper_secondary_layer);
        overlay.setOnTouchListener((v, e) -> true);
        overlay.setOnHoverListener((v, e) -> true);
        overlay.setOnGenericMotionListener((v, e) -> true);
        overlay.setOnCapturedPointerListener((v, e) -> true);
        overlay.setVisibility(stylusMenuEnabled ? View.VISIBLE : View.GONE);
        View.OnClickListener listener = view -> {
            TouchInputHandler.STYLUS_INPUT_HELPER_MODE = (view.equals(left) ? 1 : (view.equals(middle) ? 2 : (view.equals(right) ? 4 : 0)));
            left.setAlpha((TouchInputHandler.STYLUS_INPUT_HELPER_MODE == 1) ? menuSelectedTrasparency : menuUnselectedTrasparency);
            middle.setAlpha((TouchInputHandler.STYLUS_INPUT_HELPER_MODE == 2) ? menuSelectedTrasparency : menuUnselectedTrasparency);
            right.setAlpha((TouchInputHandler.STYLUS_INPUT_HELPER_MODE == 4) ? menuSelectedTrasparency : menuUnselectedTrasparency);
            visibility.setAlpha(menuUnselectedTrasparency);
        };

        left.setOnClickListener(listener);
        middle.setOnClickListener(listener);
        right.setOnClickListener(listener);

        visibility.setOnClickListener(view -> {
            if (buttons.getVisibility() == View.VISIBLE) {
                buttons.setVisibility(View.GONE);
                visibility.setAlpha(menuUnselectedTrasparency);
                int m = TouchInputHandler.STYLUS_INPUT_HELPER_MODE;
                visibility.setText(m == 1 ? "L" : (m == 2 ? "M" : (m == 3 ? "R" : "U")));
            } else {
                buttons.setVisibility(View.VISIBLE);
                visibility.setAlpha(menuUnselectedTrasparency);
                visibility.setText("X");

                //Calculate screen border making sure btn is fully inside the view
                float maxX = frm.getWidth() - 4 * left.getWidth();
                float maxY = frm.getHeight() - 4 * left.getHeight();
                if (pager.getVisibility() == View.VISIBLE)
                    maxY -= pager.getHeight();

                //Make sure the Stylus menu is fully inside the screen
                overlay.setX(MathUtils.clamp(overlay.getX(), 0, maxX));
                overlay.setY(MathUtils.clamp(overlay.getY(), 0, maxY));

                int m = TouchInputHandler.STYLUS_INPUT_HELPER_MODE;
                listener.onClick(m == 1 ? left : (m == 2 ? middle : (m == 3 ? right : left)));
            }
        });
        //Simulated mouse click 1 = left , 2 = middle , 3 = right
        TouchInputHandler.STYLUS_INPUT_HELPER_MODE = 1;
        listener.onClick(left);

        visibility.setOnLongClickListener(v -> {
            v.startDragAndDrop(ClipData.newPlainText("", ""), new View.DragShadowBuilder(visibility) {
                public void onDrawShadow(@NonNull Canvas canvas) {}
            }, null, View.DRAG_FLAG_GLOBAL);

            frm.setOnDragListener((v2, event) -> {
                //Calculate screen border making sure btn is fully inside the view
                float maxX = frm.getWidth() - visibility.getWidth();
                float maxY = frm.getHeight() - visibility.getHeight();
                if (pager.getVisibility() == View.VISIBLE)
                    maxY -= pager.getHeight();

                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_LOCATION:
                        //Center touch location with btn icon
                        float dX = event.getX() - visibility.getWidth() / 2.0f;
                        float dY = event.getY() - visibility.getHeight() / 2.0f;

                        //Make sure the dragged btn is inside the view with clamp
                        overlay.setX(MathUtils.clamp(dX, 0, maxX));
                        overlay.setY(MathUtils.clamp(dY, 0, maxY));
                        break;
                    case DragEvent.ACTION_DRAG_ENDED:
                        //Make sure the dragged btn is inside the view
                        overlay.setX(MathUtils.clamp(overlay.getX(), 0, maxX));
                        overlay.setY(MathUtils.clamp(overlay.getY(), 0, maxY));
                        break;
                }
                return true;
            });

            return true;
        });
    }

    private void showStylusAuxButtons(boolean show) {
        LinearLayout buttons = findViewById(R.id.mouse_helper_visibility);
        if (LorieView.connected() && show) {
            buttons.setVisibility(View.VISIBLE);
            buttons.setAlpha(isInPictureInPictureMode ? 0.f : 1.f);
        } else {
            //Reset default input back to normal
            TouchInputHandler.STYLUS_INPUT_HELPER_MODE = 1;
            final float menuUnselectedTrasparency = 0.66f;
            final float menuSelectedTrasparency = 1.0f;
            findViewById(R.id.button_left_click).setAlpha(menuSelectedTrasparency);
            findViewById(R.id.button_right_click).setAlpha(menuUnselectedTrasparency);
            findViewById(R.id.button_middle_click).setAlpha(menuUnselectedTrasparency);
            findViewById(R.id.button_visibility).setAlpha(menuUnselectedTrasparency);
            buttons.setVisibility(View.GONE);
        }
    }

    private void makeSureHelpersAreVisibleAndInScreenBounds() {
        final ViewPager pager = getTerminalToolbarViewPager();
        View mouseAuxButtons = findViewById(R.id.mouse_buttons);
        View stylusAuxButtons = findViewById(R.id.mouse_helper_visibility);
        int maxYDecrement = (pager.getVisibility() == View.VISIBLE) ? pager.getHeight() : 0;

        mouseAuxButtons.setX(MathUtils.clamp(mouseAuxButtons.getX(), frm.getX(), frm.getX() + frm.getWidth() - mouseAuxButtons.getWidth()));
        mouseAuxButtons.setY(MathUtils.clamp(mouseAuxButtons.getY(), frm.getY(), frm.getY() + frm.getHeight() - mouseAuxButtons.getHeight() - maxYDecrement));

        stylusAuxButtons.setX(MathUtils.clamp(stylusAuxButtons.getX(), frm.getX(), frm.getX() + frm.getWidth() - stylusAuxButtons.getWidth()));
        stylusAuxButtons.setY(MathUtils.clamp(stylusAuxButtons.getY(), frm.getY(), frm.getY() + frm.getHeight() - stylusAuxButtons.getHeight() - maxYDecrement));
    }

    public void toggleStylusAuxButtons() {
        showStylusAuxButtons(findViewById(R.id.mouse_helper_visibility).getVisibility() != View.VISIBLE);
        makeSureHelpersAreVisibleAndInScreenBounds();
    }

    private void showMouseAuxButtons(boolean show) {
        View v = findViewById(R.id.mouse_buttons);
        v.setVisibility((LorieView.connected() && show && "1".equals(prefs.touchMode.get())) ? View.VISIBLE : View.GONE);
        v.setAlpha(isInPictureInPictureMode ? 0.f : 0.7f);
        makeSureHelpersAreVisibleAndInScreenBounds();
    }

    public void toggleMouseAuxButtons() {
        showMouseAuxButtons(findViewById(R.id.mouse_buttons).getVisibility() != View.VISIBLE);
    }

    void setSize(View v, int width, int height) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        p.width = (int) (width * getResources().getDisplayMetrics().density);
        p.height = (int) (height * getResources().getDisplayMetrics().density);
        v.setLayoutParams(p);
        v.setMinimumWidth((int) (width * getResources().getDisplayMetrics().density));
        v.setMinimumHeight((int) (height * getResources().getDisplayMetrics().density));
    }

    @SuppressLint("ClickableViewAccessibility")
    void initMouseAuxButtons() {
        final ViewPager pager = getTerminalToolbarViewPager();
        Button left = findViewById(R.id.mouse_button_left_click);
        Button right = findViewById(R.id.mouse_button_right_click);
        Button middle = findViewById(R.id.mouse_button_middle_click);
        ImageButton pos = findViewById(R.id.mouse_buttons_position);
        LinearLayout primaryLayer = findViewById(R.id.mouse_buttons);
        LinearLayout secondaryLayer = findViewById(R.id.mouse_buttons_secondary_layer);

        boolean mouseHelperEnabled = prefs.showMouseHelper.get() && "1".equals(prefs.touchMode.get());
        primaryLayer.setVisibility(mouseHelperEnabled ? View.VISIBLE : View.GONE);

        pos.setOnClickListener((v) -> {
            if (secondaryLayer.getOrientation() == LinearLayout.HORIZONTAL) {
                setSize(left, 48, 96);
                setSize(right, 48, 96);
                secondaryLayer.setOrientation(LinearLayout.VERTICAL);
            } else {
                setSize(left, 96, 48);
                setSize(right, 96, 48);
                secondaryLayer.setOrientation(LinearLayout.HORIZONTAL);
            }
            handler.postDelayed(() -> {
                float maxX = frm.getX() + frm.getWidth() - primaryLayer.getWidth();
                float maxY = frm.getY() + frm.getHeight() - primaryLayer.getHeight();
                if (pager.getVisibility() == View.VISIBLE)
                    maxY -= pager.getHeight();
                primaryLayer.setX(MathUtils.clamp(primaryLayer.getX(), frm.getX(), maxX));
                primaryLayer.setY(MathUtils.clamp(primaryLayer.getY(), frm.getY(), maxY));
            }, 10);
        });

        Map.of(left, InputStub.BUTTON_LEFT, middle, InputStub.BUTTON_MIDDLE, right, InputStub.BUTTON_RIGHT)
                .forEach((v, b) -> v.setOnTouchListener((__, e) -> {
            switch(e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    getLorieView().sendMouseEvent(0, 0, b, true, true);
                    v.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    getLorieView().sendMouseEvent(0, 0, b, false, true);
                    v.setPressed(false);
                    break;
            }
            return true;
        }));

        pos.setOnTouchListener(new View.OnTouchListener() {
            final int touchSlop = (int) Math.pow(ViewConfiguration.get(MainActivity.this).getScaledTouchSlop(), 2);
            final int tapTimeout = ViewConfiguration.getTapTimeout();
            final float[] startOffset = new float[2];
            final int[] startPosition = new int[2];
            long startTime;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch(e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        primaryLayer.getLocationInWindow(startPosition);
                        startOffset[0] = e.getX();
                        startOffset[1] = e.getY();
                        startTime = SystemClock.uptimeMillis();
                        pos.setPressed(true);
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        final ViewPager pager = getTerminalToolbarViewPager();
                        int[] offset = new int[2];
                        primaryLayer.getLocationInWindow(offset);
                        float maxX = frm.getX() + frm.getWidth() - primaryLayer.getWidth();
                        float maxY = frm.getY() + frm.getHeight() - primaryLayer.getHeight();
                        if (pager.getVisibility() == View.VISIBLE)
                            maxY -= pager.getHeight();

                        primaryLayer.setX(MathUtils.clamp(offset[0] - startOffset[0] + e.getX(), frm.getX(), maxX));
                        primaryLayer.setY(MathUtils.clamp(offset[1] - startOffset[1] + e.getY(), frm.getY(), maxY));
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        final int[] _pos = new int[2];
                        primaryLayer.getLocationInWindow(_pos);
                        int deltaX = (int) (startOffset[0] - e.getX()) + (startPosition[0] - _pos[0]);
                        int deltaY = (int) (startOffset[1] - e.getY()) + (startPosition[1] - _pos[1]);
                        pos.setPressed(false);

                        if (deltaX * deltaX + deltaY * deltaY < touchSlop && SystemClock.uptimeMillis() - startTime <= tapTimeout) {
                            v.performClick();
                            return true;
                        }
                        break;
                    }
                }
                return true;
            }
        });
    }

    void onReceiveConnection(Intent intent) {
        Bundle bundle = intent == null ? null : intent.getBundleExtra(null);
        IBinder ibinder = bundle == null ? null : bundle.getBinder(null);
        if (ibinder == null)
            return;

        service = ICmdEntryInterface.Stub.asInterface(ibinder);
        try {
            service.asBinder().linkToDeath(() -> {
                service = null;

                Log.v("Lorie", "Disconnected");
                runOnUiThread(() -> { LorieView.connect(-1); clientConnectedStateChanged();} );
            }, 0);
        } catch (RemoteException ignored) {}

        try {
            if (service != null && service.asBinder().isBinderAlive()) {
                Log.v("LorieBroadcastReceiver", "Extracting logcat fd.");
                ParcelFileDescriptor logcatOutput = service.getLogcatOutput();
                if (logcatOutput != null)
                    LorieView.startLogcat(logcatOutput.detachFd());

                tryConnect();

                if (intent != getIntent())
                    getIntent().putExtra(null, bundle);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Something went wrong while we were establishing connection", e);
        }
    }

    boolean tryConnect() {
        if (LorieView.connected())
            return false;

        if (service == null) {
            boolean sent = LorieView.requestConnection();
            handler.postDelayed(this::tryConnect, 250);
            return true;
        }

        try {
            ParcelFileDescriptor fd = service.getXConnection();
            if (fd != null) {
                Log.v("MainActivity", "Extracting X connection socket.");
                LorieView.connect(fd.detachFd());
                getLorieView().triggerCallback();
                clientConnectedStateChanged();
                getLorieView().reloadPreferences(prefs);
            } else
                handler.postDelayed(this::tryConnect, 250);
        } catch (Exception e) {
            Log.e("MainActivity", "Something went wrong while we were establishing connection", e);
            service = null;

            handler.postDelayed(this::tryConnect, 250);
        }
        return false;
    }

    void onPreferencesChanged(String key) {
        if ("additionalKbdVisible".equals(key))
            return;

        handler.removeCallbacks(this::onPreferencesChangedCallback);
        handler.postDelayed(this::onPreferencesChangedCallback, 100);
    }

    @SuppressLint("UnsafeIntentLaunch")
    void onPreferencesChangedCallback() {
        prefs.recheckStoringSecondaryDisplayPreferences();

        onWindowFocusChanged(hasWindowFocus());
        LorieView lorieView = getLorieView();

        mInputHandler.reloadPreferences(prefs);
        lorieView.reloadPreferences(prefs);

        setTerminalToolbarView();

        lorieView.triggerCallback();

        filterOutWinKey = prefs.filterOutWinkey.get();
        if (prefs.enableAccessibilityServiceAutomatically.get())
            KeyInterceptor.launch(this);
        else if (checkSelfPermission(WRITE_SECURE_SETTINGS) == PERMISSION_GRANTED)
            KeyInterceptor.shutdown(true);

        useTermuxEKBarBehaviour = prefs.useTermuxEKBarBehaviour.get();
        showIMEWhileExternalConnected = prefs.showIMEWhileExternalConnected.get();

        findViewById(R.id.mouse_buttons).setVisibility(prefs.showMouseHelper.get() && "1".equals(prefs.touchMode.get()) && LorieView.connected() ? View.VISIBLE : View.GONE);
        showMouseAuxButtons(prefs.showMouseHelper.get());
        showStylusAuxButtons(prefs.showStylusClickOverride.get());

        getTerminalToolbarViewPager().setAlpha(isInPictureInPictureMode ? 0.f : ((float) prefs.opacityEKBar.get())/100);

        lorieView.requestLayout();
        lorieView.invalidate();

        for (StatusBarNotification notification: mNotificationManager.getActiveNotifications())
            if (notification.getId() == mNotificationId) {
                mNotification = buildNotification();
                mNotificationManager.notify(mNotificationId, mNotification);
            }
    }

    @Override
    public void onResume() {
        super.onResume();

        mNotification = buildNotification();
        mNotificationManager.notify(mNotificationId, mNotification);

        setTerminalToolbarView();
        getLorieView().requestFocus();
    }

    @Override
    public void onPause() {
        inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getRootView().getWindowToken(), 0);

        for (StatusBarNotification notification: mNotificationManager.getActiveNotifications())
            if (notification.getId() == mNotificationId)
                mNotificationManager.cancel(mNotificationId);

        super.onPause();
    }

    public LorieView getLorieView() {
        return findViewById(R.id.lorieView);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return findViewById(R.id.terminal_toolbar_view_pager);
    }

    private void setTerminalToolbarView() {
        final ViewPager pager = getTerminalToolbarViewPager();
        ViewGroup parent = (ViewGroup) pager.getParent();

        boolean showNow = LorieView.connected() && prefs.showAdditionalKbd.get() && prefs.additionalKbdVisible.get();

        pager.setVisibility(showNow ? View.VISIBLE : View.INVISIBLE);

        if (showNow) {
            pager.setAdapter(new X11ToolbarViewPager.PageAdapter(this, (v, k, e) -> mInputHandler.sendKeyEvent(e)));
            pager.clearOnPageChangeListeners();
            pager.addOnPageChangeListener(new X11ToolbarViewPager.OnPageChangeListener(this, pager));
            pager.bringToFront();
        } else {
            parent.removeView(pager);
            parent.addView(pager, 0);
            if (mExtraKeys != null)
                mExtraKeys.unsetSpecialKeys();
        }

        ViewGroup.LayoutParams layoutParams = pager.getLayoutParams();
        layoutParams.height = Math.round(37.5f * getResources().getDisplayMetrics().density *
                (TermuxX11ExtraKeys.getExtraKeysInfo() == null ? 0 : TermuxX11ExtraKeys.getExtraKeysInfo().getMatrix().length));
        pager.setLayoutParams(layoutParams);

        frm.setPadding(0, 0, 0, prefs.adjustHeightForEK.get() && showNow ? layoutParams.height : 0);
        getLorieView().requestFocus();
    }

    public void toggleExtraKeys(boolean visible, boolean saveState) {
        boolean enabled = prefs.showAdditionalKbd.get();

        if (enabled && LorieView.connected() && saveState)
            prefs.additionalKbdVisible.put(visible);

        setTerminalToolbarView();
        getWindow().setSoftInputMode(prefs.Reseed.get() ? SOFT_INPUT_ADJUST_RESIZE : SOFT_INPUT_ADJUST_PAN);
    }

    public void toggleExtraKeys() {
        toggleExtraKeys(getTerminalToolbarViewPager().getVisibility() != View.VISIBLE, true);
    }

    public boolean handleKey(KeyEvent e) {
        if (filterOutWinKey && (e.getKeyCode() == KEYCODE_META_LEFT || e.getKeyCode() == KEYCODE_META_RIGHT || e.isMetaPressed()))
            return false;
        return mLorieKeyListener.onKey(getLorieView(), e.getKeyCode(), e);
    }

    @SuppressLint("ObsoleteSdkInt")
    Notification buildNotification() {
        NotificationCompat.Builder builder =  new NotificationCompat.Builder(this, getNotificationChannel(mNotificationManager))
                .setContentTitle("Alevap")
                .setSmallIcon(R.drawable.ic_x11_icon)
                .setContentText(getResources().getText(R.string.notification_content_text))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setSilent(true)
                .setShowWhen(false)
                .setColor(0xFF607D8B);
        return mInputHandler.setupNotification(prefs, builder).build();
    }

    private String getNotificationChannel(NotificationManager notificationManager){
        String channelId = getResources().getString(R.string.app_name);
        String channelName = getResources().getString(R.string.app_name);
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        if (SDK_INT >= VERSION_CODES.Q)
            channel.setAllowBubbles(false);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    int orientation;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation != orientation)
            inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getRootView().getWindowToken(), 0);

        orientation = newConfig.orientation;
        setTerminalToolbarView();
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        KeyInterceptor.recheck();
        prefs.recheckStoringSecondaryDisplayPreferences();
        Window window = getWindow();
        View decorView = window.getDecorView();
        boolean fullscreen = prefs.fullscreen.get();
        boolean hideCutout = prefs.hideCutout.get();
        boolean reseed = prefs.Reseed.get();

        if (oldHideCutout != hideCutout || oldFullscreen != fullscreen) {
            oldHideCutout = hideCutout;
            oldFullscreen = fullscreen;
            // For some reason cutout or fullscreen change makes layout calculations wrong and invalid.
            // I did not find simple and reliable way to fix it so it is better to start from the beginning.
            recreate();
            return;
        }

        int requestedOrientation;
        switch (prefs.forceOrientation.get()) {
            case "portrait": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; break;
            case "landscape": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
            case "reverse portrait": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT; break;
            case "reverse landscape": requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE; break;
            default: requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }

        if (getRequestedOrientation() != requestedOrientation)
            setRequestedOrientation(requestedOrientation);

        if (hasFocus) {
            if (SDK_INT >= VERSION_CODES.P) {
                if (hideCutout)
                    getWindow().getAttributes().layoutInDisplayCutoutMode = (SDK_INT >= VERSION_CODES.R) ?
                            LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS :
                            LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                else
                    getWindow().getAttributes().layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }

            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }

        window.setFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | FLAG_KEEP_SCREEN_ON | FLAG_TRANSLUCENT_STATUS, 0);
        if (hasFocus) {
            if (fullscreen) {
                window.addFlags(FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                window.clearFlags(FLAG_FULLSCREEN);
                decorView.setSystemUiVisibility(0);
            }
        }

        if (prefs.keepScreenOn.get())
            window.addFlags(FLAG_KEEP_SCREEN_ON);
        else
            window.clearFlags(FLAG_KEEP_SCREEN_ON);

        window.setSoftInputMode(reseed ? SOFT_INPUT_ADJUST_RESIZE : SOFT_INPUT_ADJUST_PAN);

        ((FrameLayout) findViewById(android.R.id.content)).getChildAt(0).setFitsSystemWindows(!fullscreen);
    }

    @Override
    public void onBackPressed() {
    }

    public static boolean hasPipPermission(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOpsManager == null)
            return false;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        else
            return appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onUserLeaveHint() {
        if (prefs.PIP.get() && hasPipPermission(this)) {
            enterPictureInPictureMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        this.isInPictureInPictureMode = isInPictureInPictureMode;
        final ViewPager pager = getTerminalToolbarViewPager();
        pager.setAlpha(isInPictureInPictureMode ? 0.f : ((float) prefs.opacityEKBar.get())/100);
        findViewById(R.id.mouse_buttons).setAlpha(isInPictureInPictureMode ? 0.f : 0.7f);
        findViewById(R.id.mouse_helper_visibility).setAlpha(isInPictureInPictureMode ? 0.f : 1.f);

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    }

    /**
     * Manually toggle soft keyboard visibility
     * @param context calling context
     */
    public static void toggleKeyboardVisibility(Context context) {
        Log.d("MainActivity", "Toggling keyboard visibility");
        if(inputMethodManager != null) {
            android.util.Log.d("toggleKeyboardVisibility", "externalKeyboardConnected " + externalKeyboardConnected + " showIMEWhileExternalConnected " + showIMEWhileExternalConnected);
            if (!externalKeyboardConnected || showIMEWhileExternalConnected)
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            else
                inputMethodManager.hideSoftInputFromWindow(getInstance().getWindow().getDecorView().getRootView().getWindowToken(), 0);

            getInstance().getLorieView().requestFocus();
        }
    }

    @SuppressWarnings("SameParameterValue")
    void clientConnectedStateChanged() {
        runOnUiThread(()-> {
            boolean connected = LorieView.connected();
            setTerminalToolbarView();
            findViewById(R.id.mouse_buttons).setVisibility(prefs.showMouseHelper.get() && "1".equals(prefs.touchMode.get()) && connected ? View.VISIBLE : View.GONE);
            findViewById(R.id.stub).setVisibility(connected?View.INVISIBLE:View.VISIBLE);
            getLorieView().setVisibility(connected?View.VISIBLE:View.INVISIBLE);

            // We should recover connection in the case if file descriptor for some reason was broken...
            if (!connected)
                tryConnect();
            else
                getLorieView().setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));

            onWindowFocusChanged(hasWindowFocus());
        });
    }

    public static boolean isConnected() {
        if (getInstance() == null)
            return false;

        return LorieView.connected();
    }

    public static void getRealMetrics(DisplayMetrics m) {
        if (getInstance() != null &&
                getInstance().getLorieView() != null &&
                getInstance().getLorieView().getDisplay() != null)
            getInstance().getLorieView().getDisplay().getRealMetrics(m);
    }

    public static void setCapturingEnabled(boolean enabled) {
        if (getInstance() == null || getInstance().mInputHandler == null)
            return;

        getInstance().mInputHandler.setCapturingEnabled(enabled);
    }

    public boolean shouldInterceptKeys() {
        View textInput = findViewById(R.id.terminal_toolbar_text_input);
        if (mInputHandler == null || !hasWindowFocus() || (textInput != null && textInput.isFocused()))
            return false;

        return mInputHandler.shouldInterceptKeys();
    }

    private void onStartupClick(View v) {
        logToDebug("Startup button pressed.");
        v.setEnabled(false);
        launchJWM();
        // Re-enable button after a delay to prevent spamming
        handler.postDelayed(() -> v.setEnabled(true), 3000);
    }

    public void logToDebug(String msg) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        String line = "[" + timestamp + "] " + msg;
        Log.i("AlevStartup", line);
        runOnUiThread(() -> {
            TextView tv = findViewById(R.id.debug_info_text);
            if (tv != null) {
                tv.append(line + "\n");
                // Scroll to bottom
                View parent = (View) tv.getParent();
                if (parent instanceof android.widget.ScrollView) {
                    ((android.widget.ScrollView) parent).fullScroll(View.FOCUS_DOWN);
                }
            }
        });
    }

    private void extractAssets() {
        File usrDir = new File(getFilesDir(), "usr");
        File jwmrcFile = new File(usrDir, "etc/system.jwmrc");
        
        android.content.SharedPreferences sp = getSharedPreferences("alev_prefs", MODE_PRIVATE);
        int lastVersion = sp.getInt("last_assets_version", -1);
        int currentVersion = BuildConfig.VERSION_CODE;

        if (usrDir.exists() && jwmrcFile.exists() && lastVersion == currentVersion) {
            logToDebug("Assets already extracted, skipping.");
        } else {
            logToDebug("Extracting/Updating assets (v" + currentVersion + ")...");
            extractAssetFolder("bootstrap");
            sp.edit().putInt("last_assets_version", currentVersion).apply();
        }
        // Create scripts AFTER assets to ensure directories exist
        createServerScript();
        logToDebug("Assets ready.");
    }

    private void createServerScript() {
        File usrDir = new File(getFilesDir(), "usr");
        File usrBin = new File(usrDir, "bin");
        
        // Force cleanup of bin directory to resolve potential filesystem/symlink issues
        try {
            if (usrBin.exists()) {
                deleteRecursive(usrBin);
                logToDebug("Cleaned up old usr/bin");
            }
            if (!usrBin.mkdirs()) {
                logToDebug("Warning: mkdirs for usr/bin returned false");
            }
        } catch (Exception e) {
            logToDebug("Cleanup Error: " + e.getMessage());
        }

        logToDebug("Writing wrappers to: " + usrBin.getAbsolutePath());
        String libDir = getApplicationInfo().nativeLibraryDir;
        
        // Ensure stat file exists
        File statFile = new File(getFilesDir(), "usr/var/htop/stat");
        try {
            File statDir = statFile.getParentFile();
            if (statDir != null && !statDir.exists()) statDir.mkdirs();
            
            long btime = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000;
            String statContent = "btime " + btime + "\n";
            try (FileOutputStream fos = new FileOutputStream(statFile)) {
                fos.write(statContent.getBytes());
            }
            statFile.setReadable(true, false);
        } catch (IOException ignored) {}
        
        File script = new File(usrBin, "start_server.sh");
        String apkPath = getApplicationInfo().sourceDir;
        
        // The script now creates symlinks for convenience and sets up executable environment
        String content = "#!/system/bin/sh\n" +
                "export CLASSPATH=\"" + apkPath + "\"\n" +
                "export TMPDIR=\"" + getFilesDir() + "/usr/tmp\"\n" +
                "export HOME=\"" + getFilesDir() + "/home\"\n" +
                "export LIBDIR=\"" + libDir + "\"\n" +
                "mkdir -p \"$TMPDIR\"\n" +
                "mkdir -p \"$HOME\"\n" +
                "rm -f \"$TMPDIR/server.log\"\n" +
                "export LD_LIBRARY_PATH=\"" + getFilesDir() + "/usr/lib:$LIBDIR\"\n" +
                "export PATH=\"" + usrBin.getAbsolutePath() + ":$LIBDIR:$PATH\"\n" +
                "exec /system/bin/app_process -Xnoimage-dex2oat / com.alevap.CmdEntryPoint :1 > \"$TMPDIR/server.log\" 2>&1 &\n";
        
        try (FileOutputStream fos = new FileOutputStream(script)) {
            fos.write(content.getBytes());
        } catch (IOException e) { logToDebug("Script Write Error: " + e.getMessage()); }
        script.setExecutable(true);

        // Create Strong Wrappers for binaries to work in terminal
        String[] bins = {"htop", "xeyes", "jwm", "aterm", "xterm", "bash", "sh", "Xwayland"};
        for (String b : bins) {
            File wrapper = new File(usrBin, b);
            String wrapperContent = "#!/system/bin/sh\n" +
                    "export DISPLAY=\":1\"\n" +
                    "export PREFIX=\"" + getFilesDir() + "/usr\"\n" +
                    "export TERMUX_PREFIX=\"" + getFilesDir() + "/usr\"\n" +
                    "export LD_LIBRARY_PATH=\"" + getFilesDir() + "/usr/lib:" + libDir + "\"\n" +
                    "export PATH=\"" + usrBin.getAbsolutePath() + ":" + libDir + ":$PATH\"\n" +
                    "export TERMINFO=\"" + getFilesDir() + "/usr/share/terminfo\"\n" +
                    "export SHELL=\"" + usrBin.getAbsolutePath() + "/bash\"\n" +
                    "export HOME=\"" + getFilesDir() + "/home\"\n" +
                    "export TMPDIR=\"" + getFilesDir() + "/usr/tmp\"\n" +
                    "exec \"" + libDir + "/lib" + b + ".so\" \"$@\"\n";
            
            try {
                try (FileOutputStream fos = new FileOutputStream(wrapper)) {
                    fos.write(wrapperContent.getBytes());
                }
                wrapper.setExecutable(true, false);
                // Explicit chmod via shell for reliability on some devices/storage types
                try {
                    Runtime.getRuntime().exec("chmod 755 " + wrapper.getAbsolutePath()).waitFor();
                } catch (Exception ignored) {}
            } catch (Exception e) { 
                logToDebug("Wrapper Error (" + b + "): " + e.getMessage());
            }
        }
        logToDebug("Wrappers updated in bin/");

        // Create 'stop' script (Targeting X Server process only)
        File stopScript = new File(usrBin, "stop");
        String stopContent = "#!/system/bin/sh\n" +
                "PID=$(pidof com.alevap.CmdEntryPoint)\n" +
                "if [ -n \"$PID\" ]; then\n" +
                "  kill $PID\n" +
                "  echo \"X Server stopped.\"\n" +
                "else\n" +
                "  echo \"X Server not running.\"\n" +
                "fi\n";
        try (FileOutputStream fos = new FileOutputStream(stopScript)) {
            fos.write(stopContent.getBytes());
        } catch (IOException e) { logToDebug("Stop Script Write Error: " + e.getMessage()); }
        stopScript.setExecutable(true);
    }

    private void runSelfTest() {
        logToDebug("--- STARTING SELF-TEST ---");
        File usrBin = new File(getFilesDir(), "usr/bin");
        String ldPath = new File(getFilesDir(), "usr/lib").getAbsolutePath() + ":" + getApplicationInfo().nativeLibraryDir;
        String libDir = getApplicationInfo().nativeLibraryDir;

        // 1. Identity Check
        try {
            Process p = Runtime.getRuntime().exec("whoami");
            String who = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream())).readLine();
            logToDebug("[SELF-TEST] Identity: " + who);
        } catch (Exception e) { logToDebug("[FAIL] Identity: " + e.getMessage()); }

        // 2. Binary Checks (Testing WRAPPERS via sh)
        String[] binaries = {"bash", "sh", "htop", "xeyes", "jwm", "aterm", "Xwayland"};
        for (String bin : binaries) {
            File wrapperFile = new File(usrBin, bin);
            if (wrapperFile.exists()) {
                try {
                    List<String> args = new ArrayList<>();
                    args.add("/system/bin/sh");
                    args.add(wrapperFile.getAbsolutePath());
                    
                    if (bin.equals("htop") || bin.equals("jwm")) args.add("-h");
                    else if (bin.equals("Xwayland")) args.add("-version");
                    else if (bin.equals("sh") || bin.equals("bash")) {
                        args.add("-c");
                        args.add("echo ALEVAP-OK");
                    } else args.add("--version");
                    
                    ProcessBuilder pb = new ProcessBuilder(args);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    String out = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream())).readLine();
                    
                    if ((bin.equals("sh") || bin.equals("bash")) && out != null && out.contains("ALEVAP-OK")) {
                        out = "Shell functional (via wrapper)";
                    }

                    if (out == null || out.isEmpty() || out.startsWith("Usage")) {
                         out = bin + " WRAPPER OK";
                    }
                    logToDebug("[SELF-TEST] " + bin + ": " + out);
                    p.destroy();
                } catch (Exception e) { logToDebug("[FAIL] " + bin + " Wrapper: " + e.getMessage()); }
            } else {
                logToDebug("[MISSING] " + bin + " Wrapper!");
            }
        }

        // 5. JNI Check
        try {
            logToDebug("[SELF-TEST] JNI: " + (LorieView.connected() ? "CONNECTED" : "DISCONNECTED (but loaded)"));
        } catch (Exception e) { logToDebug("[FAIL] JNI: " + e.getMessage()); }
        
        logToDebug("--- SELF-TEST FINISHED ---");
    }

    private void launchJWM() {
        logToDebug("--- LAUNCHING JWM ---");
        File usrDir = new File(getFilesDir(), "usr");
        File usrBin = new File(usrDir, "bin");
        String libDir = getApplicationInfo().nativeLibraryDir;
        File jwmFile = new File(libDir, "libjwm.so");
        File jwmrcFile = new File(usrDir, "etc/system.jwmrc");
        File startScript = new File(usrBin, "start_server.sh");
        String ldPath = usrDir.getAbsolutePath() + "/lib:" + libDir;
        
        new Thread(() -> {
            try {
                // Check if we need to start the server
                if (!LorieView.connected()) {
                    logToDebug("Starting X Server (:1)...");
                    // Use our bundled shell to run the script (avoids Error 13)
                    String shPath = libDir + "/libsh.so";
                    Runtime.getRuntime().exec(new String[]{shPath, startScript.getAbsolutePath()});
                    logToDebug("Waiting for connection...");
                    int timeout = 0;
                    while (!LorieView.connected() && timeout < 100) {
                        Thread.sleep(100);
                        timeout++;
                    }
                    if (timeout >= 100) {
                        logToDebug("Server start TIMEOUT.");
                        return;
                    }
                }

                if (jwmFile.exists()) {
                    logToDebug("Starting JWM...");
                    File jwmWrapper = new File(usrBin, "jwm");
                    ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", jwmWrapper.getAbsolutePath(), "-display", ":1", "-f", jwmrcFile.getAbsolutePath());
                    
                    // Full Environment
                    Map<String, String> env = pb.environment();
                    env.put("LD_LIBRARY_PATH", ldPath);
                    env.put("PATH", usrBin.getAbsolutePath() + ":" + libDir + ":" + System.getenv("PATH"));
                    env.put("HOME", getFilesDir() + "/home");
                    env.put("TMPDIR", getFilesDir() + "/usr/tmp");
                    env.put("DISPLAY", ":1");
                    env.put("TERMINFO", getFilesDir() + "/usr/share/terminfo");
                    env.put("SHELL", usrBin.getAbsolutePath() + "/bash");
                    
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    
                    // Monitor output
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logToDebug("[jwm] " + line);
                    }
                    logToDebug("jwm exited with " + p.waitFor());
                }
            } catch (Exception e) {
                logToDebug("Launch Error: " + e.getMessage());
            }
        }).start();
    }

    private void extractAssetFolder(String relativePath) {
        try {
            String[] assets = getAssets().list(relativePath);
            if (assets == null || assets.length == 0) {
                extractAsset(relativePath);
            } else {
                for (String asset : assets) {
                    extractAssetFolder(relativePath + (relativePath.isEmpty() ? "" : "/") + asset);
                }
            }
        } catch (IOException e) {
            logToDebug("Asset Error: " + e.getMessage());
        }
    }

    private void extractAsset(String assetPath) {
        if (!assetPath.startsWith("bootstrap")) return;
        try {
            String targetRelativePath = assetPath.replaceFirst("bootstrap", "usr");
            File outFile = new File(getFilesDir(), targetRelativePath);
            
            if (outFile.exists() && outFile.isDirectory()) return; // skip if it's already a dir

            try (InputStream is = getAssets().open(assetPath)) {
                // If we can open it, it's a file
                outFile.getParentFile().mkdirs();
                try (FileOutputStream os = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) os.write(buffer, 0, len);
                }
            } catch (Exception e) {
                // If it's a directory, open() fails
                if (!outFile.exists()) outFile.mkdirs();
            }

            if (assetPath.contains("/bin/") || assetPath.contains("/libexec/")) {
                outFile.setExecutable(true, false);
                try {
                    Runtime.getRuntime().exec("chmod 700 " + outFile.getAbsolutePath());
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logToDebug("Extract Fail: " + assetPath + " - " + e.getMessage());
        }
    }

    public void setExternalKeyboardConnected(boolean connected) {
        externalKeyboardConnected = connected;
        EditText textInput = findViewById(R.id.terminal_toolbar_text_input);
        if (textInput != null)
            textInput.setShowSoftInputOnFocus(!connected || showIMEWhileExternalConnected);
        if (connected && !showIMEWhileExternalConnected)
            inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getRootView().getWindowToken(), 0);
                getLorieView().requestFocus();
            }
        
            private void addFloatingCopyButton(final TextView debugText) {
                runOnUiThread(() -> {
                    FrameLayout root = findViewById(android.R.id.content);
                    if (root == null) return;
        
                    Button btn = new Button(this);
                    btn.setText("Copy Log");
                    btn.setAlpha(0.5f);
                    btn.setTextSize(10);
                    
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.gravity = Gravity.TOP | Gravity.END;
                    lp.setMargins(0, 100, 20, 0); // Position it below potential cutouts
                    
                    btn.setLayoutParams(lp);
                    btn.setOnClickListener(v -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Alevap Logs", debugText.getText());
                        clipboard.setPrimaryClip(clip);
                        android.widget.Toast.makeText(this, "Logs copied!", android.widget.Toast.LENGTH_SHORT).show();
                    });
        
                    root.addView(btn);
                });
            }
        }
        
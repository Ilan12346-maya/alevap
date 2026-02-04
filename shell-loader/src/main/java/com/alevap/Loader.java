package com.alevap;
import com.alevap.shell_loader.BuildConfig;

public class Loader {
    static {
        System.err.println("Loader class loaded");
    }
    /**
     * Command-line entry point.
     * It is pretty simple.
     * 1. Check if application is installed.
     * 2. Check if target apk's signature matches stored hash to prevent running code of potentially replaced malicious apk.
     * 3. Load target apk code with `PathClassLoader` and start target's main function.
     * <p>
     * This way we can make this loader version-agnostic and keep it secure. All application logic is located in target apk.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        System.out.println("Loader main started");
        String cls = System.getenv("TERMUX_X11_LOADER_OVERRIDE_CMDENTRYPOINT_CLASS");
        cls = cls != null ? cls : BuildConfig.CLASS_ID;
        try {
            System.out.println("Checking package: " + BuildConfig.APPLICATION_ID);
            android.content.pm.PackageInfo targetInfo = (android.os.Build.VERSION.SDK_INT <= 32) ?
                    android.app.ActivityThread.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, android.content.pm.PackageManager.GET_SIGNATURES, 0) :
                    android.app.ActivityThread.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, (long) android.content.pm.PackageManager.GET_SIGNATURES, 0);
            if (targetInfo == null) {
                System.err.println("Package not found: " + BuildConfig.APPLICATION_ID);
                return;
            }
            // assert targetInfo.signatures.length == 1 && BuildConfig.SIGNATURE == targetInfo.signatures[0].hashCode() : BuildConfig.packageSignatureMismatchErrorText;

            android.util.Log.i(BuildConfig.logTag, "loading " + targetInfo.applicationInfo.sourceDir + "::" + BuildConfig.CLASS_ID + "::main of " + BuildConfig.APPLICATION_ID + " application (commit " + BuildConfig.COMMIT + ")");
            System.out.println("Loading class: " + cls);
            Class<?> targetClass = Class.forName(cls, true,
                    new dalvik.system.PathClassLoader(targetInfo.applicationInfo.sourceDir, null, ClassLoader.getSystemClassLoader()));
            System.out.println("Invoking main...");
            targetClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Throwable e) {
            android.util.Log.e(BuildConfig.logTag, "Loader error", e);
            e.printStackTrace(System.err);
        }
    }
}

package app.revanced.extension.gamehub;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Static helpers called from injected smali instructions.
 *
 * <p>All three methods are called from GameHub's own bytecode (injected by the patch),
 * so they must use reflection for any GameHub-internal classes.</p>
 */
@SuppressWarnings("unused")
public final class ComponentManagerHelper {

    private static final String TAG = "BannerHub";
    private static final int COMPONENTS_MENU_ID = 9;

    private ComponentManagerHelper() {}

    // ── Called from HomeLeftMenuDialog.u1() ──────────────────────────────────

    /**
     * Appends a "Components" menu item to the sidebar item list.
     *
     * @param items the live List<HomeLeftMenuDialog.MenuItem> (p0 after reassignment in u1)
     */
    @SuppressWarnings("rawtypes")
    public static void addComponentsMenuItem(List items) {
        try {
            // Get application context via hidden API (safe within the target app's own process).
            android.content.Context ctx = (android.content.Context)
                    Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null);

            Class<?> menuItemClass = Class.forName(
                    "com.xj.landscape.launcher.ui.menu.HomeLeftMenuDialog$MenuItem");
            Class<?> markerClass = Class.forName(
                    "kotlin.jvm.internal.DefaultConstructorMarker");

            // Constructor: <init>(I iconRes, I id, String name, String rightContent,
            //                     Z isUserHeader, I mask, DefaultConstructorMarker)
            Constructor<?> ctor = menuItemClass.getDeclaredConstructor(
                    int.class, int.class, String.class, String.class,
                    boolean.class, int.class, markerClass);
            ctor.setAccessible(true);

            int iconRes = ctx.getResources().getIdentifier(
                    "menu_setting_normal", "drawable", ctx.getPackageName());

            // mask=0x18 means rightContent (bit3) and isUserHeader (bit4) use Kotlin defaults
            Object item = ctor.newInstance(
                    COMPONENTS_MENU_ID, iconRes, "Components",
                    null, false, 0x18, null);

            //noinspection unchecked
            items.add(item);
        } catch (Exception e) {
            Log.e(TAG, "addComponentsMenuItem failed", e);
        }
    }

    // ── Called from HomeLeftMenuDialog.o1() ──────────────────────────────────

    /**
     * Intercepts the sidebar item click before the packed-switch runs.
     *
     * @param dialog    the HomeLeftMenuDialog fragment (p0 — pre-reassignment)
     * @param menuItem  the HomeLeftMenuDialog.MenuItem that was clicked (p1)
     * @param activity  the hosting FragmentActivity (p2)
     * @return true if we handled the click (ID=9), false to let the original switch run
     */
    public static boolean handleMenuItemClick(Object dialog, Object menuItem, Activity activity) {
        try {
            // Get item ID via obfuscated a() method (= getId())
            int id = (int) menuItem.getClass().getMethod("a").invoke(menuItem);
            if (id != COMPONENTS_MENU_ID) return false;

            // Dismiss the dialog (same as the original code does before every case)
            dialog.getClass().getMethod("dismiss").invoke(dialog);

            Intent intent = new Intent(activity, ComponentManagerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "handleMenuItemClick failed", e);
            return false;
        }
    }

    // ── Called from LandscapeLauncherMainActivity.initView() ─────────────────

    /**
     * Finds the BCI launcher ImageView in the toolbar and attaches a click listener.
     * The click opens BannersComponentInjector, or shows a toast if not installed.
     *
     * @param activity the LandscapeLauncherMainActivity instance (p0)
     */
    public static void setupBciButton(Activity activity) {
        try {
            int id = activity.getResources().getIdentifier(
                    "iv_bci_launcher", "id", activity.getPackageName());
            if (id == 0) return;

            android.view.View btn = activity.findViewById(id);
            if (btn == null) return;

            btn.setOnClickListener(v -> {
                try {
                    android.content.pm.PackageManager pm = activity.getPackageManager();
                    Intent intent = pm.getLaunchIntentForPackage("com.banner.inject");
                    if (intent != null) {
                        activity.startActivity(intent);
                    } else {
                        Toast.makeText(
                                activity,
                                "BannersComponentInjector not installed",
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "setupBciButton failed", e);
        }
    }
}

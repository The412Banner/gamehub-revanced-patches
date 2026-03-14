package app.revanced.extension.gamehub;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import androidx.fragment.app.Fragment;

@SuppressWarnings("unused")
public final class RtsOverlaySetup {

    private static final String TAG = "BannerHub";

    // Static refs so toggleRtsOverlay() can reach the overlay without activity fields
    private static WeakReference<RtsTouchOverlayView> overlayRef;
    private static WeakReference<Activity> activityRef;

    private RtsOverlaySetup() {}

    /**
     * Called from WineActivity (via injected smali at RETURN_VOID of the setup method).
     * Creates RtsTouchOverlayView and adds it on top of the InputControlsView's parent.
     */
    public static void setupRtsOverlay(Activity activity) {
        try {
            // Get InputControlsView from field 'w'
            Field wField = activity.getClass().getDeclaredField("w");
            wField.setAccessible(true);
            View inputControlsView = (View) wField.get(activity);
            if (inputControlsView == null) return;

            // Get btnLayout = parent of inputControlsView
            ViewGroup btnLayout = (ViewGroup) inputControlsView.getParent();
            if (btnLayout == null) return;

            // Get WinUIBridge from field 'h'
            Field hField = activity.getClass().getDeclaredField("h");
            hField.setAccessible(true);
            Object winUiBridge = hField.get(activity);

            // Create overlay
            RtsTouchOverlayView overlay = new RtsTouchOverlayView(activity);
            overlay.winUiBridge = winUiBridge;
            overlay.buttonsView = inputControlsView;
            overlay.setClickable(true);

            // Add MATCH_PARENT on top
            btnLayout.addView(overlay, new ViewGroup.LayoutParams(-1, -1));

            // Store refs
            overlayRef = new WeakReference<>(overlay);
            activityRef = new WeakReference<>(activity);

            // Set initial visibility
            boolean enabled = RtsSettings.getRtsTouchControlsEnabled();
            overlay.setVisibility(enabled ? View.VISIBLE : View.GONE);

            // If RTS enabled at startup, disable screen trackpad
            if (enabled && winUiBridge != null) {
                winUiBridge.getClass().getMethod("o0", boolean.class).invoke(winUiBridge, false);
                disableScreenTrackpadForProfile(activity, winUiBridge);
            }

        } catch (Exception e) {
            Log.e(TAG, "setupRtsOverlay failed", e);
        }
    }

    /**
     * Called from RtsSwitchClickListener (our extension code) to toggle the overlay.
     */
    public static void toggleRtsOverlay(boolean enabled) {
        RtsTouchOverlayView overlay = overlayRef != null ? overlayRef.get() : null;
        if (overlay == null) return;
        Activity activity = activityRef != null ? activityRef.get() : null;

        if (enabled) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setEnabled(true);
            overlay.setClickable(true);
            // Disable screen trackpad
            if (activity != null) {
                try {
                    Field hField = activity.getClass().getDeclaredField("h");
                    hField.setAccessible(true);
                    Object winUiBridge = hField.get(activity);
                    if (winUiBridge != null) {
                        winUiBridge.getClass().getMethod("o0", boolean.class).invoke(winUiBridge, false);
                        disableScreenTrackpadForProfile(activity, winUiBridge);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "toggleRtsOverlay enable failed", e);
                }
            }
        } else {
            overlay.setVisibility(View.INVISIBLE);
            overlay.setEnabled(false);
            overlay.setClickable(false);
            // Restore screen trackpad based on profile setting
            if (activity != null) {
                try {
                    Field hField = activity.getClass().getDeclaredField("h");
                    hField.setAccessible(true);
                    Object winUiBridge = hField.get(activity);
                    if (winUiBridge != null) {
                        boolean trackpadEnabled = getProfileTrackpadEnabled(activity);
                        winUiBridge.getClass().getMethod("o0", boolean.class).invoke(winUiBridge, trackpadEnabled);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "toggleRtsOverlay disable failed", e);
                }
            }
        }
    }

    /**
     * Called from SidebarControlsFragment.j0() (injected smali) to add the RTS
     * switch and gear button into the existing controls sidebar fragment.
     */
    public static void setupSidebarRts(Object fragment) {
        try {
            Fragment f = (Fragment) fragment;
            View root = f.getView();
            if (root == null) return;
            String pkg = root.getContext().getPackageName();

            // Find the RTS switch
            int switchId = root.getResources().getIdentifier("switch_rts_touch_controls", "id", pkg);
            if (switchId == 0) return;
            View switchView = root.findViewById(switchId);
            if (switchView == null) return;

            // Set initial switch state
            boolean enabled = RtsSettings.getRtsTouchControlsEnabled();
            try {
                switchView.getClass().getMethod("setSwitch", boolean.class).invoke(switchView, enabled);
            } catch (Exception e) {
                Log.e(TAG, "setSwitch failed", e);
            }

            // Set click listener on switch
            switchView.setOnClickListener(v -> {
                try {
                    boolean current = (boolean) v.getClass().getMethod("getSwitchState").invoke(v);
                    boolean newState = !current;
                    v.getClass().getMethod("setSwitch", boolean.class).invoke(v, newState);
                    RtsSettings.setRtsTouchControlsEnabled(newState);
                    RtsOverlaySetup.toggleRtsOverlay(newState);
                    // Update gear button visibility
                    updateGearButton(root, pkg, newState);
                } catch (Exception e) {
                    Log.e(TAG, "RTS switch click failed", e);
                }
            });

            // Find gear button
            int gearId = root.getResources().getIdentifier("btn_rts_gesture_settings", "id", pkg);
            View gearBtn = root.findViewById(gearId);
            if (gearBtn != null) {
                gearBtn.setVisibility(enabled ? View.VISIBLE : View.GONE);
                gearBtn.setOnClickListener(v -> {
                    android.content.Context ctx = f.getContext();
                    if (ctx != null) {
                        new RtsGestureConfigDialog(ctx).show();
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "setupSidebarRts failed", e);
        }
    }

    private static void updateGearButton(View root, String pkg, boolean rtsEnabled) {
        int gearId = root.getResources().getIdentifier("btn_rts_gesture_settings", "id", pkg);
        View gear = root.findViewById(gearId);
        if (gear != null) {
            gear.setVisibility(rtsEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private static void disableScreenTrackpadForProfile(Activity activity, Object winUiBridge) {
        try {
            // Get current profile ID from WineActivityData field 'u' -> e()
            Field uField = activity.getClass().getDeclaredField("u");
            uField.setAccessible(true);
            Object activityData = uField.get(activity);
            String profileId = null;
            if (activityData != null) {
                profileId = (String) activityData.getClass().getMethod("e").invoke(activityData);
            }
            // Call InputControlsManager.h(false, profileId) to save setting
            Class<?> icmClass = Class.forName("com.xj.pcvirtualbtn.inputcontrols.InputControlsManager");
            icmClass.getMethod("h", boolean.class, String.class).invoke(null, false, profileId);
        } catch (Exception e) {
            Log.e(TAG, "disableScreenTrackpadForProfile failed", e);
        }
    }

    private static boolean getProfileTrackpadEnabled(Activity activity) {
        try {
            Field uField = activity.getClass().getDeclaredField("u");
            uField.setAccessible(true);
            Object activityData = uField.get(activity);
            String profileId = null;
            if (activityData != null) {
                profileId = (String) activityData.getClass().getMethod("e").invoke(activityData);
            }
            Class<?> icmClass = Class.forName("com.xj.pcvirtualbtn.inputcontrols.InputControlsManager");
            return (boolean) icmClass.getMethod("B", String.class).invoke(null, profileId);
        } catch (Exception e) {
            Log.e(TAG, "getProfileTrackpadEnabled failed", e);
            return false;
        }
    }
}

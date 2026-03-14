package app.revanced.extension.gamehub;

import android.util.Log;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public final class RtsSettings {

    private static final String TAG = "BannerHub";
    private static final String KEY_RTS_ENABLED = "sp_k_enable_rts_touch_controls_global";
    private static final String KEY_GESTURE_ENABLED_PREFIX = "sp_k_rts_gesture_enabled_";
    private static final String KEY_GESTURE_ACTION_PREFIX = "sp_k_rts_gesture_action_";

    private RtsSettings() {}

    private static Object getMmkv() {
        try {
            Class<?> cls = Class.forName("com.xj.pcvirtualbtn.inputcontrols.InputControlsManager");
            return cls.getMethod("A").invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "getMmkv failed", e);
            return null;
        }
    }

    public static boolean getRtsTouchControlsEnabled() {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return false;
            return (boolean) mmkv.getClass().getMethod("c", String.class, boolean.class)
                    .invoke(mmkv, KEY_RTS_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "getRtsTouchControlsEnabled failed", e);
            return false;
        }
    }

    public static void setRtsTouchControlsEnabled(boolean enabled) {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return;
            mmkv.getClass().getMethod("y", String.class, boolean.class)
                    .invoke(mmkv, KEY_RTS_ENABLED, enabled);
        } catch (Exception e) {
            Log.e(TAG, "setRtsTouchControlsEnabled failed", e);
        }
    }

    public static boolean getRtsGestureEnabled(String gesture) {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return true;
            return (boolean) mmkv.getClass().getMethod("c", String.class, boolean.class)
                    .invoke(mmkv, KEY_GESTURE_ENABLED_PREFIX + gesture, true);
        } catch (Exception e) {
            Log.e(TAG, "getRtsGestureEnabled failed", e);
            return true;
        }
    }

    public static void setRtsGestureEnabled(String gesture, boolean enabled) {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return;
            mmkv.getClass().getMethod("y", String.class, boolean.class)
                    .invoke(mmkv, KEY_GESTURE_ENABLED_PREFIX + gesture, enabled);
        } catch (Exception e) {
            Log.e(TAG, "setRtsGestureEnabled failed", e);
        }
    }

    public static int getRtsGestureAction(String gesture) {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return 0;
            return (int) mmkv.getClass().getMethod("f", String.class, int.class)
                    .invoke(mmkv, KEY_GESTURE_ACTION_PREFIX + gesture, 0);
        } catch (Exception e) {
            Log.e(TAG, "getRtsGestureAction failed", e);
            return 0;
        }
    }

    public static void setRtsGestureAction(String gesture, int action) {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return;
            mmkv.getClass().getMethod("t", String.class, int.class)
                    .invoke(mmkv, KEY_GESTURE_ACTION_PREFIX + gesture, action);
        } catch (Exception e) {
            Log.e(TAG, "setRtsGestureAction failed", e);
        }
    }

    public static void resetRtsGestureSettings() {
        try {
            Object mmkv = getMmkv();
            if (mmkv == null) return;
            Method g = mmkv.getClass().getMethod("G", String.class);
            for (String gesture : new String[]{"TAP", "LONG_PRESS", "DOUBLE_TAP", "DRAG", "PINCH", "TWO_FINGER_DRAG"}) {
                g.invoke(mmkv, KEY_GESTURE_ENABLED_PREFIX + gesture);
            }
            g.invoke(mmkv, KEY_GESTURE_ACTION_PREFIX + "PINCH");
            g.invoke(mmkv, KEY_GESTURE_ACTION_PREFIX + "TWO_FINGER_DRAG");
        } catch (Exception e) {
            Log.e(TAG, "resetRtsGestureSettings failed", e);
        }
    }
}

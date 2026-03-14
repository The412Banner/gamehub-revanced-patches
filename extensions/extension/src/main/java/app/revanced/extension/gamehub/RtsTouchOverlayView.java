package app.revanced.extension.gamehub;

import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;
import android.view.View;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class RtsTouchOverlayView extends View {

    private static final String TAG = "BannerHub";

    // WinUIBridge instance (com.winemu.openapi.WinUIBridge)
    public Object winUiBridge;
    // Reference to InputControlsView for button-hit forwarding
    public View buttonsView;

    // Single-finger tracking
    private float lastTouchX, lastTouchY, lastGameX, lastGameY;
    private float startX, startY;
    private boolean tracking, dragging;
    private long downTime;

    // Two-finger pan/pinch
    private boolean twoFingerPanning, pinching;
    private float twoFingerStartX, twoFingerStartY, twoFingerLastX, twoFingerLastY;
    private boolean panLeft, panRight, panUp, panDown;
    private float initialPinchDistance, lastPinchDistance, accumulatedZoom;

    // Double-tap detection
    private long lastTapTime;
    private float lastTapX, lastTapY;
    private boolean doubleTapCandidate, doubleTapDelivered;

    // Button-forwarding state
    private boolean forwardingButtons;

    public RtsTouchOverlayView(Context context) {
        super(context);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!RtsSettings.getRtsTouchControlsEnabled()) return false;

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        // If forwarding to GameHub buttons, keep passing through
        if (forwardingButtons) {
            if (buttonsView != null) buttonsView.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_UP) {
                forwardingButtons = false;
            }
            return true;
        }

        // On DOWN events, check if touch hits a GameHub button
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (buttonsView != null && hitTestControlElement(event)) {
                forwardingButtons = true;
                buttonsView.dispatchTouchEvent(event);
                return true;
            }
        }

        // Two-finger handling
        if (pointerCount >= 2) {
            return handleTwoFinger(event, action);
        }

        // Transition from two-finger back to single
        if (twoFingerPanning || pinching) {
            endTwoFingerGesture();
            return true;
        }

        return handleSingleFinger(event, action);
    }

    private boolean hitTestControlElement(MotionEvent event) {
        try {
            int idx = event.getActionIndex();
            float x = event.getX(idx);
            float y = event.getY(idx);
            Method w = buttonsView.getClass().getMethod("w", float.class, float.class);
            return w.invoke(buttonsView, x, y) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean handleTwoFinger(MotionEvent event, int action) {
        float cx = (event.getX(0) + event.getX(1)) / 2f;
        float cy = (event.getY(0) + event.getY(1)) / 2f;

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // Cancel single-finger tracking
            releaseLeftButton();
            tracking = false;
            dragging = false;

            // Calculate initial finger distance
            float dist = fingerDistance(event);

            // Start two-finger pan
            twoFingerPanning = true;
            twoFingerStartX = cx; twoFingerStartY = cy;
            twoFingerLastX = cx; twoFingerLastY = cy;
            panLeft = panRight = panUp = panDown = false;

            // If action=0 (middle mouse), press it now
            if (RtsSettings.getRtsGestureAction("TWO_FINGER_DRAG") == 0) {
                pressMiddleButton();
            }

            initialPinchDistance = dist;
            lastPinchDistance = dist;
            pinching = false;
            accumulatedZoom = 0f;
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (!twoFingerPanning && !pinching) return true;

            float dist = fingerDistance(event);

            if (!pinching) {
                float change = Math.abs(dist - initialPinchDistance);
                if (change >= 50f) {
                    // Switch to pinch mode
                    pinching = true;
                    twoFingerPanning = false;
                    endTwoFingerPan();
                    releaseMiddleButton();
                    releaseLeftButton();
                    lastPinchDistance = dist;
                    return true;
                }
                // Do pan
                doPanning(cx, cy);
            } else {
                // Already pinching
                if (RtsSettings.getRtsGestureEnabled("PINCH")) {
                    float delta = dist - lastPinchDistance;
                    accumulatedZoom += delta;
                    int pinchAction = RtsSettings.getRtsGestureAction("PINCH");
                    float threshold = 5f;
                    while (Math.abs(accumulatedZoom) >= threshold) {
                        int dir = accumulatedZoom > 0 ? 1 : -1;
                        for (int i = 0; i < 5; i++) {
                            switch (pinchAction) {
                                case 1: sendPlusMinusKey(dir); break;
                                case 2: sendPageUpDownKey(dir); break;
                                default: sendScrollWheel(dir); break;
                            }
                        }
                        accumulatedZoom -= dir * threshold;
                    }
                }
                lastPinchDistance = dist;
            }
            twoFingerLastX = cx; twoFingerLastY = cy;
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            int panAction = RtsSettings.getRtsGestureAction("TWO_FINGER_DRAG");
            if (panAction == 0) releaseMiddleButton();
            else if (panAction == 1) endWasdPan();
            else endTwoFingerPan();
            releaseLeftButton();
            pinching = false;
            accumulatedZoom = 0f;
            twoFingerPanning = false;
            panLeft = panRight = panUp = panDown = false;
            return true;
        }

        return true;
    }

    private void doPanning(float cx, float cy) {
        if (!RtsSettings.getRtsGestureEnabled("TWO_FINGER_DRAG")) {
            twoFingerLastX = cx; twoFingerLastY = cy;
            return;
        }
        int panAction = RtsSettings.getRtsGestureAction("TWO_FINGER_DRAG");
        float dx = cx - twoFingerLastX;
        float dy = cy - twoFingerLastY;

        if (panAction == 0) {
            // Middle mouse drag — relative move
            moveCursorBy(dx * -0.25f, dy * -0.25f);
        } else if (panAction == 1) {
            // WASD keys
            float dxStart = cx - twoFingerStartX;
            float dyStart = cy - twoFingerStartY;
            updateWasdFromDelta(dxStart, dyStart);
        } else {
            // Arrow keys
            float dxStart = cx - twoFingerStartX;
            float dyStart = cy - twoFingerStartY;
            updateArrowFromDelta(dxStart, dyStart);
        }
        twoFingerLastX = cx; twoFingerLastY = cy;
    }

    private void updateWasdFromDelta(float dx, float dy) {
        float T = 50f;
        // Horizontal
        if (dx < -T) {
            if (!panLeft) { panLeft = true; pressWasdKey(1); if (panRight) { panRight = false; releaseWasdKey(2); } }
        } else if (dx > T) {
            if (!panRight) { panRight = true; pressWasdKey(2); if (panLeft) { panLeft = false; releaseWasdKey(1); } }
        } else {
            if (panLeft) { panLeft = false; releaseWasdKey(1); }
            if (panRight) { panRight = false; releaseWasdKey(2); }
        }
        // Vertical
        if (dy < -T) {
            if (!panUp) { panUp = true; pressWasdKey(3); if (panDown) { panDown = false; releaseWasdKey(4); } }
        } else if (dy > T) {
            if (!panDown) { panDown = true; pressWasdKey(4); if (panUp) { panUp = false; releaseWasdKey(3); } }
        } else {
            if (panUp) { panUp = false; releaseWasdKey(3); }
            if (panDown) { panDown = false; releaseWasdKey(4); }
        }
    }

    private void updateArrowFromDelta(float dx, float dy) {
        float T = 50f;
        if (dx < -T) {
            if (!panLeft) { panLeft = true; pressArrowKey(1); if (panRight) { panRight = false; releaseArrowKey(2); } }
        } else if (dx > T) {
            if (!panRight) { panRight = true; pressArrowKey(2); if (panLeft) { panLeft = false; releaseArrowKey(1); } }
        } else {
            if (panLeft) { panLeft = false; releaseArrowKey(1); }
            if (panRight) { panRight = false; releaseArrowKey(2); }
        }
        if (dy < -T) {
            if (!panUp) { panUp = true; pressArrowKey(3); if (panDown) { panDown = false; releaseArrowKey(4); } }
        } else if (dy > T) {
            if (!panDown) { panDown = true; pressArrowKey(4); if (panUp) { panUp = false; releaseArrowKey(3); } }
        } else {
            if (panUp) { panUp = false; releaseArrowKey(3); }
            if (panDown) { panDown = false; releaseArrowKey(4); }
        }
    }

    private void endTwoFingerGesture() {
        int panAction = RtsSettings.getRtsGestureAction("TWO_FINGER_DRAG");
        if (panAction == 0) releaseMiddleButton();
        else if (panAction == 1) endWasdPan();
        else endTwoFingerPan();
        pinching = false;
        accumulatedZoom = 0f;
        twoFingerPanning = false;
        panLeft = panRight = panUp = panDown = false;
    }

    private boolean handleSingleFinger(MotionEvent event, int action) {
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            tracking = true;
            dragging = false;
            lastTouchX = x; lastTouchY = y;
            startX = x; startY = y;
            downTime = System.currentTimeMillis();

            // Check for double-tap
            if (lastTapTime > 0) {
                long elapsed = downTime - lastTapTime;
                float distX = Math.abs(x - lastTapX);
                float distY = Math.abs(y - lastTapY);
                if (elapsed < 250 && distX < 50 && distY < 50) {
                    doubleTapCandidate = true;
                    doubleTapDelivered = true;
                } else {
                    doubleTapCandidate = false;
                    doubleTapDelivered = false;
                }
            } else {
                doubleTapCandidate = false;
                doubleTapDelivered = false;
            }

            warpCursorTo(x, y);

            if (doubleTapCandidate && doubleTapDelivered) {
                // Double-tap: emit two clicks on ACTION_DOWN
                doClick();
                doClick();
            }

        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!tracking) return true;

            float dx = Math.abs(x - lastTouchX);
            float dy = Math.abs(y - lastTouchY);
            if (dx < 5f && dy < 5f) return true;

            lastTouchX = x; lastTouchY = y;

            if (!dragging) {
                float fromStartX = Math.abs(x - startX);
                float fromStartY = Math.abs(y - startY);
                if (fromStartX >= 10f || fromStartY >= 10f) {
                    if (RtsSettings.getRtsGestureEnabled("DRAG")) {
                        dragging = true;
                        pressLeftButton();
                    }
                }
            }

            warpCursorTo(x, y);

        } else if (action == MotionEvent.ACTION_UP) {
            if (!tracking) {
                resetSingleFinger();
                return true;
            }

            if (dragging) {
                releaseLeftButton();
            } else if (doubleTapCandidate && doubleTapDelivered) {
                releaseLeftButton();
                doubleTapCandidate = false;
                doubleTapDelivered = false;
                lastTapTime = System.currentTimeMillis();
                lastTapX = x; lastTapY = y;
            } else {
                long elapsed = System.currentTimeMillis() - downTime;
                if (elapsed >= 300) {
                    // Long press -> right click
                    if (RtsSettings.getRtsGestureEnabled("LONG_PRESS")) {
                        doRightClick();
                    }
                } else {
                    // Tap -> left click
                    if (RtsSettings.getRtsGestureEnabled("TAP")) {
                        doClick();
                        lastTapTime = System.currentTimeMillis();
                        lastTapX = x; lastTapY = y;
                    }
                }
            }
            resetSingleFinger();
        }

        // Forward to buttons at end
        if (buttonsView != null) buttonsView.dispatchTouchEvent(event);
        return true;
    }

    private void resetSingleFinger() {
        tracking = false;
        dragging = false;
        doubleTapCandidate = false;
        doubleTapDelivered = false;
    }

    // ── WinUIBridge helpers ───────────────────────────────────────────────────

    private Object getX11Controller() {
        if (winUiBridge == null) return null;
        try {
            Field k = winUiBridge.getClass().getDeclaredField("k");
            k.setAccessible(true);
            return k.get(winUiBridge);
        } catch (Exception e) {
            return null;
        }
    }

    private void mouseEvent(float x, float y, int button, boolean isDown, boolean isRelative) {
        if (winUiBridge == null) return;
        try {
            winUiBridge.getClass().getMethod("f0", float.class, float.class, int.class, boolean.class, boolean.class)
                    .invoke(winUiBridge, x, y, button, isDown, isRelative);
        } catch (Exception e) {
            Log.e(TAG, "mouseEvent failed", e);
        }
    }

    private void keyEvent(int modifiers, int keyCode, boolean isDown) {
        Object x11 = getX11Controller();
        if (x11 == null) return;
        try {
            x11.getClass().getMethod("p", int.class, int.class, boolean.class)
                    .invoke(x11, modifiers, keyCode, isDown);
        } catch (Exception e) {
            Log.e(TAG, "keyEvent failed", e);
        }
    }

    public void warpCursorTo(float touchX, float touchY) {
        if (winUiBridge == null) return;
        Object x11 = getX11Controller();
        if (x11 == null) return;

        float gameX = touchX, gameY = touchY;
        try {
            Field aField = x11.getClass().getDeclaredField("a");
            aField.setAccessible(true);
            Object x11View = aField.get(x11);
            if (x11View != null) {
                Object screenSize = x11View.getClass().getMethod("getScreenSize").invoke(x11View);
                if (screenSize instanceof Point) {
                    int gw = ((Point) screenSize).x;
                    int gh = ((Point) screenSize).y;
                    float vx = ((View) x11View).getX();
                    float vy = ((View) x11View).getY();
                    float vw = ((View) x11View).getWidth();
                    float vh = ((View) x11View).getHeight();
                    if (vw > 0 && vh > 0) {
                        float rel = Math.max(0, Math.min(touchX - vx, vw));
                        float relY = Math.max(0, Math.min(touchY - vy, vh));
                        gameX = Math.max(0, Math.min(rel * gw / vw, gw));
                        gameY = Math.max(0, Math.min(relY * gh / vh, gh));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "warpCursorTo scale failed", e);
        }

        mouseEvent(gameX, gameY, 0, false, false);
    }

    public void moveCursorBy(float dx, float dy) {
        mouseEvent(dx, dy, 0, false, true);
    }

    public void doClick() {
        warpCursorTo(lastTouchX, lastTouchY);
        warpCursorTo(lastTouchX + 1f, lastTouchY + 1f);
        warpCursorTo(lastTouchX, lastTouchY);
        mouseEvent(0, 0, 1, true, true);
        mouseEvent(0, 0, 1, false, true);
    }

    public void doRightClick() {
        mouseEvent(0, 0, 3, true, true);
        mouseEvent(0, 0, 3, false, true);
    }

    public void pressLeftButton() { mouseEvent(0, 0, 1, true, true); }
    public void releaseLeftButton() { mouseEvent(0, 0, 1, false, true); }
    public void pressMiddleButton() { mouseEvent(0, 0, 2, true, true); }
    public void releaseMiddleButton() { mouseEvent(0, 0, 2, false, true); }

    public void sendScrollWheel(int direction) {
        mouseEvent(0, direction * -2f, 4, false, true);
    }

    public void sendPlusMinusKey(int direction) {
        int kc = direction > 0 ? 157 : 69; // NUMPAD_ADD or MINUS
        keyEvent(0, kc, true);
        keyEvent(0, kc, false);
    }

    public void sendPageUpDownKey(int direction) {
        int kc = direction > 0 ? 93 : 92; // PAGE_DOWN or PAGE_UP
        keyEvent(0, kc, true);
        keyEvent(0, kc, false);
    }

    // direction: 1=left(A), 2=right(D), 3=up(W), 4=down(S)
    public void pressWasdKey(int direction) {
        int[] kcs = {0, 29, 32, 51, 47}; // A, D, W, S
        if (direction >= 1 && direction <= 4) keyEvent(0, kcs[direction], true);
    }
    public void releaseWasdKey(int direction) {
        int[] kcs = {0, 29, 32, 51, 47};
        if (direction >= 1 && direction <= 4) keyEvent(0, kcs[direction], false);
    }

    // direction: 1=left, 2=right, 3=up, 4=down
    public void pressArrowKey(int direction) {
        int[] kcs = {0, 21, 22, 19, 20}; // DPAD_LEFT, RIGHT, UP, DOWN
        if (direction >= 1 && direction <= 4) keyEvent(0, kcs[direction], true);
    }
    public void releaseArrowKey(int direction) {
        int[] kcs = {0, 21, 22, 19, 20};
        if (direction >= 1 && direction <= 4) keyEvent(0, kcs[direction], false);
    }

    public void endTwoFingerPan() {
        if (panLeft) releaseArrowKey(1);
        if (panRight) releaseArrowKey(2);
        if (panUp) releaseArrowKey(3);
        if (panDown) releaseArrowKey(4);
        twoFingerPanning = false;
        panLeft = panRight = panUp = panDown = false;
    }

    public void endWasdPan() {
        if (panLeft) releaseWasdKey(1);
        if (panRight) releaseWasdKey(2);
        if (panUp) releaseWasdKey(3);
        if (panDown) releaseWasdKey(4);
        panLeft = panRight = panUp = panDown = false;
    }

    private static float fingerDistance(MotionEvent e) {
        float dx = e.getX(1) - e.getX(0);
        float dy = e.getY(1) - e.getY(0);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}

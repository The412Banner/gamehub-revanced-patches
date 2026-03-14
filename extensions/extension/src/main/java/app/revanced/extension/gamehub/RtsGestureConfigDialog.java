package app.revanced.extension.gamehub;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.util.Log;

@SuppressWarnings("unused")
public class RtsGestureConfigDialog implements CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "BannerHub";

    private final Context context;
    private Dialog dialog;
    private View dialogView;
    private boolean isInitializing = true;

    // For deferred action picker
    private String pendingKey;
    private int pendingIndex;

    public RtsGestureConfigDialog(Context context) {
        this.context = context;
    }

    public void show() {
        if (context == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        if (inflater == null) return;

        int layoutId = context.getResources().getIdentifier(
                "rts_gesture_config_dialog", "layout", context.getPackageName());
        if (layoutId == 0) return;

        dialogView = inflater.inflate(layoutId, null);

        dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(dialogView);

        setupCheckboxes();
        showActionLabels();

        // Close button
        int tvCloseId = context.getResources().getIdentifier("tvClose", "id", context.getPackageName());
        View closeBtn = dialogView.findViewById(tvCloseId);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> dialog.dismiss());

        isInitializing = false;
        dialog.show();

        // Configure window after show
        try {
            android.view.Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(0));
                w.setLayout(-1, -1);
                w.setGravity(Gravity.CENTER);
                WindowManager.LayoutParams attrs = w.getAttributes();
                if (attrs != null) {
                    attrs.gravity = Gravity.CENTER;
                    attrs.dimAmount = 0f;
                    w.setAttributes(attrs);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "window config failed", t);
        }
    }

    private int id(String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }

    private void setupCheckboxes() {
        setupCheckbox("rts_gesture_tap_checkbox", "TAP");
        setupCheckbox("rts_gesture_long_press_checkbox", "LONG_PRESS");
        setupCheckbox("rts_gesture_double_tap_checkbox", "DOUBLE_TAP");
        setupCheckbox("rts_gesture_drag_checkbox", "DRAG");
        setupCheckbox("rts_gesture_pinch_checkbox", "PINCH");
        setupCheckbox("rts_gesture_two_finger_checkbox", "TWO_FINGER_DRAG");
    }

    private void setupCheckbox(String idName, String gestureKey) {
        if (dialogView == null) return;
        View v = dialogView.findViewById(id(idName));
        if (!(v instanceof CheckBox)) return;
        CheckBox cb = (CheckBox) v;
        cb.setChecked(RtsSettings.getRtsGestureEnabled(gestureKey));
        cb.setTag(gestureKey);
        cb.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isInitializing) return;
        Object tag = buttonView.getTag();
        if (tag instanceof String) {
            RtsSettings.setRtsGestureEnabled((String) tag, isChecked);
        }
    }

    private void showActionLabels() {
        if (dialogView == null) return;

        // Pinch action label
        View pinchSpinner = dialogView.findViewById(id("rts_gesture_pinch_spinner"));
        if (pinchSpinner instanceof TextView) {
            String[] pinchLabels = {"Scroll Wheel", "+/- Keys", "Page Up/Down"};
            int action = RtsSettings.getRtsGestureAction("PINCH");
            ((TextView) pinchSpinner).setText(pinchLabels[Math.min(action, 2)]);
            pinchSpinner.setOnClickListener(v -> showActionPicker("PINCH",
                    pinchLabels, RtsSettings.getRtsGestureAction("PINCH")));
        }

        // Two-finger drag action label
        View twoFingerSpinner = dialogView.findViewById(id("rts_gesture_two_finger_spinner"));
        if (twoFingerSpinner instanceof TextView) {
            String[] twoFingerLabels = {"Middle Mouse", "WASD", "Arrow Keys"};
            int action = RtsSettings.getRtsGestureAction("TWO_FINGER_DRAG");
            ((TextView) twoFingerSpinner).setText(twoFingerLabels[Math.min(action, 2)]);
            twoFingerSpinner.setOnClickListener(v -> showActionPicker("TWO_FINGER_DRAG",
                    twoFingerLabels, RtsSettings.getRtsGestureAction("TWO_FINGER_DRAG")));
        }
    }

    private void showActionPicker(String gestureKey, String[] options, int currentAction) {
        if (context == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        int layoutId = context.getResources().getIdentifier(
                "rts_action_picker_dialog", "layout", context.getPackageName());
        if (layoutId == 0) return;

        View pickerView = inflater.inflate(layoutId, null);
        Dialog picker = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        picker.setContentView(pickerView);

        // Set option texts and check marks
        int[][] optionIds = {
            {id("rts_action_option_0"), id("rts_action_option_0_text"), id("rts_action_option_0_check")},
            {id("rts_action_option_1"), id("rts_action_option_1_text"), id("rts_action_option_1_check")},
            {id("rts_action_option_2"), id("rts_action_option_2_text"), id("rts_action_option_2_check")},
        };

        for (int i = 0; i < 3 && i < options.length; i++) {
            View row = pickerView.findViewById(optionIds[i][0]);
            TextView text = pickerView.findViewById(optionIds[i][1]);
            View check = pickerView.findViewById(optionIds[i][2]);
            if (text != null) text.setText(options[i]);
            if (check != null) check.setVisibility(i == currentAction ? View.VISIBLE : View.GONE);
            final int index = i;
            if (row != null) {
                row.setOnClickListener(v -> {
                    RtsSettings.setRtsGestureAction(gestureKey, index);
                    picker.dismiss();
                    // Refresh the spinner label in the main dialog
                    showActionLabels();
                });
            }
        }

        picker.show();
        try {
            android.view.Window w = picker.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(0));
                w.setLayout(-1, -1);
                w.setGravity(Gravity.CENTER);
            }
        } catch (Throwable t) {
            Log.e(TAG, "picker window config failed", t);
        }
    }
}

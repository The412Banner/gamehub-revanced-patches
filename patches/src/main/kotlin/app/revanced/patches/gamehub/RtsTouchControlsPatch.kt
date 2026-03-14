package app.revanced.patches.gamehub

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import org.w3c.dom.Element

// ── Fingerprints ──────────────────────────────────────────────────────────────

/**
 * Matches SidebarControlsFragment.j0() — onCreateView wiring method.
 * Injection: BEFORE return-void — call setupSidebarRts(fragment).
 */
private val sidebarControlsFragmentFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/winemu/sidebar/SidebarControlsFragment;" &&
            method.name == "j0"
    }
}

/**
 * Matches the WineActivity method that creates InputControlsManager + InputControlsView
 * and adds them to btnLayout.
 * Injection: BEFORE return-void — call setupRtsOverlay(activity).
 */
private val wineActivitySetupFingerprint = fingerprint {
    custom { method, classDef ->
        if (classDef.type != "Lcom/xj/winemu/WineActivity;") return@custom false
        val instructions = method.implementation?.instructions ?: return@custom false
        // Must contain new-instance of both InputControlsManager and InputControlsView
        var hasManager = false
        var hasView = false
        for (insn in instructions) {
            if (insn.opcode == Opcode.NEW_INSTANCE) {
                val ref = (insn as? Instruction21c)?.reference?.toString() ?: continue
                if (ref == "Lcom/xj/pcvirtualbtn/inputcontrols/InputControlsManager;") hasManager = true
                if (ref == "Lcom/xj/pcvirtualbtn/inputcontrols/InputControlsView;") hasView = true
            }
            if (hasManager && hasView) return@custom true
        }
        false
    }
}

// ── Resource patch ────────────────────────────────────────────────────────────

@Suppress("unused")
val rtsResourcePatch = resourcePatch(
    name = "RTS touch controls resources",
    description = "Adds resource IDs, strings and layout changes required by the RTS Touch Controls patch.",
) {
    compatibleWith(
        "com.xiaoji.egggame"("5.3.5"),
        "gamehub.lite"("5.3.5"),
        "com.tencent.ig"("5.3.5"),
        "com.antutu.ABenchMark"("5.3.5"),
        "com.antutu.benchmark.full"("5.3.5"),
        "com.ludashi.aibench"("5.3.5"),
        "com.ludashi.benchmark"("5.3.5"),
        "com.mihoyo.genshinimpact"("5.3.5"),
    )

    execute {
        // 1. Add RTS strings
        document("res/values/strings.xml").use { xml ->
            val resources = xml.documentElement
            fun addString(name: String, value: String) {
                val el = xml.createElement("string")
                el.setAttribute("name", name)
                el.textContent = value
                resources.appendChild(el)
            }
            addString("winemu_sidebar_rts_touch_controls", "RTS Touch Controls")
            addString("rts_gesture_settings", "RTS Gesture Settings")
            addString("rts_gesture_settings_title", "RTS Gesture Settings")
        }

        // 2. Add RTS IDs
        document("res/values/ids.xml").use { xml ->
            val resources = xml.documentElement
            fun addId(name: String) {
                val el = xml.createElement("item")
                el.setAttribute("type", "id")
                el.setAttribute("name", name)
                resources.appendChild(el)
            }
            addId("switch_rts_touch_controls")
            addId("rts_controls_container")
            addId("btn_rts_gesture_settings")
            addId("rts_gesture_tap_checkbox")
            addId("rts_gesture_long_press_checkbox")
            addId("rts_gesture_double_tap_checkbox")
            addId("rts_gesture_drag_checkbox")
            addId("rts_gesture_pinch_checkbox")
            addId("rts_gesture_pinch_spinner")
            addId("rts_gesture_two_finger_checkbox")
            addId("rts_gesture_two_finger_spinner")
            addId("rts_action_option_0")
            addId("rts_action_option_0_text")
            addId("rts_action_option_0_check")
            addId("rts_action_option_1")
            addId("rts_action_option_1_text")
            addId("rts_action_option_1_check")
            addId("rts_action_option_2")
            addId("rts_action_option_2_text")
            addId("rts_action_option_2_check")
        }

        // 3. Add CloudProgressStyle stub to styles.xml (required for aapt2 link validation)
        document("res/values/styles.xml").use { xml ->
            val resources = xml.documentElement
            val existing = resources.getElementsByTagName("style")
            var hasCloudProgressStyle = false
            for (i in 0 until existing.length) {
                val el = existing.item(i) as? Element ?: continue
                if (el.getAttribute("name") == "CloudProgressStyle") {
                    hasCloudProgressStyle = true
                    break
                }
            }
            if (!hasCloudProgressStyle) {
                val style = xml.createElement("style")
                style.setAttribute("name", "CloudProgressStyle")
                resources.appendChild(style)
            }
        }

        // 4. Modify winemu_sidebar_controls_fragment.xml — insert RTS controls row
        //    after key_cursor_speed and before gamepad_sensitivity
        document("res/layout/winemu_sidebar_controls_fragment.xml").use { xml ->
            val all = xml.getElementsByTagName("*")
            for (i in 0 until all.length) {
                val el = all.item(i) as? Element ?: continue
                if (el.getAttribute("android:id") == "@id/key_cursor_speed") {
                    val rtsContainer = xml.createElement("LinearLayout")
                    rtsContainer.setAttribute("android:orientation", "horizontal")
                    rtsContainer.setAttribute("android:id", "@id/rts_controls_container")
                    rtsContainer.setAttribute("android:layout_width", "fill_parent")
                    rtsContainer.setAttribute("android:layout_height", "@dimen/dp_56")
                    rtsContainer.setAttribute("android:gravity", "center_vertical")

                    val rtsSwitch = xml.createElement("com.xj.winemu.view.SidebarSwitchItemView")
                    rtsSwitch.setAttribute("android:id", "@id/switch_rts_touch_controls")
                    rtsSwitch.setAttribute("android:layout_width", "0dp")
                    rtsSwitch.setAttribute("android:layout_height", "@dimen/dp_56")
                    rtsSwitch.setAttribute("android:layout_weight", "1")
                    rtsSwitch.setAttribute("app:switch_title", "@string/winemu_sidebar_rts_touch_controls")
                    rtsContainer.appendChild(rtsSwitch)

                    val gearBtn = xml.createElement("ImageButton")
                    gearBtn.setAttribute("android:id", "@id/btn_rts_gesture_settings")
                    gearBtn.setAttribute("android:layout_width", "@dimen/dp_40")
                    gearBtn.setAttribute("android:layout_height", "@dimen/dp_40")
                    gearBtn.setAttribute("android:layout_marginEnd", "@dimen/dp_8")
                    gearBtn.setAttribute("android:background", "?android:attr/selectableItemBackgroundBorderless")
                    gearBtn.setAttribute("android:src", "@drawable/ic_settings")
                    gearBtn.setAttribute("android:contentDescription", "@string/rts_gesture_settings")
                    gearBtn.setAttribute("android:visibility", "gone")
                    rtsContainer.appendChild(gearBtn)

                    val next = el.nextSibling
                    if (next != null) el.parentNode.insertBefore(rtsContainer, next)
                    else el.parentNode.appendChild(rtsContainer)
                    break
                }
            }
        }

        // 5. Copy new resource files (layouts, drawables, color) from patch bundle
        copyResources("rts")
    }
}

// ── Bytecode patch ────────────────────────────────────────────────────────────

@Suppress("unused")
val rtsTouchControlsPatch = bytecodePatch(
    name = "RTS touch controls",
    description = "Adds RTS-style touch controls to the WineEmu game view: tap/drag/long-press/pinch/two-finger pan with configurable gestures.",
) {
    compatibleWith(
        "com.xiaoji.egggame"("5.3.5"),
        "gamehub.lite"("5.3.5"),
        "com.tencent.ig"("5.3.5"),
        "com.antutu.ABenchMark"("5.3.5"),
        "com.antutu.benchmark.full"("5.3.5"),
        "com.ludashi.aibench"("5.3.5"),
        "com.ludashi.benchmark"("5.3.5"),
        "com.mihoyo.genshinimpact"("5.3.5"),
    )

    dependsOn(rtsResourcePatch)

    extendWith("extensions/extension.rve")

    execute {
        // ── 1. SidebarControlsFragment.j0() — inject setupSidebarRts before return-void ──
        val j0Method = sidebarControlsFragmentFingerprint.method
        val j0ReturnIndex = j0Method.implementation!!.instructions
            .indexOfLast { it.opcode == Opcode.RETURN_VOID }
        j0Method.addInstructions(
            j0ReturnIndex,
            "invoke-static { p0 }, Lapp/revanced/extension/gamehub/RtsOverlaySetup;->setupSidebarRts(Ljava/lang/Object;)V",
        )

        // ── 2. WineActivity setup method — inject setupRtsOverlay before return-void ──
        val wineSetupMethod = wineActivitySetupFingerprint.method
        val wineReturnIndex = wineSetupMethod.implementation!!.instructions
            .indexOfLast { it.opcode == Opcode.RETURN_VOID }
        wineSetupMethod.addInstructions(
            wineReturnIndex,
            "invoke-static { p0 }, Lapp/revanced/extension/gamehub/RtsOverlaySetup;->setupRtsOverlay(Landroid/app/Activity;)V",
        )
    }
}

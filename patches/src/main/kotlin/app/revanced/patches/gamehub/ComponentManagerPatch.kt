package app.revanced.patches.gamehub

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.indexOfFirstInstruction
import app.revanced.patcher.extensions.InstructionExtensions.indexOfLastInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

// ── Fingerprints ─────────────────────────────────────────────────────────────

/**
 * Matches HomeLeftMenuDialog.u1() — builds the sidebar menu items list.
 * At the return-void point, p0 = the List<MenuItem> (reassigned from 'this' mid-method).
 */
private val menuListBuilderFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/ui/menu/HomeLeftMenuDialog;" &&
            method.name == "u1"
    }
}

/**
 * Matches HomeLeftMenuDialog.o1() — static click handler.
 * Signature: (HomeLeftMenuDialog, HomeLeftMenuDialog$MenuItem, FragmentActivity) -> Unit
 * p0=dialog, p1=MenuItem, p2=activity; ID obtained via p1.a() then moved into p0.
 */
private val menuClickHandlerFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/ui/menu/HomeLeftMenuDialog;" &&
            method.name == "o1"
    }
}

/**
 * Matches LandscapeLauncherMainActivity.initView(Bundle) — wires up the BCI launcher button.
 */
private val launcherInitViewFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/ui/main/LandscapeLauncherMainActivity;" &&
            method.name == "initView"
    }
}

// ── Resource patch ────────────────────────────────────────────────────────────

/**
 * Adds the "My Games" string rename, iv_bci_launcher resource ID,
 * BCI button ImageView to the toolbar layout, and registers ComponentManagerActivity
 * in the manifest.
 */
@Suppress("unused")
val componentManagerResourcePatch = resourcePatch(
    name = "Component manager resources",
    description = "Adds resource IDs, strings and layout changes required by the Component Manager patch.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute { context ->
        // 1. Rename "My" tab → "My Games"
        context["res/values/strings.xml"].also { xml ->
            xml.getElementsByTagName("string").let { nodes ->
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    if (el.getAttribute("name") == "llauncher_main_page_title_my") {
                        el.textContent = "My Games"
                        break
                    }
                }
            }
        }

        // 2. Add iv_bci_launcher ID
        context["res/values/ids.xml"].also { xml ->
            val resources = xml.documentElement
            val item = xml.createElement("item")
            item.setAttribute("type", "id")
            item.setAttribute("name", "iv_bci_launcher")
            resources.appendChild(item)
        }

        // 3. Insert BCI launcher ImageView after iv_search in the toolbar LinearLayout
        context["res/layout/llauncher_activity_new_launcher_main.xml"].also { xml ->
            val allElements = xml.getElementsByTagName("*")
            for (i in 0 until allElements.length) {
                val el = allElements.item(i) as? Element ?: continue
                if (el.getAttribute("android:id") == "@id/iv_search") {
                    val bciButton = xml.createElement("ImageView")
                    bciButton.setAttribute("android:id", "@id/iv_bci_launcher")
                    bciButton.setAttribute("android:padding", "@dimen/mw_3dp")
                    bciButton.setAttribute("android:layout_width", "@dimen/mw_30dp")
                    bciButton.setAttribute("android:layout_height", "@dimen/mw_30dp")
                    bciButton.setAttribute("android:src", "@drawable/ic_outline_open_in_new_24")
                    bciButton.setAttribute("android:tint", "#ffffffff")
                    bciButton.setAttribute("android:layout_marginStart", "@dimen/mw_16dp")
                    bciButton.setAttribute("android:alpha", "0.8")
                    val next = el.nextSibling
                    if (next != null) el.parentNode.insertBefore(bciButton, next)
                    else el.parentNode.appendChild(bciButton)
                    break
                }
            }
        }

        // 4. Register ComponentManagerActivity in the manifest
        context["AndroidManifest.xml"].also { xml ->
            val application = xml.getElementsByTagName("application").item(0) as? Element ?: return@also
            val activity = xml.createElement("activity")
            activity.setAttribute(
                "android:name",
                "app.revanced.extension.gamehub.ComponentManagerActivity",
            )
            activity.setAttribute("android:label", "Components")
            activity.setAttribute("android:exported", "false")
            application.appendChild(activity)
        }
    }
}

// ── Bytecode patch ────────────────────────────────────────────────────────────

/**
 * Injects calls to ComponentManagerHelper at three injection points:
 *  1. HomeLeftMenuDialog.u1()  — append Components item to the sidebar list
 *  2. HomeLeftMenuDialog.o1()  — intercept click for item ID=9 before the packed-switch
 *  3. LandscapeLauncherMainActivity.initView() — wire up the BCI toolbar button
 */
@Suppress("unused")
val componentManagerPatch = bytecodePatch(
    name = "Component manager",
    description = "Adds a Component Manager to the GameHub sidebar and a BCI launcher button to the toolbar.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    dependsOn(componentManagerResourcePatch)

    extendWith("extensions/extension.rve")

    execute {
        // ── 1. u1(): append Components item to menu list before return-void ──────
        menuListBuilderFingerprint.method.apply {
            val returnIndex = indexOfLastInstruction(Opcode.RETURN_VOID)
            addInstructions(
                returnIndex,
                """
                    invoke-static { p0 }, Lapp/revanced/extension/gamehub/ComponentManagerHelper;->addComponentsMenuItem(Ljava/util/List;)V
                """.trimIndent(),
            )
        }

        // ── 2. o1(): intercept ID=9 click at the start of the method ──────────
        // Signature: (HomeLeftMenuDialog, MenuItem, FragmentActivity) -> Unit
        // p0=dialog, p1=MenuItem, p2=activity; .locals 2 (v0, v1 available)
        menuClickHandlerFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static { p0, p1, p2 }, Lapp/revanced/extension/gamehub/ComponentManagerHelper;->handleMenuItemClick(Ljava/lang/Object;Ljava/lang/Object;Landroid/app/Activity;)Z
                    move-result v0
                    if-eqz v0, :cond_bh_not_handled
                    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
                    return-object v0
                    :cond_bh_not_handled nop
                """.trimIndent(),
            )
        }

        // ── 3. initView(): wire up BCI button before return-void ─────────────
        launcherInitViewFingerprint.method.apply {
            val returnIndex = indexOfLastInstruction(Opcode.RETURN_VOID)
            addInstructions(
                returnIndex,
                """
                    invoke-static { p0 }, Lapp/revanced/extension/gamehub/ComponentManagerHelper;->setupBciButton(Landroid/app/Activity;)V
                """.trimIndent(),
            )
        }
    }
}

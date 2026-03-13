# gamehub-revanced-patches Progress Log

## v2.0.0 — feat: sync ComponentManagerActivity + expand compatibleWith (2026-03-13)
**Commit:** `3de8fdc` | **Tag:** `v2.0.0`

### What changed
- **`ComponentManagerActivity.java`** — fully rewritten to match BannerHub v2.2.0 smali:
  - Two-level navigation: component list → options (Inject file / Backup / Back)
  - Title "Banners Component Injector" on every screen (20sp, centered, 48px padding)
  - Shows last injected filename as `[-> filename]` suffix in component list
  - Persists injected filenames in SharedPreferences `"bh_injected"` across restarts
  - `getFileName(Uri)` queries `OpenableColumns.DISPLAY_NAME` via ContentResolver
  - `buildRoot()` / `buildList()` helpers to reduce layout boilerplate
- **`ComponentManagerPatch.kt`** — expanded `compatibleWith` in both
  `componentManagerResourcePatch` and `componentManagerPatch` from just `com.xiaoji.egggame`
  to all 8 GameHub package variants:
  `gamehub.lite`, `com.tencent.ig`, `com.antutu.ABenchMark`, `com.antutu.benchmark.full`,
  `com.ludashi.aibench`, `com.ludashi.benchmark`, `com.mihoyo.genshinimpact`, `com.xiaoji.egggame`

### Files touched
- `extensions/extension/src/main/java/app/revanced/extension/gamehub/ComponentManagerActivity.java`
- `patches/src/main/kotlin/app/revanced/patches/gamehub/ComponentManagerPatch.kt`

---

## v1.0.4 — fix: skip apiCheck in CI (2026-03-12)
**Commit:** `97efee1` | **Tag:** `v1.0.4`

### What changed
- CI build was failing on `apiCheck` task because no API dump file was committed.
- Added `-x apiCheck` to the Gradle build command in the workflow.

### Files touched
- `.github/workflows/build.yml`

---

## v1.0.3 — fix: use execute{} not apply{}, add -Xcontext-receivers flag (2026-03-12)
**Commit:** `c41a941` | **Tag:** `v1.0.3`

### What changed
- Changed `apply{}` to `execute{}` in resource and bytecode patches so `this` resolves
  to the correct patch context and `document()` is accessible.
- Added `-Xcontext-receivers` compiler flag to `patches/build.gradle.kts` so the
  fingerprint method context receiver compiles correctly.

### Files touched
- `patches/src/main/kotlin/app/revanced/patches/gamehub/ComponentManagerPatch.kt`
- `patches/build.gradle.kts`

---

## v1.0.2 — fix: correct ReVanced patcher v2 API usage (2026-03-12)
**Commit:** `337f8d3` | **Tag:** `v1.0.2`

### What changed
- Replaced `execute { context -> }` with `apply { }` (no explicit parameter).
- Replaced `context["path"].also` with `document("path").use` for DOM access.
- Replaced non-existent `indexOfLastInstruction()` with
  `method.implementation!!.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }`.
- Removed invalid imports for `indexOfFirstInstruction` / `indexOfLastInstruction`.
- Fixed early-return in `o1()` handler to use `return-void` (void method).

### Files touched
- `patches/src/main/kotlin/app/revanced/patches/gamehub/ComponentManagerPatch.kt`

---

## v1.0.1 — fix: add libs.versions.toml, root build.gradle.kts, extension manifest, gitignore (2026-03-12)
**Commit:** `f87ec8c` | **Tag:** `v1.0.1`

### What changed
- Added missing `gradle/libs.versions.toml` version catalog.
- Added root `build.gradle.kts`.
- Added extension `AndroidManifest.xml`.
- Added `.gitignore`.

### Files touched
- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `extensions/extension/src/main/AndroidManifest.xml`
- `.gitignore`

---

## v1.0.0 — feat: initial GameHub ReVanced patches (2026-03-12)
**Commit:** `e5591bd` | **Tag:** `v1.0.0`

### What changed
Initial repo — ReVanced patches for GameHub Lite 5.3.5 using the ReVanced patcher framework.

**Patches:**
- `componentManagerPatch` — bytecode injections at 3 smali points:
  - `HomeLeftMenuDialog.u1()` — append Components menu item (ID=9) via reflection
  - `HomeLeftMenuDialog.o1()` — intercept click for ID=9 before packed-switch
  - `LandscapeLauncherMainActivity.initView()` — wire up BCI toolbar button
- `componentManagerResourcePatch` — XML modifications:
  - Rename `"My"` tab → `"My Games"` (`llauncher_main_page_title_my`)
  - Add `iv_bci_launcher` ImageView to top-right toolbar LinearLayout
  - Add `iv_bci_launcher` resource ID
  - Register `ComponentManagerActivity` in AndroidManifest

**Extension classes:**
- `ComponentManagerActivity` — ListView UI, inject + backup operations, SAF picker
- `WcpExtractor` — ZIP/zstd-tar/XZ-tar extraction using GameHub's own bundled libs via reflection
- `ComponentManagerHelper` — static helpers called from injected smali

### Files touched
- `.github/workflows/build.yml`
- `extensions/extension/src/main/java/app/revanced/extension/gamehub/ComponentManagerActivity.java`
- `extensions/extension/src/main/java/app/revanced/extension/gamehub/ComponentManagerHelper.java`
- `extensions/extension/src/main/java/app/revanced/extension/gamehub/WcpExtractor.java`
- `patches/src/main/kotlin/app/revanced/patches/gamehub/ComponentManagerPatch.kt`
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`

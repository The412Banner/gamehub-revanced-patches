# GameHub ReVanced Patches

ReVanced patches for **GameHub 5.3.5** (`com.xiaoji.egggame`).

## Patches

### `component-manager` (+ `component-manager-resources`)

Adds a Component Manager and BCI launcher button to GameHub.

#### Features
- **Components** entry in the GameHub left sidebar → launches `ComponentManagerActivity`
  - Lists all installed components from `files/usr/home/components/`
  - **Inject** WCP or ZIP file via file picker — supports zstd tar, XZ tar, and flat ZIP
  - **Backup** component folder to `Downloads/BannerHub/{name}/`
- **BCI launcher button** in the top-right toolbar → opens BannersComponentInjector (`com.banner.inject`), or shows a toast if not installed
- **"My Games"** tab label (renamed from "My")

## How it works

Patches inject calls to extension helper classes at three points in the original bytecode:

| Injection point | Class | Method | What it does |
|---|---|---|---|
| `HomeLeftMenuDialog.u1()` | menu list builder | before `return-void` | Appends "Components" item (ID=9) via reflection |
| `HomeLeftMenuDialog.o1()` | click handler | before packed-switch | Intercepts ID=9, launches `ComponentManagerActivity` |
| `LandscapeLauncherMainActivity.initView()` | launcher | before `return-void` | Wires up BCI toolbar button click listener |

Resource changes: adds `iv_bci_launcher` ID, renames the "My" string, inserts the toolbar ImageView, registers the new Activity in the manifest.

## Building

Requires a GitHub token with `read:packages` scope to access the ReVanced Gradle plugin registry.

```bash
GITHUB_TOKEN=<token> GITHUB_ACTOR=<username> gradle build
```

Outputs:
- `patches/build/*.rvp` — patches bundle (load with ReVanced CLI or Manager)
- `extensions/extension/build/*.rve` — compiled extension dex

## Applying with ReVanced CLI

```bash
java -jar revanced-cli.jar patch \
  --patches patches/build/gamehub-revanced-patches-*.rvp \
  --merge extensions/extension/build/extension.rve \
  GameHub-5.3.5.apk
```

## Notes

- Built and tested against GameHub 5.3.5 ReVanced Normal (`com.xiaoji.egggame`)
- Uses GameHub's own bundled libraries (commons-compress, zstd-jni, tukaani xz) via reflection — no external dex injection needed
- `TarArchiveInputStream.getNextTarEntry()` is called as `s()` (R8-obfuscated name verified from decompiled APK)

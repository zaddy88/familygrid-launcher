# FamilyGrid Launcher

*Made for parents and grandparents.*

FamilyGrid is a simple Android home screen. It has big, colourful tiles for the few apps and people that matter, and it always shows whether the Wi-Fi is actually working.

## Features

- **Big tiles** in a 3-column grid that fills the screen. Long names shrink to fit instead of being cut off.
- **Person tiles (speed dial).** Add a family member from your contacts or type in a name and number. One tap calls them, or opens the keypad with the number filled in if you prefer.
- **Status chips.** You can turn each one on or off:
  - **Wi-Fi.** "Working" (connected and internet verified), "No internet" (connected but internet not verified) or "Off". Tap it to open Wi-Fi settings.
  - **Battery.** Shows the level and charging state, and warns when the battery is low.
  - **Bluetooth.** Shows the name of the connected headset, speaker or car (off by default).
- **"All apps" button.** Opens a large, plain list of every installed app, with a clearly labelled **‹ Back** button.
- **Big Back / Home / Apps buttons (optional).** An accessibility service draws a large labelled bar over the small system navigation icons, and it appears in every app. You switch it on under *Settings → Accessibility → FamilyGrid big buttons*; the editor has a shortcut to that page. The labels can be renamed in any language from the editor ("Rename the buttons"). The service only draws buttons and runs Back/Home. It cannot read what is on the screen (`canRetrieveWindowContent="false"`).
- **First-run setup.** On first launch you choose the tiles from the apps on this phone, pick a text size, and choose which chips to show.
- **Languages:** English, اردو (Urdu), العربية (Arabic), हिन्दी (Hindi), বাংলা (Bengali) and Español. You choose the language in the app, separately from the phone's own language. Urdu and Arabic switch the layout to right-to-left. Built-in tile names such as "Phone" and "Camera" follow the language, but names the family typed are left alone.
- **Text size.** Normal, Large or Extra large.
- **Hidden editor.** Hold the clock for 2 seconds. In the editor you can add, remove, reorder, rename and recolour tiles. You can protect it with a PIN.
- The Back button does nothing on the home screen, so nothing surprising happens.

## Build (no Gradle)

Requirements: Android SDK (`build-tools/34.0.0`, `platforms/android-34`) and JDK 17.
Edit the paths at the top of `build.ps1`, then run:

```powershell
./build.ps1
```

The script runs `aapt2 link → javac → d8 → jar → zipalign → apksigner` and writes `out/FamilyGrid.apk`.

### Install on a phone

```bash
adb install -r out/FamilyGrid.apk
```

Then press Home and choose **FamilyGrid Launcher → Always**. You can also do it with adb:

```bash
adb shell cmd package set-home-activity dev.fluxnova.familygrid/.MainActivity
```

## Code layout (3 tiers)

| File | Tier | Role |
|---|---|---|
| `MainActivity.java` | UI | Home screen: clock, status chips, tile grid |
| `SettingsActivity.java` | UI | Tile editor, settings and first-run setup |
| `AppsActivity.java` | UI | Large "All apps" list |
| `NavBarService.java` | UI | Optional accessibility overlay: big Back / Home / Apps bar |
| `HubActions.java` | Logic | Launching apps and calls; Wi-Fi, battery and Bluetooth state |
| `HubConfig.java` | Data | `tiles.json` (tiles), SharedPreferences (PIN, text size, toggles) |

Tiles are stored in `files/tiles.json` (version 2). Version 1 files are still read.

## Adding a language

1. Copy `res/values/strings.xml` to `res/values-<code>/strings.xml` (for example `values-tr` for Turkish) and translate the text. Leave the `name="..."` keys unchanged, and keep `%1$s`, `%1$d`, `%%` and `\n`.
2. Add the code and the language's own name to `LANGUAGE_TAGS` and `LANGUAGE_NAMES` in `HubConfig.java`.
3. Rebuild. Native speakers should review the translations, which started as machine drafts.

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_NETWORK_STATE` | Wi-Fi / internet status |
| `CALL_PHONE` (optional) | Person tiles call straight away. If it is denied, they open the keypad instead. |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` (optional) | Name of the connected Bluetooth device |

## License

Apache License 2.0. See [LICENSE](LICENSE).

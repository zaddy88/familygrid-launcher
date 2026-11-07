# 🤖 Clean up and modernize an Android phone with an AI coding agent

You can let an AI coding agent drive your terminal and clean up a parent's or grandparent's phone:
remove bloatware, stop pop-ups and junk notifications, then install FamilyGrid. It works over USB with
**ADB**. No root, no unlocked bootloader, no Android Studio, and **every step can be undone**.

> **Any agent that can run terminal commands works**, for example **Claude Code**, **OpenAI Codex CLI**,
> **Cursor**, **Gemini CLI**, **Aider** or **Warp**.
> I did it with **Warp + GLM** on a TECNO POVA (Android 10, HiOS 7): about 50 junk apps removed in
> one session, then this launcher was built and installed. The prompts below are written to work in
> any of these tools.

---

## What you need

| | |
|---|---|
| A computer (Windows, macOS or Linux) | with your AI agent installed |
| [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) | gives you `adb` |
| A USB cable | data-capable, not charge-only |
| The phone | with **USB debugging** switched on (below) |

**Switch on USB debugging:** Settings → About phone → tap **Build number** 7 times → back to Settings →
System → **Developer options** → **USB debugging** on. Plug in the phone and tap **Allow** on the
"Allow USB debugging?" prompt.

> **Safety rules for you and the agent**
> - Removing apps with `pm uninstall --user 0` **does not delete them from the phone**. It hides them for
>   the current user, and `cmd package install-existing <package>` brings them back.
> - Never remove the phone (dialer), Contacts, Messages, Settings, the keyboard, Google Play services,
>   or anything the agent isn't sure about.
> - Keep a **restore script** for every change.
> - Always review the list before anything is removed.

---

## Step-by-step prompts

Copy each prompt into your agent **one at a time**, read what it reports, and only then move on.

### Prompt 1: Connect and look around (read-only)

```text
I want to clean up an Android phone for an elderly relative using ADB. Do not change anything yet.

1. Run `adb devices` and confirm exactly one phone is connected and authorized.
2. Show me the model, brand and Android version (adb shell getprop ro.product.model,
   ro.product.brand, ro.build.version.release).
3. List all packages with `adb shell pm list packages -f` and also the third-party ones with
   `adb shell pm list packages -3`.
4. Save everything to a file called phone-inventory.txt in this folder.
Report back with a short summary.
```

### Prompt 2: Sort the apps into keep / remove

```text
Using phone-inventory.txt, sort every package into these groups and show me a table
(package, what it is in plain words, group):

- KEEP – SYSTEM: anything the phone needs (com.android.*, dialer, contacts, messages, settings,
  keyboard, launcher, camera, Google Play services and Play Store, carrier/SIM apps). Never touch these.
- KEEP – USED: apps the family actually uses (ask me if unsure, e.g. WhatsApp, YouTube, banking).
- REMOVE – JUNK: ad services, lock-screen "magazines", telemetry, game centres, app stores other
  than Play, "cleaners/boosters", duplicate browsers, preinstalled promo apps.
- ASK ME: anything you are not sure about.

Do not remove anything. Wait for me to approve the REMOVE list.
```

### Prompt 3: Disable first (trial run, reversible)

```text
For the packages I approved, create two scripts in this folder:

1. disable-junk script: one line per package
   `adb shell pm disable-user --user 0 <package>`  # comment: what it is
2. restore-all script: one line per package
   `adb shell pm enable <package>` and `adb shell cmd package install-existing <package>`

Use .ps1 on Windows or .sh on macOS/Linux. Show me both scripts, then run the disable script
and report any errors. Then tell me to use the phone normally for a day before continuing.
```

### Prompt 4: Remove for good (still reversible)

```text
The phone has worked fine with the junk disabled. Now create uninstall-junk with
`adb shell pm uninstall --user 0 <package>` for the same packages, keep restore-all up to date,
run it, and then list the remaining third-party packages so I can check the result.
```

### Prompt 5: Stop pop-ups that appear over other apps

```text
Find apps that are allowed to "display over other apps" (these cause full-screen pop-ups and ads):
run `adb shell appops query-op SYSTEM_ALERT_WINDOW allow` (or, if that isn't supported, check
`adb shell dumpsys appops` for SYSTEM_ALERT_WINDOW). Show me the list with a plain description of each.

For the ones I approve, run `adb shell appops set <package> SYSTEM_ALERT_WINDOW deny`
and add the undo line `adb shell appops set <package> SYSTEM_ALERT_WINDOW default`
to restore-all. Never deny it for accessibility tools, the keyboard or the phone/dialer.
```

### Prompt 6: Quiet the notifications

```text
List which apps posted notifications recently with `adb shell dumpsys notification --noredact`
(only report package names and how many, not the message text).
For apps I don't need alerts from, explain how to turn off their notifications in
Settings → Apps → <app> → Notifications.
Then tell me how to install FamilyGrid and switch on its "Quiet mode", which hides
notifications from apps that aren't on the home screen.
```

### Prompt 7: Install FamilyGrid (the big-tile home screen)

```text
Download the latest FamilyGrid APK from
https://github.com/zaddy88/familygrid-launcher/releases/latest/download/FamilyGrid.apk
and install it with `adb install -r FamilyGrid.apk`.
Then make it the default home screen:
`adb shell cmd package set-home-activity dev.fluxnova.familygrid/.MainActivity`
Press Home (`adb shell input keyevent KEYCODE_HOME`), take a screenshot with
`adb exec-out screencap -p > home.png` and show me.
After that, tell me to open "FamilyGrid Settings" on the phone and follow the Setup check.
```

### Prompt 8: Check everything (read-only)

```text
Verify the phone is healthy:
- the dialer, Contacts, Messages, Camera, WhatsApp and Play Store still open
  (use `adb shell monkey -p <package> -c android.intent.category.LAUNCHER 1`),
- FamilyGrid is the home app,
- no crashes: `adb logcat -d -b crash`.
Report anything unusual. Also confirm restore-all is saved somewhere safe.
```

---

## Undo everything

Run your **restore-all** script, or for a single app:

```bash
adb shell cmd package install-existing <package>
adb shell pm enable <package>
adb shell appops set <package> SYSTEM_ALERT_WINDOW default
```

To go back to the phone's original home screen, open **Settings → Apps → Default apps → Home app**
(or FamilyGrid Settings → Setup check), and pick the original launcher.

## Bonus: build FamilyGrid from source with an agent

This repository is set up so an agent can build it without Android Studio or Gradle:

```text
Clone https://github.com/zaddy88/familygrid-launcher, read README.md, install JDK 17 and the
Android SDK build-tools 34 + platform 34, update the paths at the top of build.ps1, run it,
fix any errors, install out/FamilyGrid.apk with adb and take a screenshot of the home screen.
```

---

*If this helped your family, please ⭐ star the repo and share what you changed. Credit the tools you used!*

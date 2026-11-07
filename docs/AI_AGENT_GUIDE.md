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

Cross-check before you suggest anything. Do NOT rely on package names alone:
- Download the community-maintained package list from Universal Android Debloater Next Generation
  (GitHub: Universal-Debloater-Alliance/universal-android-debloater-next-generation, file uad_lists.json).
- For every package, add a column with its UAD-ng safety rating (Recommended / Advanced / Expert /
  Unsafe) and description, or "not listed".
- Only suggest REMOVE for packages rated "Recommended" (or "Advanced" with a clear reason).
  Anything "Expert", "Unsafe" or "not listed" goes to ASK ME, even if the name looks like junk.

Do not remove anything. Wait for me to approve the REMOVE list.
```

> **Why the cross-check matters:** a name like `com.transsion.xyz` can be an ad service *or* part of the
> phone's dialer. The UAD-ng list is reviewed by its community per manufacturer, and you approve every package yourself,
> so the agent's guess is never the only check.

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
Also save the final list of removed packages to removed-packages.txt (one per line) and record the
phone's build fingerprint (`adb shell getprop ro.build.fingerprint`) in it, so we can tell
after a system update whether anything came back.
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

### Prompt 9: After a system update (re-check, don't guess)

System updates can bring removed apps back or switch them on again. Keep removed-packages.txt and
restore-all with your notes, then after any update:

```text
The phone just had a system update. Compare the current build fingerprint with the one in
removed-packages.txt. Then, for every package in removed-packages.txt, check whether it is installed
for user 0 again (`adb shell pm list packages <package>`) or enabled.
Show me ONLY the ones that came back, re-check each against the UAD-ng list (the update may have
changed what a package does), and wait for my approval before removing them again with the same
reversible commands. Also confirm FamilyGrid is still the default home app.
```

### Prompt 10: Hand the phone back safely (lock down ADB)

ADB gives your computer far more control over the phone than any normal app has. When everything
works, remove that access:

```text
We are finished. Help me hand the phone back safely:
1. Confirm restore-all and removed-packages.txt are saved on this computer (not on the phone).
2. Run `adb kill-server` on this computer.
3. Then tell me, step by step, to do this ON THE PHONE (you cannot do it over ADB):
   Settings › System › Developer options › "Revoke USB debugging authorisations",
   then switch "USB debugging" off, then switch "Developer options" off at the top.
4. Finally run `adb devices` and confirm the phone is no longer listed.
```

FamilyGrid's **Setup check** also shows **"USB debugging is off" (Recommended)** and keeps nagging
until it is, so a phone isn't handed over with ADB still open.

---

## ✅ Handover checklist

- [ ] Phone works: calls, WhatsApp, camera, messages, Play Store
- [ ] FamilyGrid is the home app; Setup check is all ✓
- [ ] `restore-all` and `removed-packages.txt` saved on **your** computer
- [ ] **Revoke USB debugging authorisations** (Developer options)
- [ ] **USB debugging off** and **Developer options off**
- [ ] A FamilyGrid **PIN** set, so tiles aren't changed by accident
- [ ] Re-check after the next system update (Prompt 9)

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

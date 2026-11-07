# Publishing FamilyGrid on Google Play

A step-by-step checklist for a first release. Google's rules change every year, so check
the linked Google pages before each step. This guide was written in November 2026.

> FamilyGrid is an Android launcher, so it ships on **Google Play** (and optionally
> GitHub Releases / F-Droid). Apple's App Store is iPhone-only and can't run Android launchers.

---

## Overview

| # | Step | Who | Time |
|---|------|-----|------|
| 1 | Make the app Play-ready (target API 36, release key, AAB) | code | 1–2 days |
| 2 | Create a Play Console developer account | you | ~1 hour plus 1–3 days for ID checks |
| 3 | Prepare the store listing (text, icon, screenshots, privacy policy) | you + code | half a day |
| 4 | Fill in the app content forms (Data safety, Accessibility, rating…) | you | 1 hour |
| 5 | **Closed test: 12 testers for 14 days in a row** (new personal accounts) | you + testers | 2+ weeks |
| 6 | Apply for production access, then release | you | a few days of review |

---

## 1. Make the app Play-ready (code work)

1. **Target Android 16 (API 36).** Since **31 Aug 2026**, new phone apps must target API 36
   ([Google: target API requirements](https://developer.android.com/google/play/requirements/target-sdk)).
   FamilyGrid targets 34 today.
   - Install the new SDK parts: `sdkmanager "platforms;android-36" "build-tools;36.0.0"`.
   - Update `build.ps1` (paths) and `AndroidManifest.xml` (`targetSdkVersion="36"`).
   - **Edge-to-edge is forced** from API 35. The settings and All apps screens must leave space
     for the status bar and navigation bar (window insets), or their content goes under them.
     Test this on a phone with Android 15 or 16.
2. **Release signing key.** Create it once and **back it up somewhere safe**. If you lose it,
   you can't update the app (Play App Signing can reset only the *upload* key).
   ```
   keytool -genkeypair -v -keystore familygrid-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   Keep the password out of git: have `build.ps1` read it from an environment variable.
3. **Android App Bundle (.aab).** Play only accepts bundles for new apps. The build needs a
   bundle step: `aapt2 link --proto-format`, a zip in the bundle module layout, then
   [`bundletool build-bundle`](https://developer.android.com/tools/bundletool), then `jarsigner` with the upload key.
4. **Version numbers.** Raise `versionCode` (a whole number) for **every** upload. `versionName` is the
   version people see (for example `1.0.0`).
5. **Package name is permanent.** `dev.fluxnova.familygrid` can never change after the first upload.

## 2. Developer account

1. Go to [play.google.com/console](https://play.google.com/console) and sign up. There's a **one-time US$25 fee**.
2. Choose **Personal** (just you) or **Organization** (needs a D-U-N-S number, which can take weeks to get).
   A personal account is fine, but it adds the testing rule in step 5.
3. Complete identity verification (government ID) and phone and email verification.

## 3. Store listing

| Item | Limit / size | Suggested |
|---|---|---|
| App name | 30 chars | `FamilyGrid – Simple Launcher` |
| Short description | 80 chars | `Big tiles, clear Wi-Fi status and easy buttons for parents and grandparents.` |
| Full description | 4000 chars | see draft below |
| App icon | 512×512 PNG | export from `res/drawable/ic_launcher.xml` |
| Feature graphic | 1024×500 PNG/JPG | tiles + tagline "Made for parents and grandparents" |
| Phone screenshots | 2–8, 16:9 or 9:16 | home (3 per row), Urdu home, settings checklist, big buttons |
| Category | — | **Personalization** |
| Contact email | required | your support email |
| **Privacy policy URL** | required | host `docs/PRIVACY.md` with GitHub Pages |

**Full description (draft):**

> FamilyGrid turns any Android phone into a calm, simple home screen for parents and grandparents.
>
> • Big, colourful tiles for the apps they use: WhatsApp, YouTube, Camera and more
> • One-tap speed-dial tiles for family members
> • Always-visible Wi-Fi status that shows whether the internet is *really* working
> • Battery and Bluetooth status at a glance
> • Optional large, labelled Back / Home / Apps buttons
> • 2, 3 or 4 tiles per row and three text sizes
> • English, Urdu, Arabic, Hindi, Bengali and Spanish, with right-to-left support
> • A PIN lock so tiles aren't changed by accident
> • No ads, no tracking, no account. Everything stays on the phone.
>
> Set it up for a loved one in two minutes, then press Home and choose FamilyGrid.

## 4. App content forms (Play Console → Policy → App content)

- **Privacy policy:** the URL from step 3.
- **Data safety:** FamilyGrid **collects and shares no data**. Contacts and tiles stay on the device.
  Answer "No" to collection. A contact the user picks is only read to save a name and number on the phone.
- **Accessibility API declaration:** required because of `NavBarService`. Launchers **can't** declare
  `isAccessibilityTool` ([Google policy](https://support.google.com/googleplay/android-developer/answer/10964491)),
  so Play requires:
  - an **in-app disclosure plus "Agree"** before the user switches it on. This is done: see the "About the big buttons" dialog;
  - a short **video** (screen recording) showing the disclosure, switching the service on, and the buttons working;
  - a description: *"Draws large labelled Back/Home/Apps buttons over the navigation bar for elderly users; performs only GLOBAL_ACTION_BACK/HOME; canRetrieveWindowContent=false; reads no screen content."*
- **Permissions:** `CALL_PHONE` is allowed (no special form). FamilyGrid doesn't use call-log or SMS permissions.
- **Content rating:** fill in the questionnaire. Expected result: Everyone / PEGI 3.
- **Target audience:** 18+ (it's set up by adults for older relatives; **not** for children).
- **Ads:** "No ads".

## 5. Closed testing (new personal accounts)

Personal accounts created after Nov 2023 must run a **closed test with at least 12 testers who stay
opted in for 14 days in a row** before they can publish to production
([Google help](https://support.google.com/googleplay/android-developer/answer/14151465)).

1. Play Console → Testing → **Closed testing** → create a track and upload the `.aab`.
2. Add testers by Google account email (family and friends are ideal for this app).
3. Share the opt-in link. Each tester must **accept the invite and install the app from Play**.
4. Keep at least 12 opted in for 14 days in a row. If someone leaves, the count can restart, so invite 15–20.
5. Collect feedback; publish fixes to the same track as needed.

## 6. Production

1. Apply for production access (Play asks a few questions about your test).
2. Create a production release with the same or a newer `.aab`, and write release notes.
3. Choose countries and send for review (usually a few days; the first review can be longer).

---

## Also: GitHub Releases (no review, no fee)

Attach the signed `.apk` to a GitHub Release for people who sideload. Use the **same release key**
so GitHub and Play builds can update each other. (Play re-signs builds with its own app signing key
unless you upload your key, so for now treat GitHub and Play installs as separate.)

## Donations

Don't put donation or "Buy me a coffee" links **inside the app** on Play: outside the US, Google has
removed apps for this (WireGuard, Language Transfer and others). Put them on the GitHub page and
your website instead.

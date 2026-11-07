# Google Play upload kit (FamilyGrid 0.5.0)

Everything you need to publish FamilyGrid, in order. Steps marked **🧑 you** must be done by you
in your own browser (Google requires the account owner to do them). Everything else is ready in this repo.

Background and policy details: [PUBLISHING.md](PUBLISHING.md).

---

## Step 1: Create your upload key (🧑 you, once, about 2 minutes)

In a terminal in this folder:

```powershell
powershell -ExecutionPolicy Bypass -File tools\new-release-key.ps1
```

Choose a strong password and save it in a password manager. Then **back up**
`E:\Learning\android-modernization\familygrid-release\familygrid-upload.jks` (USB stick + cloud).

## Step 2: Build the bundle (🧑 you, about 1 minute)

```powershell
.\build-aab.ps1
```

It asks for the key password (hidden) and creates **`out\FamilyGrid.aab`**. That's the file you upload.

## Step 3: Developer account (🧑 you)

1. Go to <https://play.google.com/console/signup>, sign in with the Google account you want to own the app.
2. Choose **Personal** (just you), pay the **one-time US$25**, verify your identity (ID) and phone.
3. Wait for verification (usually 1–3 days).

## Step 4: Create the app (🧑 you)

Play Console → **Create app**:

| Field | Value |
|---|---|
| App name | `FamilyGrid – Simple Launcher` |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free |
| Declarations | tick both (developer policies, US export laws) |

## Step 5: Store listing (Grow → Store presence → Main store listing)

**Short description** (80 max):
```
Big tiles, clear Wi-Fi status and easy buttons for parents and grandparents.
```

**Full description:**
```
FamilyGrid turns any Android phone into a calm, simple home screen for parents and grandparents.

• Big, colourful tiles for the apps they really use: WhatsApp, YouTube, Camera, Photos and more
• One-tap speed-dial tiles for family members
• Wi-Fi status you can trust: shows whether the internet is really working
• Battery and Bluetooth status at a glance
• Optional large, labelled Back / Home / Apps buttons in every app
• Quiet mode: hides junk notifications from apps that aren't on the home screen
• 2, 3 or 4 tiles per row and three text sizes
• English, Urdu, Arabic, Hindi, Bengali and Spanish, with right-to-left support
• Guided setup with a checklist, and a PIN lock so tiles aren't changed by accident
• Free and open source. No ads, no tracking, no account. Everything stays on the phone.

Set it up for a loved one in two minutes, then press Home and choose FamilyGrid.
```

**Graphics** (all in [`docs/play/`](play/)):

| Play field | File |
|---|---|
| App icon (512×512) | `icon-512.png` |
| Feature graphic (1024×500) | `feature-graphic-1024x500.png` |
| Phone screenshots (2–8) | `phone-1-home.png` … `phone-7-quiet-mode.png` |

**Category:** Personalization · **Contact email:** your support email ·
**Website:** `https://github.com/zaddy88/familygrid-launcher`

## Step 6: App content (Policy → App content)

| Form | Answer |
|---|---|
| **Privacy policy** | `https://github.com/zaddy88/familygrid-launcher/blob/main/docs/PRIVACY.md` |
| **Ads** | No, the app doesn't contain ads |
| **App access** | All functionality is available without special access (no login) |
| **Content rating** | Fill the questionnaire: category *Utility, productivity, communication or other*; answer **No** to every content question → expected rating Everyone / PEGI 3 |
| **Target audience** | 18 and over (it's set up by adults for older relatives) |
| **News app** | No |
| **Data safety** | **No** data collected, **no** data shared. (Contacts the user picks and tile settings stay on the device and are never transmitted.) Encryption in transit: not applicable (no data sent). Users can delete data: yes, by clearing app data or uninstalling. |
| **Government app / Financial features / Health** | No / None / No |

**Accessibility API declaration** (asked because of the big-buttons service):
- *Is your app an accessibility tool?* **No** (launchers don't qualify).
- *Describe how your app uses the AccessibilityService API:*
  ```
  FamilyGrid is a simplified home screen for elderly users. Its optional accessibility service
  ("FamilyGrid big buttons") draws large, labelled Back / Home / Apps buttons over the system
  navigation bar, and only performs GLOBAL_ACTION_BACK and GLOBAL_ACTION_HOME when the user taps them.
  It does not read screen content (canRetrieveWindowContent="false"), does not collect or share any
  data, and never acts on its own. A prominent in-app disclosure ("About the big buttons") explains
  this and requires the user to tap "Agree and continue" before Android's Accessibility settings open.
  ```
- *Video:* a 30–60 s screen recording: open FamilyGrid Settings → Big Back/Home buttons → the
  disclosure → Agree → switch it on in Accessibility → the buttons working in another app.
  Upload it unlisted to YouTube and paste the link. (With the phone connected, an agent can record it
  with `adb shell screenrecord`.)

## Step 7: Closed test, 12 testers for 14 days (🧑 you)

Required for personal accounts created after Nov 2023.

1. **Test and release → Testing → Closed testing → Create track** → upload `out\FamilyGrid.aab`.
2. Release notes: `First test release of FamilyGrid.`
3. **Testers:** create an email list with **15–20** Google accounts (family, friends, colleagues).
   15–20 gives a margin above the 12 required.
4. Send them the **opt-in link**; each must tap *Become a tester* and **install from Play**.
5. Keep at least 12 opted in for **14 days in a row**. Fix anything they report and upload a new
   bundle (raise `versionCode` in `AndroidManifest.xml` each time).

## Step 8: Production (🧑 you)

1. **Apply for production access** (answer the questions about your test).
2. **Production → Create new release** → upload the latest `.aab` → countries → **Send for review**.

---

### Before every new upload
- Raise `android:versionCode` (and `versionName`) in `AndroidManifest.xml`
- `.\build-aab.ps1`
- Upload `out\FamilyGrid.aab`

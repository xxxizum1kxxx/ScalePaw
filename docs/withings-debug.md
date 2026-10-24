# Withings driver debugging guide (experimental)

[简体中文](withings-debug.zh-CN.md)

The Withings driver is **experimental**. It was written from public protocol notes and has not been verified on real hardware yet. It is **read-only**: it reads the measurements stored on the scale and never deletes them, never changes the scale's Wi-Fi or account setup, and never updates firmware. ScalePaw is not affiliated with Withings.

If you own a Withings scale and are willing to test, this guide explains what to try and what to send back.

## 1. Supported models

| Model | Advertised name | Status |
| --- | --- | --- |
| Body Smart (WBS13) | `Body Smart…` | Main target; the reference implementation was tested on it |
| Body+ (WBS05) | `Body+…` | Should work; pairs without a passkey |
| Body Comp, Body Scan, Body Cardio | `Body Comp…`, `Body Scan…`, `Body Cardio…` | Unknown; claimed by name only |

The driver also claims any device that advertises the Withings service UUID (`00000020-5749-5448-…`). In the Bluetooth list it shows as "Withings … (experimental)".

## 2. Preparation

1. **Set the scale up in the Withings app first.** The scale must belong to a Withings account. ScalePaw cannot do the first-time setup.
2. **Get the account key (kl).** Each scale has a 32-character secret that only the account knows. Without it the scale refuses to talk.
   - In ScalePaw: Settings, Bluetooth, select the scale, then "Get key from Withings" and sign in. The key of the matching scale is stored automatically.
   - This only works for **email and password** accounts. Accounts that sign in with Apple or Google, or that have two-step verification, will fail with "Withings did not open a session". In that case get the key another way (for example with [withings-local](https://github.com/pierosavi/withings-local)) and paste it into the "Account key (kl)" field.
   - Your email and password are sent only to Withings and are not stored. ScalePaw signs out right after reading the key.
3. **Leave "Sync the scale clock" on** unless you are testing it. It sets the scale's time and time zone on each connection.

## 3. Finding and pairing

- The scale only advertises for **about a minute after a weigh-in**, or after you **hold its button for 3 seconds** (setup mode). Start the search in ScalePaw, then step on the scale. ScalePaw waits up to two minutes for the scale to appear.
- **Body Smart** shows a 6-digit code on its display. Enter it in the Android pairing dialog. **Body+** pairs without a code.
- If the scale shows "Setup failed" or similar after pairing with ScalePaw, that is expected: ScalePaw does not run the Withings setup. The scale keeps working with the Withings app.
- If ScalePaw reports "The scale is in setup mode", the scale has not been set up with a Withings account yet. Finish that in the Withings app.

## 4. What to test

Please try each combination and note what happens (connected? readings imported? error message?):

| Scale mode | Withings app Bluetooth | Expected |
| --- | --- | --- |
| Bluetooth only | Phone Bluetooth on, Withings app closed | Should work; the main case |
| Bluetooth only | Withings app open | The Withings app may grab the connection first |
| Wi-Fi | Withings app closed | Unknown: Wi-Fi scales may stop advertising after uploading |
| Wi-Fi | Withings app open | Unknown |

Also useful:

- Weigh in two or three times, then connect once. Are all readings imported, with the right times?
- Weigh in, connect, then connect again without weighing. ScalePaw should say "no new readings".
- Turn "Sync the scale clock" off, weigh in, connect. Are the timestamps still right?
- Which values appear: weight, body fat, muscle, water, bone, visceral fat, BMR, metabolic age, heart rate?

## 5. Collecting diagnostics

### In-app diagnostics (preferred)

When a session fails, the driver keeps a short log of it. Open Settings, Bluetooth, select the scale, scroll to "Last session" and tap **Copy diagnostics**. Paste the text into the issue or message.

The log contains message types and lengths, the firmware version and error codes. It **does not** contain the account key, the MAC address, the challenge bytes or any measurement values.

### adb logcat

With a debug build and USB debugging on:

```sh
adb logcat | grep "ScalePaw/"
```

Lines tagged `ScalePaw/WithingsHandler` come from the driver; `ScalePaw/GattLink` shows Bluetooth-level failures such as pairing errors.

### nRF Connect

If ScalePaw cannot find or connect to the scale at all, [nRF Connect](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp) helps:

1. Scan right after a weigh-in and look for the scale.
2. Screenshot the advertisement: name, service UUIDs and manufacturer data.
3. Connect and screenshot the service list.

**Blur or remove the MAC address** before sharing screenshots.

## 6. Common problems

| Message or symptom | Likely cause | What to do |
| --- | --- | --- |
| Scale not found | It was not advertising | Step on the scale or hold its button 3 seconds right after starting the search |
| Pairing fails or keeps asking | Wrong passkey, or an old bond | Remove the scale in Android Bluetooth settings and try again |
| "needs its account key" | No key stored | Sign in with Withings or paste the key |
| "rejected the account key" (error -6) | Wrong or outdated key | Sign in again; the key changes when the scale is set up again |
| "refused the request before authentication" (error -5) | Authentication did not complete | Copy diagnostics and report it |
| "could not prove it knows the account key" | Key belongs to another scale | Check which scale you picked on the sign-in page |
| Readings dated 1970 or skipped | Scale clock not set | Keep "Sync the scale clock" on |
| "stopped answering" | Connection dropped | Keep the phone close and try again |
| "error X for command Y" | Unknown scale behaviour | Copy diagnostics and report it |

## 7. Privacy

- The account key is stored only on the phone, in ScalePaw's driver settings.
- Email and password are sent only to `scalews.withings.net` over HTTPS and are kept in memory only while signing in.
- Diagnostics are redacted as described above. Still, read them before sharing.

## 8. Commands ScalePaw never sends

To keep the scale and its account intact, the driver never sends:

- stored-measure action 2 (delete readings from the scale)
- `ASSOCIATION_KEYS_SET` (308) and `SETUP_OK` (275), which change the account binding
- `SCALE_SESSION` (269)
- any Wi-Fi configuration command
- any firmware update command

It only sends: `PROBE`, `PROBE_CHALLENGE`, `TIME_SET` (when enabled), `CONNECT_REASON`, `STORED_MEASURE` (actions 0 and 1, which only read), `SYNC_OK` and `DISCONNECT`.

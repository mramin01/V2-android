# VBOX QA Report — v1.0 audit + fixes (2026-11-22)

Audit of the v1.0 build the user installed on his Samsung phone, against his
5 screenshots and his report: (1) UI size bigger than the screen,
(2) no v2 core, (3) subscription list / pasted config link does not get
selected, (4) "many other problems, only the appearance was done".

Scope of this pass: UI layout, selection logic, every button/option.
NOT touched (owned by the core agent): `core/`, `scripts/`, `app/libs/`,
`app/build.gradle.kts`, `settings.gradle.kts`, `X25519.kt`.
No GitHub upload, no CI run, no contact with the user in this pass.

`python3 check_project.py` → **ALL CHECKS PASSED** (i18n EN/FA parity 96/96).

## A. Layout overflow — fixed

### A1. The "cut-off 4th tab" in the bottom nav (screenshot 1)
**Cause:** the "Ping" pill was placed at `BottomStart` with a fixed
`offset(y = -16dp)`, directly under the centered nav bar, which is drawn
later and therefore on top. On a 360dp-wide screen the pill (x≈16..130dp)
sat underneath the nav bar (x≈26..334dp); only its chart icon peeked out
at the left edge — it looked like a half-cut 4th tab.
**Fix (`MainActivity.kt`):** the whole bottom overlay is now one `Column`
(power row, then nav bar) with no absolute offsets. The Ping pill sits
next to the power button in the power row.

### A2. Power button could overlap the nav bar
**Cause:** the button used a fixed `offset(y = -108dp)`; the nav bar's
height grows with font/display scale, so on larger scales the two touched.
**Fix:** same Column restructure — the button is in a row *above* the nav
bar by construction, so overlap is impossible at any scale.

### A3. Nav bar wider than the screen
**Cause:** `NavPill` used fixed 22dp horizontal padding; with Persian
labels ("کانفیگ‌ها", "تنظیمات") and a larger font scale the three pills
overflowed the screen width.
**Fix:** pills are now `weight(1f)` each, icon 24dp, label 12sp,
`maxLines = 1` — they always fit, in either language, at any font scale.

### A4. Content hidden behind the overlay (Home card "taller than the screen")
**Cause:** every screen ended with a magic `VerticalSpacer(180)` that did
not match the real overlay height, and the snackbar sat at 120dp — behind
the overlay.
**Fix (`ui/Components.kt` + all screens):** one shared constant
`BottomClearanceDp = 200`, used as the bottom spacer in Home, Configs,
ServerList (LazyColumn), Settings and all sub-screens; snackbar padding
raised to the same 200dp. The last row of every screen can now be scrolled
fully above the overlay.

### A5. HWID pill could overflow its row (Settings)
**Fix:** `GreyValuePill` text is now `maxLines = 1` with ellipsis; the
copy button still copies the full value.

## B. Selection bugs — fixed

### B1. Adding a config never selected it (the user's main complaint)
**Cause:** `addLink`, `addFromClipboard`, `addSubscription` and
`refreshSubscription` saved the server but never wrote
`selectedConfigId`. The radio stayed on the old server (or nothing).
**Fix (`VpnViewModel.kt`):** new `addLinks(text)` — parses one link or
all links in pasted text — and `selectNewOrExisting()`: the first newly
added config becomes the active server. Re-adding an already-stored link
selects the stored server instead of reporting "0 added" (new
`alreadyAdded` string). QR scan, share intents and deep links now go
through the same path.

### B2. Refreshing a subscription silently un-selected the active server
**Cause:** `ConfigParser` gave every parsed link a fresh random UUID, so
after a refresh all rows had new ids and the stored `selectedConfigId`
pointed at deleted rows → selection became null.
**Fix (`data/ConfigParser.kt`):** `stableId(link, subscriptionId)` —
`UUID.nameUUIDFromBytes(group|link)` — the same link always gets the same
id, so selection and saved pings survive a refresh. `replaceSubscriptionConfigs`
still replaces the rows; the ids are now stable.

### B3. Subscription showed "1 (0)" servers and "No information" (screenshots 2–3)
**Cause (most likely, see "not verified" below):** `replaceSubscriptionConfigs`
replaced the stored servers with *whatever the fetch returned, even an
empty list, with no warning*. A seller panel can return HTTP 200 with a
valid `subscription-userinfo` header (usage 6.47/25 GB was shown) but an
empty body — exactly what panels do for an expired subscription, and this
one showed "0 Days left". One such refresh/first-add wiped the servers
silently. The old code also never surfaced `FetchResult.rawLines`, so
"empty body" and "unparseable body" looked identical.
**Fix (`VpnViewModel.kt`, `applyFetchResult`):**
- An empty fetch NEVER wipes stored servers; it keeps them, updates the
  usage info, and tells the user why: "no configs" vs "N lines received,
  but none could be parsed" vs the expired notice when `daysLeft == 0`.
- After a successful fetch the selection is preserved (same server, now
  with a stable id) or falls back to the first fresh server.
- Subscription URLs without a scheme get `https://` prepended.
- `SubscriptionFetcher` now `distinctBy { it.id }` — duplicate links in
  one body can no longer produce duplicate LazyColumn keys.
- Empty group detail (`ServerListScreen.kt`) is now a real empty state:
  explanation, expired warning in red when applicable, and a working
  Refresh button (subscriptions) instead of bare "No information".
- Empty subscriptions now show a "No servers" subtitle under the usage
  bar on the Configs screen (`ConfigsScreen.kt`).

### B4. "Ping All" in the Local group pinged every server, including subscriptions
**Cause:** `pingAll(null)` meant "all configs"; the Local card and the
Local detail screen passed `null`.
**Fix:** new `pingGroup(groupId)` ("local" → Local only, else that
subscription) and a no-arg `pingAll()` for the Configs sort row.

### B5. Power-button Ping pill silently did nothing with no server selected
**Fix:** `pingSelected()` now shows "Select a server" instead of
returning silently.

### B6. `addFromClipboard` could crash on an empty clipboard
**Cause:** `primaryClip?.getItemAt(0)` throws when the clipboard exists
but has 0 items.
**Fix:** `itemCount` guard + "no link found" message.

## C. Button-by-button verification — fixed

| Element | Before | After |
|---|---|---|
| Configs gear icon next to "Ping All" | dead icon, no handler | opens the sort menu |
| Subscription card refresh icon | dead — tap opened the group instead | refreshes that subscription |
| Configs sort menu + sort label | hardcoded English "Ping/Name/None" | localized (`sortPing/sortName/sortNone`) |
| Manual-link dialog | parsed only ONE link; a failed save closed the dialog and the text was lost | parses all links in the text; stays open with an inline error when none found |
| Subscription dialog | blank URL (or a proxy link in the URL field) closed the dialog silently | stays open with an inline error (`enterSubscriptionUrl` / `invalidLink`) |
| QR scan button | never requested CAMERA permission — scanner could open on a black preview | runtime permission flow in `MainActivity`; denial shows a message (`cameraPermissionNeeded`) |
| Settings → Reset | message was hardcoded `Strings.en.resetDone` (English even in FA mode) and the service was left running | message captured in the current language before wiping; VPN service stopped first |
| "Auto update" toggle on subscriptions | stored but never acted on | once per launch, auto-update subscriptions older than 24h are refreshed silently (empty-fetch guard protects the servers) |
| Home "DNS (DoU)" row | fixed label even when mode was UDP/DoH | label follows the actual mode: DNS (UDP)/(DoH)/(DoU) |
| Home info icons, Telegram row, Routing note, "DNS server" field label | hardcoded English texts | moved into `AppStrings` EN+FA (`stopOnSleepInfo`, `smartConnectInfo`, `telegramCommunityInfo`, `routingNote`, `dnsServer`) |
| Ping label "timeout" | hardcoded English in `PingTester` | localized via `s.timeout` |
| Busy state (subscription fetch/refresh) | `state.busy` was set but nothing in the UI showed it | progress bar under the sort row on the Configs screen |
| Settings "VPN Core" row + Core screen | `CoreEngineFactory.create()` called twice per composition; hardcoded "VPN Core" title | single `remember`ed lookup; localized `vpnCore` string |

Verified working as-is (no change needed): Stop-on-sleep toggle (wired to
the screen-off receiver in `VboxVpnService`), Smart Connect toggle,
Routing/DNS/Appearance/Tunnel/DNS-mode radios, MTU field (576–9000),
IPv6/LAN-bypass switches, language chips, HWID/public-key copy buttons,
server tap-to-select, server delete (clears selection when it was the
selected one), subscription rename/delete in the `...` menu, Reset
confirmation dialog, power button (still honestly refuses while there is
no core — no fake connection), Docs link (shows the key explanation dialog;
there is no docs website to link to).

## D. What was NOT fixed here (out of scope for this pass)

- **The VPN core itself ("هسته v2 نداره"):** `CoreEngineFactory` still
  returns `NoCoreEngine` — the sing-box `libbox.aar` is not bundled.
  Building it from sing-box source with gomobile (Go + NDK) is the core
  agent's job. The UI stays honest about it: power button shows the
  "core not ready" dialog, Settings shows "VPN Core — Not installed" in
  red, the Core screen explains how to enable it. Nothing in this pass
  fakes a connection.

## E. Honestly not verifiable without a real Android device

1. **The exact server response behind "1 (0)".** I could not reproduce
   the user's subscription fetch without his subscription URL/body. The
   empty-fetch wipe + expired-panel theory fits all the evidence (usage
   header present, 0 servers, 0 days left), and the fix removes the harm
   in every variant of it — but if his panel returns a body format this
   parser genuinely cannot read (e.g. Clash YAML, JSON), the app will now
   *say so* ("N lines received, but none could be parsed") instead of
   silently showing 0. If that message appears on his phone, the body
   format needs a new parser.
2. **Pixel-level layout on his Samsung.** The overflow fixes are
   structural (weight-based nav, no absolute offsets, 200dp clearance),
   which is the correct class of fix, but I could not render the screens
   at his exact density/font scale — CI (compile + unit tests) is the
   only automated gate available here; there is no emulator in this
   environment.
3. **QR scanning end-to-end, clipboard on his device, subscription fetch
   over his network.** Code paths are traced and guarded, but only a
   real phone run proves them.
4. **The parse error he hit when installing v1.0** ("problem parsing
   this package") was most likely a truncated download; the APK itself
   was verified as a valid Android package. If it recurs with the full
   19.4 MB file, it would point at his Android version (< 8.0, below
   `minSdk 26`) or a broken download manager.

## Files changed (14)

- `app/src/main/java/com/amin/vbox/MainActivity.kt` — bottom overlay
  restructure, weight-based nav, Ping pill relocation, snackbar clearance,
  CAMERA permission flow, intents/QR through `addLinks`.
- `app/src/main/java/com/amin/vbox/VpnViewModel.kt` — `addLinks`,
  selection on add, `applyFetchResult` empty-fetch guard, `pingGroup`,
  `pingSelected` feedback, startup auto-refresh, `resetAll` language +
  service stop, `showMessage`.
- `app/src/main/java/com/amin/vbox/data/ConfigParser.kt` — stable
  deterministic config ids.
- `app/src/main/java/com/amin/vbox/data/Store.kt` — `addConfigs` returns
  the added configs.
- `app/src/main/java/com/amin/vbox/data/SubscriptionFetcher.kt` —
  dedupe fetched configs by id.
- `app/src/main/java/com/amin/vbox/data/PingTester.kt` — localized
  timeout label parameter.
- `app/src/main/java/com/amin/vbox/i18n/Strings.kt` — 17 new keys, EN+FA
  (96/96 parity).
- `app/src/main/java/com/amin/vbox/ui/Components.kt` —
  `BottomClearanceDp = 200`, HWID pill ellipsis.
- `app/src/main/java/com/amin/vbox/ui/HomeScreen.kt` — live DNS label,
  localized info texts, clearance.
- `app/src/main/java/com/amin/vbox/ui/ConfigsScreen.kt` — gear opens sort
  menu, refresh icon works, group-scoped ping, busy bar, 0-server
  subtitle, localized sort labels, clearance.
- `app/src/main/java/com/amin/vbox/ui/ServerListScreen.kt` — real empty
  state with Refresh, group-scoped ping, localized timeout, clearance.
- `app/src/main/java/com/amin/vbox/ui/SettingsScreen.kt` — cached core
  check, localized core label, clearance.
- `app/src/main/java/com/amin/vbox/ui/SubSettingsScreens.kt` — localized
  DNS/routing/core strings, cached core check, clearance.
- `app/src/main/java/com/amin/vbox/ui/AddConfig.kt` — manual-link and
  subscription dialogs validate before dismissing.

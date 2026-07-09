# Connectivity Audit — Spotify Connection Reliability

> Root-cause analysis of intermittent playback-control failures.
> Produced as Phase 1 of the connection-architecture rework (2026-07-07).

## 1. The Path of a Play Command Today

```
User taps Play (Compose)
  → SpotifyViewModel.playTrack()/togglePlayPause()/…      [ui/SpotifyViewModel.kt]
    → attemptPlaybackCommandWithWakeup { command }
      → SpotifyPlaybackController.play()/forceResume()/…   [player/SpotifyPlaybackController.kt]
        → DeviceManager.getPhoneDeviceId()                 [player/DeviceManager.kt]
          → lockedDeviceId?  else cache (2-min TTL)  else GET /v1/me/player/devices
        → PUT /v1/me/player/play?device_id=…
        → on 404: DeviceManager.refreshDeviceId() → retry once
        → on 404 again: retry WITHOUT device_id ("let Spotify choose")
      → on false: AVRCP KEYCODE_MEDIA_PLAY kickstart → wait 2 s → refreshDeviceId() → retry once
    → applyPlaybackCommandOutcome() → _deviceNotFoundError / _isOffline
    → delay(500) → syncPlaybackState()  (also runs every 2–4 s independently)
```

There is **no single owner** of "are we connected, and to what?" — the answer is
smeared across four cooperating-but-uncoordinated mechanisms:

| Mechanism | Where | Precedence |
|-----------|-------|------------|
| Locked device (Settings) | `DeviceManager.lockedDeviceId` (var, set by ViewModel from DataStore) | Absolute — bypasses everything, even validity checks |
| Device cache (2-min TTL) | `DeviceManager.cachedDeviceId` | Used if fresh |
| Remembered device (blind-spot fallback) | Same field! `fallbackToRememberedDevice()` returns the *expired* cache | Used when `/devices` is empty or errors |
| Passive registration | `registerActiveDevice()` called from the metadata poll | Overwrites the cache every 2–4 s while playing |

## 2. Failure-Mode Map

### F1. Empty `/devices` while phone is actually playing
Spotify's `/v1/me/player/devices` frequently omits a device that *is* active
(documented Connect blind spot, worst on cellular and right after BT reconnect).
- If nothing is remembered → `getPhoneDeviceId()` returns null → `play()` returns
  `false` **without ever issuing an HTTP request** → AVRCP kickstart fires (may
  start the wrong local queue on the phone) → "device not found" banner.
- Note the asymmetry: `executeWithRetry()` has a null-device "let Spotify choose"
  fallback, but `play(...)` (the most-used command) **early-returns on null device
  before reaching it** (`SpotifyPlaybackController.kt:50-53`). Pause/next/previous
  fall through with `deviceId = null` and often succeed. → *"skip works but play
  doesn't"* symptom.

### F2. Stale `device_id` (phone re-registered after sleep/reconnect)
Spotify assigns a **new device_id** when the phone's Spotify process restarts.
The 2-min cache and the "remembered device" both serve the dead ID.
- `executeWithRetry` recovers only if `refreshDeviceId()` returns a *different*
  ID. But `refreshDeviceId()` falls back to the *same remembered (dead) ID* when
  `/devices` is empty (`DeviceManager.kt:129-133`), so the retry is skipped and
  the command fails — even though a no-device_id call would have succeeded.

### F3. Locked device is absolute and never validated
`lockedDeviceId` short-circuits both `getPhoneDeviceId()` **and**
`refreshDeviceId()`. If the locked device's ID rotated (F2), every command 404s
forever; the 404-retry calls `refreshDeviceId()` which returns the same dead
locked ID. Only the "retry without device_id" fallback saves some commands.
The lock stores an **ID**, but Spotify device IDs are not stable identities —
the lock should be a *preference by identity/type* ("my Pixel", "car player"),
re-resolved per session.

### F4. 403 restricted device
Car built-in players often report `is_restricted = true`. Priority rule 2 ("any
smartphone") ignores `isRestricted`; only rule 3 filters it. A 403 result is
terminal (`executeWithRetry` returns false, no re-target), then the AVRCP hack
fires even though the failure had nothing to do with the phone being asleep.

### F5. Coarse offline detection
Every `IOException` anywhere (metadata sync, search, library) sets a single
`_isOffline` flag. There is no distinction between:
- no network at all,
- Spotify API unreachable but LAN fine,
- network fine but **no active player** (204 from `/me/player`),
- device asleep (devices listed, none active).
The banner therefore flip-flops: one timed-out poll → "Offline Mode"; next poll
succeeds → banner clears; repeat. UI trust is destroyed even when audio works.

### F6. Success ≠ session health
`PUT /play` returning 202/204 means Spotify **accepted** the command, not that
audio started. After BT reconnect, commands are accepted against a session that
is routed to phone speaker or nowhere. The app never verifies via a follow-up
`GET /me/player` inside the command path (the 500 ms-later poll updates
metadata but doesn't gate success/failure state or trigger re-target).

### F7. AVRCP kickstart as default failure path
Any semantic failure (403, no device, 404-after-retries) triggers
`KEYCODE_MEDIA_PLAY` into the system — which can start the phone's *local*
last-used media app or the wrong Spotify queue. It's suppressed during calls,
but it still runs for failures it cannot possibly fix (403 restricted, wrong
target) and is invisible/undiagnosable to the user.

### F8. Startup / BT-reconnect recovery is data-oriented, not session-oriented
`scheduleStartupRecovery` + `onResume` retry Home/Library/devices *loads* but
never establish a **playback session**: no transfer, no verification that a
target is Ready. So Home renders fine while play is dead — the exact reported
symptom. `BluetoothAutoLaunchReceiver` only foregrounds the Activity; the ACL
event is not used to kick device re-resolution, and it races Spotify's own
reconnect (a devices fetch fired instantly on ACL usually misses the phone).

## 3. Race Conditions

| # | Race | Effect |
|---|------|--------|
| R1 | `cacheDevice()` uses `mutex.tryLock()` — proceeds to mutate even if the lock is *not* acquired; the finally block then `unlock()`s a mutex held by someone else (swallowed `IllegalStateException`) or double-writes | Cache can be written concurrently by the metadata poll (`registerActiveDevice`) and `refreshDeviceId`; last-writer-wins mid-command |
| R2 | Metadata poll `registerActiveDevice()` (car player active) vs command path `refreshDeviceId()` (picks phone) | Target flips between phone and car between consecutive commands — the "wrong device" symptom |
| R3 | Two concurrent commands both see expired cache → both call `refreshDeviceId()` → duplicate `/devices` calls (the read path's mutex releases before the refresh) | Wasted rate-limit budget; interleaved cache writes |
| R4 | Command in flight while `syncPlaybackState()` overwrites `_playbackState` and auto-clears `_deviceNotFoundError` | Error banners flash and self-dismiss mid-recovery; optimistic UI toggles revert |
| R5 | `attemptPlaybackCommandWithWakeup` retry (2 s later) races the 500 ms post-command sync | State observed between attempts is indeterminate |

## 4. Why Both Endpoints Are Unreliable Today

- **Phone as endpoint**: dies on F1/F2 (blind spots + ID rotation after sleep).
  The heuristics were tuned for this case but the fallbacks feed dead IDs back
  into the retry loop.
- **Car player as endpoint**: only reachable via priority rule 3 ("any active
  device") — i.e., the app targets it *by accident* when it's already active,
  and passive registration then locks onto it, fighting rule 1 next refresh
  (R2). It is never a first-class choice; `transferPlayback` (`forceResume`) is
  wired to the *discovered phone*, so tapping play can actively steal the
  session **away** from the car player the user wanted.

## 5. Design Conclusions (feeds Phase 2)

1. One owner: a `PlaybackSessionManager` holding a single
   `ConnectionState` (`Disconnected / Discovering / Ready(device) /
   Degraded(reason) / Offline`) exposed as StateFlow. ViewModel observes; never
   computes connectivity.
2. Explicit `PlaybackTarget` (`Auto | Phone | CarPlayer | Specific(identity)`),
   persisted as a *preference*, re-resolved to a live device_id per session —
   replaces the raw ID lock (kept for backward compat as `Specific`).
3. Structured reconnect pipeline (idempotent, single-flight):
   discover → select per target → transfer if needed → **verify via GET
   /me/player** → Ready. Triggered by: cold start, `onResume`, ACL_CONNECTED
   (with settle delay), profile activation, command failure.
4. Command gate: commands consult ConnectionState; if not Ready, run the
   reconnect pipeline first (bounded), else fail fast with a *specific* reason.
5. Failure taxonomy replaces boolean flags: `NoNetwork`, `SpotifyUnreachable`,
   `NoDevices`, `DeviceAsleep`, `RestrictedDevice`, `WrongDevice`,
   `RateLimited`, `AuthExpired` — each mapped to a distinct banner + action.
6. AVRCP kickstart becomes an opt-in `RecoveryStrategy` used only for
   `DeviceAsleep` on a Phone target, never for 403/offline/wrong-target.
7. Health loop (session-scoped, low frequency) separate from UI metadata poll;
   both respect the global 429 lockout.

# Phone wake & Connect reliability

Cloud-Bridge controls **Spotify Connect** over the Web API. It cannot force a
cold iPhone to open Spotify over raw IP — that is an iOS platform limit.

## What every user gets (default)

When the car app starts or Bluetooth reconnects, `establishSession()` runs:

1. **Discover** — `GET /v1/me/player/devices`
2. **Remembered transfer** — blind `transferPlayback` to the last successful phone id
3. **Soft resume** — play with no `device_id` (revive last cloud session if any)
4. **Bluetooth AVRCP** — `KEYCODE_MEDIA_PLAY` **only if** A2DP or HEADSET is connected
5. **Optional webhook** — only if the user configured a URL (advanced)
6. **Burst rediscover** — short retries after each successful wake step

### AVRCP (universal car path)

- **When:** phone-oriented target + Connect not ready + **media Bluetooth linked**
- **When not:** emulator with no BT phone, Bluetooth off, unpaired, in-call audio
- **Why:** this is the only mainstream wake that works for generic users without
  home automation or a companion app

Logs when skipped:

```text
AvrcpRecovery: Kickstart skipped: no Bluetooth A2DP/HEADSET device connected
```

## Advanced / optional: wake webhook

Some users (including developers with Home Assistant) can POST to a private
webhook so the phone opens Spotify via a notification or Shortcut.

| | |
|--|--|
| **Required?** | No — leave empty for normal installs |
| **Where** | Settings → **Advanced: optional phone wake** |
| **Default** | Disabled (`HttpCompanionWake` no-ops without a URL) |
| **Example URL** | `http://homeassistant.local:8123/api/webhook/cloud_bridge_wake_spotify` |

The app POSTs:

```json
{"source":"cloud-bridge","action":"wake_spotify"}
```

This is **not** part of the core product story. Document it for power users only.

### iPhone + Home Assistant (example)

1. Create a HA automation with `trigger: webhook` and `webhook_id: …`
2. Action: notify the iOS companion app with `url: "spotify://"` (or a Shortcut)
3. Paste the webhook URL into Cloud-Bridge Settings
4. Test with `curl -X POST -d '{}' <url>` from the LAN

There is **no** supported way for the car app alone to open Spotify on a stock
iPhone over Wi‑Fi without some on-phone agent (HA companion, Shortcut, or a
dedicated companion app).

## UX banners

During establish, the UI may show progressive status:

- Looking for phone Spotify…
- Trying last known device…
- Asking Spotify to resume session…
- Bluetooth wake — waiting for phone…
- Asking phone automation to open Spotify… *(only if webhook configured and used)*
- Waiting for phone to appear…

If establish still fails: **No Spotify device found. Open Spotify on your phone and retry.**

## Related code

| Type | Role |
|------|------|
| `PlaybackSessionManager.establishSession` | Owns the full ladder |
| `DeviceWakeCoordinator` | Ordered wake steps |
| `AvrcpRecoveryStrategy` | BT-gated media key wake |
| `BluetoothMediaLink` | A2DP/HEADSET connected? |
| `HttpCompanionWake` | Optional webhook |
| `LastKnownDeviceStore` | Durable last Connect phone |

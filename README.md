# Claude Usage Companion

Android + Wear OS companion for monitoring Claude Code usage from a phone.

The phone stores the OAuth token locally, refreshes Claude usage directly, and syncs a usage snapshot to Wear OS through the Wear Data Layer. The watch never owns the OAuth token.

## Changes in this fork

**Usage is polled every 5 minutes instead of every minute.** This cuts the
probe requests to `api.anthropic.com` from roughly 1440/day to 288/day. The
Wear tile freshness interval moved from 60s to 300s to match; the tile only
reads a cached snapshot, so nothing is lost.

**Token auto-refresh — implemented, but currently blocked by the server.**
The vault now stores `{accessToken, refreshToken, expiresAt}` instead of a
bare access token (still encrypted with the Android Keystore), and
`ClaudeAuthClient` trades the refresh token for a new access token.

As of 2026-08-30 this does not work: both `platform.claude.com` and
`console.anthropic.com` answer the refresh call with an immediate
`HTTP 429 {"type":"rate_limit_error"}`, on the first attempt of a session,
in well under a second. That looks like a deliberate block on this endpoint
for unofficial clients rather than genuine burst limiting. The OAuth flow it
relies on is undocumented and community-reverse-engineered, so this was
always liable to break.

The code is left in place behind a backoff, in case the block is temporary:

- On failure it backs off 30 minutes, doubling to a 6 hour maximum, and the
  cooldown is persisted, so a failing refresh does not hammer the endpoint.
  Without this the app issued well over a thousand rejected auth requests a
  day, which is both rude and a good way to get an account flagged.
- The failure reason is logged (tag `ClaudeUsageAuth`) and surfaced in the
  app's status line, so a refresh that stops working says why instead of
  reporting a generic expired token.

**In practice you still paste a token by hand.** Run Claude Code once so it
renews its own credentials, then copy `accessToken` / `refreshToken` /
`expiresAt` out of `~/.claude/.credentials.json` and paste the JSON into the
app. The token input accepts the full `.credentials.json`, a compact JSON
with those three fields, or a bare access token.

## Screenshots

| Phone app | Wear app | Wear Tile |
| --- | --- | --- |
| ![Phone usage dashboard](docs/images/phone-usage.png) | ![Wear usage dashboard](docs/images/wear-ring.png) | ![Wear dual-ring tile](docs/images/wear-tile.png) |

## Features

- Phone-first OAuth setup.
- Direct usage refresh from the phone, independent of a desktop computer.
- Cached usage snapshots with automatic refresh about once per minute when a token is saved.
- Manual refresh button for immediate updates.
- Two Material-style phone progress bars and Android widget bars:
  - 5-hour usage window.
  - 7-day usage window.
- Reset countdown labels for both windows.
- Wear OS Material 3 companion app with compact 5-hour and 7-day usage bars.
- Wear OS Tile support with a dual-ring 5-hour / 7-day glance.
- Watch settings for the Tile 7-day ring and high-usage alerts.
- Android home-screen widget.

## How Usage Is Read

The phone makes a lightweight Claude API request using the saved OAuth token and reads the Claude usage headers from the response. The app stores only the latest usage snapshot for UI, widget, and Wear sync.

The OAuth token is stored only on the phone. It is not sent to the watch.

## Run On Emulators Or Devices

Start one Android phone emulator and one Wear OS emulator, then run:

```bash
./gradlew runPhoneDebug
./gradlew runWearDebug
```

The run tasks auto-detect a phone target and a Wear target. To override the target device:

```bash
./gradlew runPhoneDebug -PphoneSerial=emulator-5554
./gradlew runWearDebug -PwearSerial=emulator-5556
```

For physical devices, connect the phone or watch with ADB debugging enabled, then run:

```bash
./gradlew runPhoneDeviceDebug
./gradlew runWearDeviceDebug
```

If more than one matching target is connected, pass the serial:

```bash
./gradlew runPhoneDeviceDebug -PphoneSerial=<phone-serial>
./gradlew runWearDeviceDebug -PwearSerial=<wear-serial>
```

You can also use the shared Android Studio run configurations:

- `Phone Emulator`
- `Wear Emulator`
- `Phone Real Device`
- `Wear Real Device`

## Build

```bash
./gradlew assembleDebug
```

## Project Structure

- `app/` - Android phone app and home-screen widget.
- `wear/` - Wear OS app and Tile service.
- `docs/images/` - README screenshots.

## Notes

- The app is an unofficial companion and is not affiliated with Anthropic.
- Wear Tiles currently use the legacy `androidx.wear.tiles` builders, which compile with deprecation warnings. A future cleanup can migrate the Tile implementation to ProtoLayout Material components.
- Usage availability depends on Claude's OAuth/API response headers.

# BrightHome Lite

> **This is the Lite version.** BrightHome Lite is built only with Light's official SDK, so it can go in the Light Tool Library. It is the same tool, packaged so Light can build and sign it. For the full app, get [BrightHome](https://github.com/gi-os/BrightHome) ([on BrightMarket](https://brightmarket.gzl.dev/app/com.thelightphone.brighthome/)).

Home Assistant on the Light Phone III, as an official Light SDK tool. It shows up on the
phone as **BrightHome Lite** (`com.gios.brighthomelite`).

BrightHome was already a light-sdk tool, but it lived in `examples/BrightHome` of a fork
pinned to SDK 0.0.12, and Light's tool builder only builds `tool/` against the current SDK.
This repo is that code moved into `tool/` and brought up to SDK 0.2.0.

Built on [lightphone/light-sdk](https://github.com/lightphone/light-sdk) (MIT). The SDK
modules (`sdk/`, `plugin/`, `lint-rules/`) are unchanged upstream code; the tool lives in
`tool/`.

## What it does

- **Favorites is the home page.** The handful of things you actually touch, one tap from
  the launcher.
- **Search** by name, room or entity id. A name that starts with what you typed ranks
  above one that only contains it.
- **Rooms** groups entities the way Home Assistant does: the entity's own area first, its
  device's area second. This needs an admin token, because the area, entity and device
  registries are admin-only. With a non-admin token everything goes in one list and
  Settings says why.
- **Scenes** runs scenes, scripts and buttons.
- **Controls.** A tap toggles lights, switches, locks and media players. Holding a row
  opens its controls: brightness for lights, target temperature and mode for thermostats,
  open/stop/close and position for covers. The phone's wheel adjusts whichever value is on
  screen. Buttons only appear if the device reports it supports them.
- **Live state** over the Home Assistant WebSocket while the tool is on screen, so a light
  someone else turns off updates without a refresh. The socket closes when the screen does.
- **Setup by QR.** Open `setup/pair.html` on a computer, fill in the address and token,
  and scan the code with **Set up > Scan setup code**. Typing it in by hand also works.

### Compared with the full BrightHome

Same screens, same logic. Two differences, both small:

| | BrightHome | BrightHome Lite |
| --- | --- | --- |
| LAN address (`http://192.168.x.x:8123`) | Refused. Its old SDK could not allow cleartext, so it needed https (a Cloudflare tunnel). | Works. SDK 0.2.0 has the `cleartext-http` capability, which this tool declares. https still works. |
| `VIBRATE` permission | Requested | Dropped. No code used it. |

BrightHome's v1.0.2 fix for a pairing-screen crash was a patch to its vendored SDK copy
(the keyboard-options decoder), not to tool code. This repo does not patch the SDK.
Upstream 0.2.0 decodes those replies with `explicitNulls = false` and
`ignoreUnknownKeys = true`, which covers the case that crashed. The unit test that
exercised the patched decoder was SDK-level and was not carried over.

## Network and privacy

The tool talks to one host: **your own Home Assistant server**, at the address you give
it. It sends REST calls and opens a WebSocket there, nothing else. If you use Cloudflare
Access, the two service-token headers go to that same address. There is no analytics, no
crash reporting and no third-party service.

`setup/pair.html` loads nothing and sends nothing. The QR encoder is embedded in the file,
so your token never leaves the browser tab.

Stored on the phone, in the tool's DataStore: the address, the Home Assistant token, the
optional Cloudflare Access client id and secret, and your favorites list. **Forget this
instance** in Settings deletes all of it. The token stays valid in Home Assistant until you
revoke it there. Tokens are never shown on screen.

Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA` (only for the setup QR scan,
through `LightQrCodeScanner`). Capability: `cleartext-http`, so a LAN address works.

## Setup

1. In Home Assistant, go to your profile > Security > Long-lived access tokens and create
   one. Use an admin account if you want the Rooms tab.
2. Open `setup/pair.html` from this repo on a computer (double-click it, no server
   needed). Enter your address, for example `http://192.168.1.20:8123`, and the token.
3. On the phone, open BrightHome Lite > Set up > Scan setup code, allow the camera, and
   point it at the screen.

A LAN address only works while the phone is on your home Wi-Fi. To reach Home Assistant
from elsewhere, use an https address: Nabu Casa, or a Cloudflare tunnel. If you put
Cloudflare Access in front of the tunnel, create a service token with a *Service Auth*
policy and fill in the two Access fields in `pair.html`. The tool sends them as
`CF-Access-Client-Id` and `CF-Access-Client-Secret` with every request.

## Build

```
./gradlew -DlightSdk.toolOnly=true :tool:assembleDebug
```

Unit tests (pairing payload parsing, area resolution, search ranking, control ranges):

```
./gradlew -DlightSdk.toolOnly=true :tool:testDebugUnitTest
```

GitHub Actions runs tests, lint and the debug build on every push
(`.github/workflows/build-tool.yml`). It builds only and publishes nothing. Release builds
are signed with the committed `tool/keystore/bright-lite.jks` so sideloaded updates install
over each other; Tool Library builds are signed by Light.

## Tool Library submission notes

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`. Capability: `cleartext-http`.
- Network: only the user's own Home Assistant server.
- Camera: QR scan during setup only, via the SDK's `LightQrCodeScanner`.
- Intentionality: no feed. The WebSocket is open only while the tool is on screen, to keep
  device state correct, and nothing runs in the background.
- The wheel mapping (`WHEEL_CCW` / `WHEEL_CW`, falling back to scancodes 19 and 20) is the
  one the full BrightHome shipped with. Screens with nothing to adjust pass the wheel
  through so it still controls brightness.

## License

MIT, as the upstream SDK. See `LICENSE`.

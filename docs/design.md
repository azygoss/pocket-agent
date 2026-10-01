# Pocket Agent design language

Pocket Agent's look comes from its mark: a `›` prompt built from square
pixels, plus one amber and one coral pixel. Everything below follows from
that, so new UI should too.

## Principles

- **Color is signal, never decoration.** Three colors each carry one meaning,
  and nothing else uses them:
  - blue (`accent`): live / working / the active thing (a connected session,
    focus, links, a search match);
  - amber (`warning`): Pocket needs you (pin a host key, a reconnect retry,
    a dropped session, a meter near its limit);
  - coral (`danger`): an error, a failed connection, close, delete.

  Everything else is ink on graphite (dark) or ink on paper (light).
  `success` green exists only for result readouts (`exit 0`, diff `+`,
  "Kaydedildi") — never for chrome. Primary buttons and the FAB are *ink*
  filled, not blue, so blue stays a signal.
- **Pixels, not dots.** Status marks are small squares (corner ≤ 2dp,
  `PixelShape`), drawn by `SignalPixel` / `StatusPixel`:
  - hollow blue — running (connecting, reconnecting, uploading). It blinks on
    the shared 1 Hz clock (`BlinkClock`), never an infinite animation;
  - solid blue — live (an active SSH session);
  - solid amber — needs you (suspended session, TOFU prompt);
  - solid coral — error;
  - hollow muted — idle (a remote tmux session not attached here).

  Hollow vs. solid also carries the meaning, so color is never the only cue.
- **Two voices of type.** Prose, titles and controls use the system sans.
  Every *readout* uses IBM Plex Mono (`Readout`): hosts, `user@host:port`,
  session names, paths, times, counts, key caps, fingerprints, versions. The
  terminal itself uses the user's chosen terminal font (`LocalMonoFont`),
  independent of `Readout`. Section labels are plain sentence case,
  12sp medium, muted (`SectionLabel`) — no all-caps, no letter-spacing.
- **One selection mark.** The current item (a nav tab, a session tab, a
  segmented option, a font chip) is shown by its ground (`active`) alone — no
  accent stripe, no color change of the label. A theme swatch is selected by
  an ink frame.
- **Sheets on chrome.** The window is chrome (`chrome`): the top strip and the
  bottom navigation sit on it directly. Content is a sheet (`bg`) inset by
  8dp (`SheetInset`) with a 10dp radius and a hairline border (`SheetShape`).
  In the terminal tab the terminal panel *is* the sheet; the session tabs and
  the key strip sit on chrome around it. Full screen drops chrome entirely.
- **Hosts have sigils.** `HostSigil` draws a deterministic, mirrored 3×3 pixel
  pattern in one of six hues (the theme's bright ANSI colors), derived from
  `host + user`. It replaces the generic server icon wherever a host appears,
  so the same machine looks the same everywhere.
- **Agent work is a soft list of steps.** In a transcript (`ChatDialog`) a
  message is plain prose — no bubbles, no side stripes. Each tool call is a
  borderless row with an icon tile that tells its kind (terminal, read,
  edit, write, web, search) and, by its color, its state — coral when it
  failed, quiet ink when done. Results are a small mono line under the tile.
  Three or more consecutive tool calls fold into one group row
  ("4 araç · 1 hata · Bash, Read, Edit") whose chevron turns when opened.
- **Meters are cells.** Usage and download progress are rows of pixel cells
  (`CellMeter`), turning amber past the warning threshold. No spinners: busy
  state is a blinking hollow pixel with a mono label (`BusyPixel`).

- **Empty states are drawn in pixels.** `PixelIllustration` renders small
  pixel drawings (`PixelArt.Terminal`, `PixelArt.Folder`) from the same palette
  as the mark, so an empty screen still speaks the product's language.
- **Controls wear the language.** `ui/Controls.kt` re-dresses Material:
  dialogs are raised sheets with a hairline and ink text buttons
  (`PocketAlertDialog`); text fields keep their label visible *above* the box,
  turn blue only on focus, and render readout values (host, port, URL, token,
  path) in Plex Mono (`mono = true`); switches use a blue track only when on;
  the snackbar is a raised strip with a live pixel.
- **Home is a status board.** A time-of-day greeting with the date as a
  readout, three stat tiles (sessions, hosts, backend), live session sheets,
  then *Agent activity*: the backend inbox as rows whose pixel maps the event
  category (approval → amber, error → coral, running → hollow blue, unread
  completion → solid blue).

## Tokens (`ui/theme/Tokens.kt`)

Surfaces step up `chrome` → `bg` (sheet) → `surface` (cards, key strip,
tiles) → `raised` (dialogs, popovers, icon tiles). `hover` and `active` are
ink washes for pressed/selected grounds. Borders: `border` for hairlines
inside a sheet, `borderStrong` for control outlines. Text: `text`, `text2`,
`muted`; `faint` is decorative only (empty cells, handles, separators in a
path), never text.

Every theme in the catalog maps onto these tokens (`ConsoleTheme.tokens()`),
so all 19 themes follow the same rules. The house themes are **Pocket**
(graphite), **Pocket AMOLED** (true black) and **Pocket Paper** (light). Muted
text meets WCAG AA (4.5:1) on `bg`, `surface` and `raised` in the house
themes.

Shapes: pixel 2dp, key/control 6dp, button 8dp, sheet/card 10dp, dialog 16dp.
Touch targets are at least 44dp (48dp for icon buttons).

## Motion

One-shot only: tabs cross-fade (150ms in, 90ms out), a settings detail page
slides 1/16 of the width while fading in, selection grounds cross-fade in
130ms, the group chevron turns, the terminal panel springs back when a
pull-down is released. No
loops, no shimmer, no spinners. The only repeating thing is the 1 Hz blink,
and its clock only ticks while something is actually running. With the
system "remove animations" setting (animator scale 0) the blink holds steady
and one-shot motion jumps to its end state.

## Verifying a change

`DesignShotsTest` renders the main screens in dark and light with Robolectric
native graphics. It is skipped unless `PA_SHOTS_DIR` is set:

```bash
cd apps/android
PA_SHOTS_DIR=/tmp/shots ./gradlew testDebugUnitTest --tests dev.pocketagent.DesignShotsTest
```

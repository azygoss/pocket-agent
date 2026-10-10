# Pocket Agent design language

Pocket Agent is a terminal you carry. The design keeps the terminal at the
center, shows what is running before anything else, and makes every state
readable at a glance on a small screen. The mark is a mint tile with a `>_`
prompt; the same geometry is the launcher icon, the in-app `BrandMark` and
the notification icon.

## Principles

- **Running things first.** The root screen (*Oturumlar*, `HubScreen`) is
  ordered by urgency: a pending agent approval, open sessions with a live
  preview, tmux sessions that live on the host but are not open on this
  device, agent activity, quick launch profiles, then hosts. There is no tab
  bar; everything is one scroll.
- **Agent events lead to the agent.** The host hook sends the tmux session
  of an agent as `tmux:<name>`. An approval banner and any event row whose
  session is open here (or still alive on the host) open that terminal with
  one tap ("Terminale git"); other rows only mark the event read. Approvals
  are answered in the terminal itself, because the backend does not carry
  the decision.
- **The terminal is a layer, not a tab.** Tapping a session slides the
  workspace up over the root. It can be dismissed with the chevron, the back
  gesture, or by pulling the session bar down (the workspace follows the
  finger, a hint says when releasing will return). While the root is
  scrolled past the session cards, a dock at the bottom shows the active
  session's last line and brings it back with one tap.
- **Tone, not lines.** Surfaces step up in tone (`bg` → `card` → `raised` →
  `overlay`); there are no hairline borders around cards and no shadows.
  Lists are grouped into one rounded block whose rows are separate lazy
  items (`GroupRow`), so long lists stay virtualized.
- **Status is a word plus a shape.** `StatusChip` pairs a dot with the state
  in words ("Bağlı", "Bağlanıyor", "Yeniden bağlanıyor 2/5", "Bağlantı
  hatası"). Dots are filled for a state and hollow for idle; the busy dot
  pulses only while connecting and holds still when animations are off.
  Color is never the only cue.
- **One primary action per view.** Filled mint pill = primary, tonal mint =
  secondary, outlined = neutral alternative, text button = inline action.
  Destructive actions are coral and always confirmed.
- **Closing is explicit.** Closing a session asks every time and offers
  *Sonlandır* (kill the tmux session and its agent) or *Ayır* (drop the
  connection, keep the session running on the host). Sessions owned by
  another device can only be left.
- **Two voices of type.** Interface text uses IBM Plex Sans. Technical
  values use IBM Plex Mono (`Code`): hosts, `user@host:port`, paths,
  commands, fingerprints, key caps. The terminal uses the user's chosen
  terminal font (`LocalMonoFont`), independent of both.
- **Hosts have faces.** `HostAvatar` shows the host's initial on a hue
  derived from `host + user`, so the same machine looks the same in the
  session card, the dock, the host list and the host picker.
- **Agent work reads as steps.** In a transcript (`ChatDialog`) messages are
  plain prose. Each tool call is a row with an icon tile for its kind;
  results are small check/cross chips under it. Three or more consecutive
  tool calls fold into one group row ("4 araç · 1 hata").

## Navigation

`PocketApp` keeps a small back stack of routes: `Hub` (root), `Workspace`
(terminal), `Files`, `Settings`. Back always pops one layer; closing the
last session drops `Workspace` and `Files` from the stack so the user never
lands on an empty terminal. Deep links (`pocketagent://tmux`) open the
workspace; `pocketagent://add` and "Hostu düzenle" from a failed connection
return to the root with the host editor open.

Choices with a few options open a bottom sheet (`PocketSheet`), confirmations
use a dialog (`PocketDialog`, `ConfirmDialog`), long forms and readers open
full screen (`FullScreenDialog`: host editor, file/diff viewer, transcript,
web preview).

## Tokens (`ui/theme/Tokens.kt`)

Every catalog theme maps onto the same roles (`ConsoleTheme.tokens()`), so
all 19 themes follow the same rules:

- Surfaces: `bg`, `card`, `raised`, `overlay`; separators `line` and
  `lineStrong`.
- Text: `text`, `text2`, `text3` (decorative, never body text).
- Brand: `primary`, `onPrimary`, `primaryText`.
- Tones: `accent`, `live`, `busy`, `warn`, `danger`, `neutral`, `violet`.
  Each `Tone` carries `fg` (dot/icon), `text` (on cards), `soft`
  (container) and `onSoft` (text on that container).

Contrast is computed, not hoped for: `readableOn()` nudges a color toward the
theme ink (and then toward white/black) until it meets the target. Text and
`text2` reach 4.5:1 on `bg`, `card` and `raised`; tone text and `onSoft`
reach 4.5:1; dots and icons reach 3:1. `ThemeCatalogTest` checks this for
every theme.

Inset controls (text fields, segmented rails, disabled buttons) use
`wellColor`, one tone above the surface they sit on (`LocalSurface`), so they
stay visible on the page, inside cards and inside sheets.

The house themes are **Pocket** (ink with mint), **Pocket AMOLED** (true
black) and **Pocket Paper** (light).

## Shape and spacing

Spacing follows a 4/8 rhythm (`Space`): 16dp screen margins, 12–16dp inside
cards, 28dp between sections. Corners grow with the surface: key 10dp,
field 14dp, card 22dp, sheet 28dp, buttons and chips fully round. Touch
targets are at least 48dp; icon buttons use the Material 48dp target.

## Type scale

Plex Sans, SemiBold titles with slight negative tracking: headline 28/24sp,
title 20/16/14sp, body 16/14/12.5sp, labels 14/12/11sp Medium. Nothing that
must be read is smaller than 12sp.

## Motion (`Motion`)

Durations are 120ms (fast), 220ms (standard) and 320ms (emphasized) with
decelerating enter and accelerating exit curves. Opening the terminal slides
up a fifth of the height while fading in; pushing Files or Settings slides
in from the right by an eighth; settings pages slide by a tenth. The pulled
workspace springs back if released early. With the system "remove
animations" setting, transitions are skipped and the busy dot stops
pulsing.

## Adaptive layout

The activity handles rotation, keyboard, density and window-size changes
itself (`configChanges`), so turning the phone or attaching a keyboard never
drops the open layer, a sheet or a half-typed form. Side navigation bars and
display cutouts are kept clear on every screen.

At 840dp and wider (tablets, unfolded foldables, landscape tablets) the root
stays on the left at 400dp and the selected layer (terminal, files,
settings) opens on the right; the dock and the pull-to-dismiss gesture are
off because the list is always visible. Forms and settings are capped at a
readable 640dp width. The terminal always uses the full width of its pane.

## Terminal workspace

- Session bar: chevron, session name, status line (state · `user@host` ·
  `cols×rows`), search, files, overflow (new session, rename, web preview,
  share scrollback, full screen, reconnect, close).
- Session tabs appear when two or more sessions are open.
- The canvas runs edge to edge. Rows are measured as if the keyboard were
  closed, so opening the keyboard never resizes the remote PTY.
- Key panel: `ctrl` latch on the left; scrolling keys ordered for agent
  prompts (`esc`, Enter, arrows, `tab`, Shift+Tab, `^C` `^D` `^Z` `^L`,
  navigation, symbols, user snippets); paste, attach file and keyboard
  toggle fixed on the right. Keys fire when the finger lifts, so scrolling
  the strip never types. Arrows and page keys repeat after 400ms of holding.
- Short windows (landscape phones): while the keyboard is up the session
  bar hides, so the terminal keeps its rows; the hidden bar height is added
  back to the row count, so the remote PTY still does not resize.
- Each session keeps its own scroll position; switching tabs recomputes the
  PTY size for the new session.
- Pinch zoom shows the new scale and grid size for a moment. "Ekranı açık
  tut" (overflow menu or settings) keeps the screen on only while the
  terminal is visible; key haptics can be turned off.
- Hardware keyboards: Ctrl+letter, Alt+letter (ESC prefix), Shift+Tab,
  Delete, arrows, Home/End and Page keys are mapped.

## Files

- Dotfiles are hidden by default; the list footer says how many and shows
  them with one tap. A filter that starts with `.` searches them too.
- Sorting by name, modification time or size, folders always first.
- Images (up to 10 MB) open in a viewer with pinch zoom and double-tap.
- Text and diffs open in a virtualized, line-numbered reader. A grep hit
  opens its file scrolled to the matching line, highlighted.
- The listing belongs to the active session: switching to a session on
  another host resets the path and opens that host's home.

## Forms

- The host editor asks before discarding typed values, warns when the same
  address is already saved, chains fields with the keyboard's Next action
  and saves on Done. A stale "sına" result clears when a field changes.
- Pairing accepts a pasted `pa1|…` QR payload as well as the 8-character
  code.

## Performance and energy

- **Ship a non-debuggable build.** Releases use the `dist` build type
  (`make dist-apk`): `debuggable=false`, so ART honours baseline profiles and
  AOT-compiles, while staying signed with the same key as earlier releases so
  in-app updates keep working. R8 stays off until the sshj/BouncyCastle
  reflection paths are verified on a device.
- **Baseline profiles.** `profileinstaller` installs the libraries' profiles
  on sideload installs; `src/main/baseline-prof.txt` marks all app code hot.
- **No IO in composition.** Secret presence checks are cached in
  `ConnectionRepository` instead of hitting `File.exists` on every row.
- **Derive once.** Theme tokens, the color scheme and typography are
  remembered per theme; avatar colors are remembered per host.
- **Redraw, don't recompose.** The busy pulse and the cursor blink are read
  in the draw phase (`graphicsLayer`).
- **Throttle previews.** Session card and dock previews sample the terminal
  buffer at most 4×/s and keep only the last non-empty lines.
- **No theme flash.** The shell paints only the window color until saved
  preferences are read, so the default theme never flashes before the
  chosen one.
- **Stable lists.** Lazy lists declare keys and `contentType`; loading states
  are static skeleton rows in the final geometry (no layout shift).

## Verifying a change

`DesignShotsTest` renders the main screens, sheets and the launcher icon in
dark and light with Robolectric native graphics. It is skipped unless
`PA_SHOTS_DIR` is set:

```bash
cd apps/android
PA_SHOTS_DIR=/tmp/shots ./gradlew testDebugUnitTest --tests 'dev.pocketagent.Design*'
```

`DesignShellShotsTest` captures the real `MainActivity` shell in compact and
wide windows.

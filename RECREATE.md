# Build prompt: Doom of Mokhaiotl Metrics

Hand the text below (everything under the rule) to a fresh agent as its task. It is a behavioural
specification: it says what the plugin must do, not how any of it is built.

---

Build a RuneLite plugin called **Doom of Mokhaiotl Metrics** from scratch, in Java, in a new
repository. It times delves in the Doom of Mokhaiotl, reports how fast the deep delves are going,
counts what the player's gear gave back while they were down there, and keeps a lifetime record of
every run.

Everything below is behaviour and defaults. How you structure the code, what you call things, how
you persist anything and how you test it are yours to decide — decide them well, but do not ask me
to choose. Where the spec says a figure "reads" a certain way, that string is the requirement.

You will need to work out from the game and from the RuneLite API which signals carry the facts
this spec depends on — when a delve starts and ends, how long the game says a delve took, how deep
the player is, when a special attack was spent and what was equipped when it was, when a heal or a
prayer restore or a hitsplat lands. Prefer whichever signal the game states most plainly and most
precisely; several plausible-looking ones are unreliable, so verify rather than assume. It is a
RuneLite hub plugin, so it must respect the hub's restrictions (no reflection, no external
processes, no dynamic code loading, Java 11 source level, no blocking work on the client thread).

## 1. Timing a run

A **run** is one trip into the Doom: it starts when the player drops into delve 1 and ends when
they die, claim loot, leave from the end-of-delve panel, or otherwise leave the cave.

- The delve number shown must be the one the game itself just announced, and the times must agree
  with the game's own reported durations. Do not maintain a parallel count that can drift.
- The first delve's clock must start where the game's own clock for that delve starts, not where
  the announcement lands — those differ by roughly two seconds, and getting it wrong makes delve 1
  read long against the duration the game reports for the same delve.
- **Delve segments are contiguous with no gaps.** A delve's time runs from the moment the previous
  delve was cleared, so restocking, eating and dropping down the hole are charged to the delve they
  precede. The segments must sum to the total run time by construction.
- The clock is wall clock and never pauses.
- **Dying part way into a delve costs nothing already banked.** The reported total is the time
  through the previous delve, and the partial delve is discarded from every figure. The displayed
  run time must always be measured over the same span as the pace shown next to it, so the two can
  never disagree.

### Interruptions

- A dropped connection does **not** end a run. Hopping and logging out put the player back outside
  the entrance, but a reconnect can land them back in the delve they were already in. On coming
  back, wait until the game has actually said where the player is: back in the delve and the run
  carries on; back outside and it ended where it was last seen. Expect the answer to arrive late
  and to sometimes arrive as a value that has not changed, and handle both.
- Walking into delve 1 and straight back out produces no clear and no announcement at all. Give up
  on such a run after about a minute of the boss being absent with nothing banked, and drop it
  silently: no chat summary, no history record, nothing kept on screen. Once a delve has been
  banked this rule no longer applies.
- A run the plugin joined part way through — because it was enabled mid-trip — is tracked normally
  but flagged, because its start time is a guess. Its timer label gets an asterisk (`Time*`).

## 2. The two paces

One config setting picks which pace drives both the overlay and the chat messages.

| Mode | Formula | Answers |
|---|---|---|
| **Deep pace** (default) | `3600 / mean(delve 9+ segment times)` | How fast are my deep delves right now |
| **Run pace** | `deep delves (8+) / total run time` | How many deep delves per hour am I banking |

Delve 8 counts as a deep delve for Run pace but is excluded from the Deep pace average, because it
has a different amount of health to 9 and above. **Neither floor is a setting** — where deep starts
and where the health changes are facts about the fight, and making them configurable only makes the
numbers incomparable between players.

Worked example. A run to delve 20 where delves 1–7 took 8:00, delve 8 took 2:00 and delves 9–20
took 18:00:

- **Deep pace** is `40.0/hr` — twelve delves at a flat 1:30 each.
- **Run pace** is `27.9/hr` — thirteen deep delves banked across the full 28:00, warm-up included.

Run pace starts low and climbs as the shallow delves amortise: 13.8/hr at delve 10, 23.4/hr at
delve 15, 27.9/hr at delve 20. Deep pace stays flat as long as delve times do.

Both are built on the contiguous segments, so restocking counts against the player: a delve fought
in 1:28 after a two-minute restock costs 3:28 of pace. The fight length the game reported is shown
separately in the chat message.

A pace with nothing to average yet reads `-`, not `0`. Deep pace needs one delve 9 or deeper; Run
pace needs one deep delve banked.

## 3. What is drawn over the game

The **Display** setting chooses between three, defaulting to `Panel`.

### Panel

An overlay of labelled rows. While a run is going:

```
Doom Metrics
Delve             14
Time           21:40
Deep pace    40.0/hr
```

Each of the delve number, run timer and pace rows has its own on/off switch, all on by default.

When a run ends the panel stays up for a while rather than vanishing, so the numbers are still
there when the player gets back from their gravestone:

```
Doom Metrics
Died on      Delve 7
Cleared            6
Time           11:17
Deep pace          -
```

Right-clicking the overlay offers **Clear**, which dismisses a finished run early. A linger of 0
hides it immediately.

### Infobox

`Display` set to `Infobox` replaces the panel with a single infobox square — the plugin icon with
one figure over it, sat in the infobox bar with everything else.

A square holds one number, so an **Infobox figure** setting picks which: the delve, the run timer,
the pace, the time left to the target delve, any one of the eight counters, or any one of the four
counter headings with its sources summed. Defaults to `Delve`.

Figures are shortened to fit: `1.2k` for a counter past a thousand, `1h23` for a run past the hour,
`10h` past ten of them, `40.1` for a pace. Whatever was dropped to make it fit goes in the tooltip
— the unit, the full precision, and whether this is a run the plugin saw the start of.

`Time to target` counts down to the delve set under **Target delve**, whether or not **Show target
delve** is switched on, so the target is set in one place whichever thing is drawing it. It reads
`Done` once the target is behind the player, and a dimmed `-` until this run has cleared a delve 9.

Counters keep the colours they have on the panel — red for hitpoints, blue for prayer, yellow for
damage — and one still at zero is drawn grey, so a spec the player expected to be firing is visibly
not. The counter checkboxes have no say over the square: they choose which lines the *panel* draws.

Right-clicking the square also offers **Clear**.

### Off

Draws nothing over the game at all. Nothing else changes: delves are still timed, counters still
count, chat messages still arrive, and the side panel and history still fill up.

## 4. Target delve

**Show target delve** (off by default) adds two rows to both the overlay and the side panel: the
delve being aimed for, and how much longer this run has to go to reach it.

```
Doom Metrics
Delve             14
Time           21:40
Deep pace    40.0/hr
Target            50
Predicted      54:00
```

The prediction is what the delve 9+ average says the delves between here and there will take.

- It reads `-` until this run has cleared a delve 9.
- It reads `Reached` once the target is behind the player.
- The delve in progress is charged against it as it goes, so the figure counts down second by
  second rather than sitting still between clears.
- It never drops below what the delves still to come must take.

Two things a flat average cannot know are left in on purpose, because every other figure here is a
flat average too: delves 1–8 are quicker than the mean, so a target set during the warm-up reads
long, and delves get slower the deeper they go, so a distant target reads short.

Landing on the target is announced in chat whatever the chat interval says, including when the
messages are switched off entirely.

## 5. Counters

The plugin can count what the player's gear and spellbook gave back. **Every counter is off by
default.** A ticked counter appears on the overlay under the pace, in the side panel's table, and
as an option on the history chart.

| Counter | Group | Counted in |
|---|---|---|
| Blood barrage | Spell healing | hitpoints healed |
| Other spells | Spell healing | hitpoints healed |
| Ancient godsword | Spec healing | hitpoints healed |
| Blowpipe | Spec healing | hitpoints healed |
| Other specs | Spec healing | hitpoints healed |
| Eldritch staff | Prayer restored | prayer points restored |
| Zaryte crossbow | Spec damage | damage dealt |
| Other specs | Spec damage | damage dealt |

Each figure is drawn in the colour of what it is counted in — hitpoints red, prayer blue, damage
yellow — so the lines are legible without reading the labels. A counter that has not fired yet
stays grey at zero rather than disappearing, so the overlay does not resize mid-delve.

**Group counters** decides how ticked counters are drawn, defaulting to `Separate`. `Separate`
gives each its own line; `Combined` folds them into one line per group, summing only that group's
*ticked* members and skipping a group with none ticked.

```
Separate                       Combined
Barrage        1,204           Spell heals    1,204
AGS              316           Spec heals       316
Eldritch         180           Prayer           180
ZCB           12,470           Spec dmg      12,470
```

No headings are drawn on the overlay in either mode — everything there is something the player
asked for, so the labels carry the qualification instead. Note that "Other specs" is a row under
both *Spec healing* and *Spec damage*, and an ancient godsword spec both heals and hits, so overlay
labels must name the effect as well as the source (`AGS heal`, `Other spec damage`). The side
panel's table has headings and can use the unqualified names; the chart dropdown needs qualified
ones. Keep the three label sets distinct and hold them apart in tests.

### Attribution

The game does not say what caused a heal. A blood barrage heal, a blowpipe spec heal and a bite of
a saradomin brew are the same hitpoints going up. What the game *does* say is when special attack
energy moved and what was equipped when it did, and when a blood spell drained something. So credit
an effect to whichever of those it can be pinned on, and **drop anything that cannot be pinned**.

Requirements that follow from that:

- Brews, food, natural regeneration and prayer potions must be missing from these figures. That is
  the point, not a shortcoming: a counter that swallowed them would report sustain the gear never
  earned. **Every number is a floor — what could be proven — and never an over-count.**
- Recognise a special attack by the weapon that was equipped when the energy was spent, not by an
  animation; animations are shared. Anything not broken out separately still counts as "other
  specs" — a spec was definitely fired.
- A weapon has far more item ids than a list can keep up with (ornament kits, charged and uncharged
  forms, empty and loaded forms, recolours from Leagues and betas). Make recognition survive a form
  you did not enumerate, or a player's ZCB row will quietly read zero.
- Several causes can be in flight at once. Chaining one spec into another is ordinary play, and the
  ancient godsword's Blood Sacrifice does not pay out until its mark expires roughly eight ticks
  later — so a design that remembers only the most recent spec loses every delayed payout to
  whatever was fired next. Each cause needs both a window of when its effect may arrive and a limit
  on how many hitsplats it may claim, or the auto-attack behind a spec reads as spec damage.
- Effects can reach the plugin *before* their cause does, because of the order the client reads
  players and NPCs within a tick. An effect that explains nothing must wait out the tick it arrived
  on before being given up on.
- When two open windows could both explain one effect, the more recent takes it. That is a bounded
  guess: both causes really did produce a heal on that tick, and only which amount went to which is
  uncertain.
- **Damage to the volatile earth is not counted** (the shockwave shield and its path nodes). It
  guarantees a max hit and two must be broken to raise the shield, which makes it the best thing in
  the delve to spend a blowpipe or eldritch spec on and makes counting the damage misleading — a
  delve's spec damage would read as though the work had been done there. Only the *damage* is
  dropped; the heal and the prayer that spec was fired for are counted exactly as before, which is
  the whole reason it was fired at that target.

## 6. Chat

Post a message whenever the delve number is a multiple of the configured interval, skipping shallow
delves — so the default of 5 reports at delve 10, 15, 20 and so on. All messages are client-side
only and prefixed `[Doom]`.

```
[Doom] Delve 20 in 1:30.0 | 28:00 elapsed | 27.9/hr
[Doom] Target reached | Delve 50 in 1:31.2 | 1:14:20 elapsed | 38.4/hr
[Doom] Cleared delve 20 | 28:00 | 27.9/hr
[Doom] Died on delve 21 | cleared 20 in 28:00 | 27.9/hr
```

The first figure is the fight itself as the game timed it; the elapsed figure is the whole run.
Interval 0 turns the messages off. A separate **Announce run end** setting (on by default) controls
the summary posted on claim, leave or death; it fires only when at least one delve was cleared.
Abandoned runs are never announced.

## 7. Side panel

Behind a chevron icon in the sidebar, top to bottom:

- **Current run** — the same rows as the overlay, so the numbers are somewhere other than over the
  game world. Lead with the two figures a player actually reads mid-fight — which delve they are on
  and how long they have been down — set large, with the rest in small type beneath. The panel is
  worth nothing if those have to be picked out of a list of eleven numbers all set in the same type.
- **This session** and **Lifetime**, side by side under two column headings rather than stacked in
  two sections. They answer different questions — how this evening is going, and what the character
  has done over all of them — but the question a player actually asks is whether tonight is better
  than usual, and that is a comparison. Each holds elapsed time, deep pace, deep delves banked, and
  the ticked counters.
- **Milestones** — the lifetime table (below).
- **Open history** — a button opening the history window (below).

A **session** is a sitting, not a login: it ends after half an hour without a run, which is long
enough that banking and walking back never break it and short enough that coming back tomorrow
starts clean. Nothing is lost when one ends — every run is banked to the lifetime total as it
finishes, whatever session it belonged to.

Panel and window must never read anything the client thread is still writing to.

## 8. Milestones

Every tenth delve gets a row in a lifetime table.

```
Milestones
Delve     KC        PB
10        77    9:00.0
20        76   19:00.0
...
170        1 2:52:00.0
```

- A row appears the first time that delve is cleared and never goes away, so the rows are the
  milestones the character has reached.
- **KC** counts the clears.
- **PB** is the shortest time from the *start of a run* through to that clear — the same span the
  run timer measures, restocking included; not the sum of the fight lengths.
- One run to delve 172 therefore touches every row from 10 to 170, because it cleared every delve
  below the one it died on.
- Stored against the logged-in character, so an alt keeps its own, and it survives client restarts
  and updates.
- Times are held in the unit the game counts delves in and shown as hours, minutes, seconds and a
  tenth. That means the tenth only ever lands on a multiple of six, which is correct.
- A personal best beaten since the client started is shown in green.

**Delves reached before installing.** The game remembers the character's deepest delve ever. The
first time a character logs in, mark the rows up to it as reached — with no KC and no PB. Nothing
is invented; this just stops a returning player being told they have never been past delve 10.

**Runs joined part way through.** Such a run's start time is too late, and left alone it would hand
out a personal best nobody earned. Instead measure it from a moment the run provably had not begun
by: the player cannot drop back into the Doom past delve 1, so the run started after they logged
in, which was after the client started. The clear still counts towards KC. This makes the time too
long rather than too short, and a time that is too long simply never wins — if the player logged in
at the cave and enabled the plugin mid-trip the bound is tight enough that a genuine best stands;
if the client had been open for hours it is loose, and that run quietly fails to set one. Bound the
figure; do not discard it.

## 9. History

Every finished run is written down, and **Open history** puts the record in a window of its own —
one wide enough for a chart, which a side panel is not.

```
Show [ Deepest delve      v ]

Lifetime totals        Per run
Spell healing           .     .   .    .  .
  Blood barrage    ..  . . ..  . ...  . . ..
  Other spells    . .. ..  .. .. .  ...  . .
Spec healing     ~~~~~~~~~~~~~~~~~~~~~~~~~~
  ...
Milestones
```

- One dot per run, oldest first, with a rolling average drawn through them. A plain line through
  every run is unreadable past a few hundred points — it fills in as a solid band and the trend
  disappears into it. Dots let the density show through; the average carries the trend.
- A dropdown picks what the dots measure: how deep the run got (the default), or any one of the
  eight counters. Depth and healing share nothing but an x-axis, so show one at a time — never two
  scales on one picture.
- Switching metric must redraw from memory, not go back to disk.
- Beside the chart sit this character's lifetime counter totals and the milestone table.
- A run recorded before counters existed contributes a zero to every counter, not a gap: dropping
  those runs would renumber every run after them and leave the two charts disagreeing about which
  run was which.
- Runs that ended with the plugin no longer watching are a floor on where the run got to rather
  than the answer, so leave them out of the depth chart while still keeping the record.
- The two chart colours must be checked for lightness, contrast and colour-blind separation against
  the surface they sit on rather than picked by eye, and identity must not rest on colour alone —
  one series is dots, the other a line.

### Persistence

- Per character, on disk, under the RuneLite directory in a subdirectory of the plugin's own.
- Nothing is ever discarded to keep the file small — losing the oldest runs defeats the point. Aim
  for something where twenty thousand runs is about a megabyte.
- Ending the ten-thousandth run must cost no more than ending the first, and a write torn by a
  crash must cost the last run rather than the whole history. Note that the config service is the
  wrong home for this: it caps a single value at 262144 characters and rewrites the whole value on
  every save. Aggregates that stop growing (the milestone table, lifetime totals) belong there;
  a per-run history does not.
- Each run records when it ended, how deep it got, how long that took, how it ended, whether the
  plugin saw its start and its end, and what the counters recorded.
- Also record the notable drops from each run — the eye of Ayak (both forms), avernic treads,
  mokhaiotl cloth and the pet. Nothing needs to display them yet. Only those: the supplies and
  currency the Doom hands out say nothing about how the trip went. Read the pile when it is
  claimed, never before — an unclaimed pile is not yet the player's.
- Never do disk work on the client thread.

## 10. Config

| Setting | Default | Notes |
|---|---|---|
| Pace | Deep pace | Which figure the overlay and chat show |
| Chat every N delves | 5 | 0 disables the messages; range 0–100 |
| Announce run end | on | Summary on claim, leave or death |
| Display | Panel | Panel of rows, one infobox square, or nothing |
| Infobox figure | Delve | Which single figure the square holds |
| Show delve number | on | Overlay row |
| Show run timer | on | Overlay row |
| Show pace | on | Overlay row |
| Keep result for | 30 min | How long a finished run stays on screen; 0 hides at once; range 0–180 |
| Show target delve | off | Adds the target and predicted rows |
| Target delve | 50 | The delve being aimed for; range 10 up to a ceiling well clear of the world record |

Counters section, collapsed by default:

| Setting | Default |
|---|---|
| Group counters | Separate |
| Blood barrage heal | off |
| Other spell heal | off |
| AGS heal | off |
| Blowpipe heal | off |
| Other spec heal | off |
| Eldritch prayer | off |
| ZCB damage | off |
| Other spec damage | off |

Advanced section, collapsed by default:

| Setting | Default | Notes |
|---|---|---|
| Debug logging | off | Logs every Doom varplayer change and delve transition, at debug level |

Notes on the config:

- Pick a config group name specific enough to never collide, and treat every stored key — config
  keys, and the names inside anything you persist — as a format: renaming one silently resets
  saved settings or drops a figure out of every record already written. Version what you store.
- Any delve ceiling must be set well clear of the world record rather than level with it. The
  record only moves one way as gear improves, and a plugin that has to be updated to keep up with
  it will one day refuse to show someone their own delve.

## 11. Acceptance

The plugin is done when all of the following hold. Test what you can without the game — the timing,
pace, prediction, formatting, attribution and storage rules are all decidable from inputs alone,
and should be under test that way, including against strings the game actually emitted rather than
ones typed from memory so a change to the game's wording fails a test rather than quietly
miscounting.

1. Delve numbers and durations match what the game announced, including past delve 8 where the
   real number is presented differently.
2. Delve 1's segment matches the duration the game reports for delve 1.
3. Segments sum to the run time.
4. A death mid-delve leaves the banked total and the pace beside it unchanged and consistent.
5. Both paces match the worked example in §2 to the digit.
6. A reconnect back into the delve continues the run; a reconnect to outside ends it where it was.
7. A walk in and straight back out is dropped silently.
8. Counters never exceed what could be proven, and brews and food never appear in them.
9. Two specs in flight at once do not steal each other's hitsplats.
10. Milestone PBs are per character, survive a restart, and are never set by a run whose start was
    only bounded rather than seen.
11. The history survives a restart, and ending a run costs the same at ten thousand runs as at one.
12. Nothing blocks the client thread, and nothing in the panel or window reads state the client
    thread is still writing.

You cannot verify in-game behaviour yourself, and you must not automate game input to try. When the
build is clean and the tests pass, tell me what to test in-game — the golden path and the edge
cases worth exercising — and wait for me to confirm before calling it done.

---

## Appendix: game-side facts

These are things about the game itself, recorded from real client logs. They are here so you do not
have to spend a play session deriving them — but verify each against the current game rather than
trusting this list, and design so that a wording change or an id you did not know about degrades
gracefully instead of silently reading zero.

Nothing here dictates structure. It says what the game emits; what you do with it is still yours.

### Chat lines

Every delve is bracketed by two game messages, and the closing one carries the delve number and the
fight length to a tenth of a second. This makes chat the signal a run should be driven by: it is
the only thing carrying both facts, it agrees with the game's own timing by construction, and
unlike the varplayers it never fires on login.

The game embeds colour templates that a plain tag-strip does not remove — `@mes_hl_red@` opens and
`</col>` closes. Both must be survived. Verbatim from a log, with the templates intact:

```
@mes_hl_red@Delve level: 3</col>
@mes_hl_red@Delve level: 8+ (15)</col>
Delve level: 3 duration: @mes_hl_red@1:05.40</col>. Personal best: @mes_hl_red@0:37.80</col>
Delve level: 8+ (15) duration: @mes_hl_red@1:30.60</col>. Personal best: @mes_hl_red@0:48.60</col>
Delve level: 2 duration: @mes_hl_red@0:23.40</col> (new personal best)
```

So: `Delve level: N` opens delve N, and the same line with a `duration:` clause closes it. Cases
that will catch a careless parser:

| Line | Why it matters |
|---|---|
| `Delve level: 8+ (15)` | past delve 8 the real number moves into the brackets and the leading number sticks at 8 — the bracketed one wins whenever it is present |
| `... duration: 0:23.40 (new personal best)` | this variant has no `Personal best:` clause at all |
| `Delve level 1 - 8 duration: 9:35.40. Personal best: 6:45.00` | **no colon after "level"** — a milestone summary posted on clearing delve 8, not a delve clear, and must not be read as one |
| `Total duration: 4:47.40` | a running total posted after delves 1–7, not an end-of-trip marker |
| `Deep delves completed: 6,694` | lifetime chatter, not a boundary |

Durations run to a tenth of a second and grow an hours field on a long delve, so parse
`[h:]mm:ss.tt`.

Other lines worth knowing:

- A blood spell taking health off something and giving it to the caster announces itself in chat,
  with `drain some of your opponent` in the middle of the line. This is the **only** signal the
  game gives for a blood barrage heal, and it lands on the tick of the heal every time. Nothing
  else works: the heal is not a hitsplat, the cast animation is shared by every ancient spell, and
  the impact graphic — the obvious answer, and the one to try first — is never reported at all, not
  on any actor and not on the ground. Match the middle of the line, because the possessive moves
  between `your opponent's` and `your opponents'` depending on how many were hit. Note it does not
  say *which* blood spell, and the amulet of blood fury says the same line off a melee hit; both
  are the right way round in a delve, where barraging is the norm and nobody brings a fury.
- The pet announces itself three different ways depending on whether it had room to walk out:
  `You have a funny feeling like you're being followed`, `You have a funny feeling like you would
  have been followed`, and `You feel something weird sneaking into your backpack`. Match on the
  opening words; the rest of each line varies.

### Varplayers

Decoded by lining them up against the chat messages above. All exist as named constants in the
game-value package — never hardcode the numbers.

| Varp | Name | Meaning |
|---|---|---|
| 4798 | `DOM_LAST_DELVE_LEVEL` | The delve just cleared, minus one. **Persists across logins and is set during the login varp flood** — never treat it as a run start. |
| 4803 | `DOM_TOTAL_DURATION` | Cumulative fight ticks this trip, excluding the time between delves. Resets just before entry. |
| 4804 | `DOM_LAST_LEVEL_DURATION` | Ticks the delve just cleared took. `151` ticks matches the `1:30.60` the game reported for that delve, which is how the tick-to-second relationship was confirmed. |
| 4805 | `DOM_LEVEL_START_TIME` | The server tick the current delve began. **Lands about two seconds after the chat line, and is the instant the game's own duration is measured from** — this is what §1's first-delve anchoring needs. |
| 4828 | `DOM_CURRENT_LEVEL_TEMP` | Delves descended to this trip. **Zero for the whole of delve 1**, and cleared on leaving. |

Also relevant:

- `DOM_DEEPEST_LEVEL` — the character's deepest delve ever. This is what §8's pre-install seeding
  reads. It arrives in the login varp flood, which may land either side of the character's profile
  being ready, so both orderings have to work.
- `SA_ENERGY` — the special attack energy bar. A fall in it is the signal that a spec was fired.
  It fires *before* the player and their equipment are updated for the tick, so the weapon has to
  be read a step later — by which point the client's own tick counter may have moved on, so capture
  the tick at the moment the energy fell rather than when you read the weapon.

Two traps in the varplayers, both of which caused real bugs:

- `DOM_CURRENT_LEVEL_TEMP` sitting at zero for the whole of delve 1 means leaving before delve 2
  produces no transition at all, and "back in delve 1" is indistinguishable from "back outside".
- Across a reconnect a varplayer is very often set to the value it already held, and a varplayer
  set to what it already was **raises no change event** — so the case you most want to hear about,
  "still in delve 12", is exactly the case that never fires one. Read the value rather than waiting
  to be told about it, and give it a few ticks of grace before believing a zero, because the flood
  lands a tick or two after login and a delve read before it looks exactly like being outside.

### Interfaces, scripts, NPCs and items

All available as named game-value constants. Look up widgets by component id from those constants;
never combine interface and child ids by hand.

- **End-of-delve panel** — it has a claim button and a leave button, and clicking either ends the
  run. There is also a script that fires when loot is actually claimed. Watch both: the click is
  what closes the run out and cannot wait for the script, while the script is the reading that is
  certain to be at the moment the pile becomes the player's. Take the larger count when both
  arrive. Claiming is the only moment worth reading the pile at — leave any other way, or die, and
  it stays behind.
- **Boss NPCs** — the Doom has a plain form, a shielded form and a burrowed form, and all three
  count as "the boss is present" for the abandon check in §1.
- **Volatile earth** — the shockwave shield and the shockwave path nodes are the NPCs whose damage
  §5 excludes. Match on name as well as id, because an id list is only what this version knew and a
  form you did not enumerate would quietly start counting again.
- **Notable drops** — the eye of Ayak has both a charged and an uncharged item id and only one of
  them is the drop; which is not checkable from outside the game, so list both. The other three are
  the avernic treads, the mokhaiotl cloth and the pet.
- **Spec weapons** — the zaryte crossbow; the ancient godsword (which has a beta-recolour id as
  well); the toxic blowpipe, which has **loaded and empty forms plus an ornament variant of each**
  — the empty one matters because the game swaps to it on the shot that runs the scales out, and a
  spec fired on that shot would otherwise land on an unrecognised id; and the eldritch nightmare
  staff. Fall back to matching the item's name when the id is one you do not list, since the name
  is stable across every ornament, charge state and recolour.
- **Healing spell graphics** — the blood rush, burst and blitz impacts and the sanguinesti staff
  impact (which has a justiciar variant) are watched on the *target*, for the spells that name
  themselves that way. Only graphics a player can produce belong on such a list: ids that merely
  have "blood" in the name are NPC attacks and quest scenes, and a boss playing one of its own
  would open a window that then took credit for a real heal.

### Effects

- **Heals and prayer restores** are read as the player's hitpoints or prayer *boosted level* going
  up. Put no floor on where a prayer rise started: an eldritch spec fired on an empty prayer book
  is exactly the case that matters most, and a guard against zero would drop it.
- **Damage** is read from hitsplats.
- Within a tick the client reads players before NPCs, so a heal on the player's own head is offered
  a tick's worth of events *before* the barrage on the target that caused it. Likewise a melee
  spec's hit arrives on the tick it was fired while the weapon that fired it can only be read a
  step later. This is the ordering §5 requires you to tolerate.
- The ancient godsword's Blood Sacrifice hits once immediately, then marks the target for eight
  ticks; when the mark expires the target takes 25 typeless damage and only then is the attacker
  healed. So one spec produces three separate effects across ten ticks, and a single wide window
  covering all of them would let every auto-attack in between read as spec damage.
- The blowpipe spec heals half of what it hits for, both landing together. The eldritch spec
  restores prayer rather than hitpoints. The zaryte crossbow spec heals nothing — it drains the
  target's defence. Dragon claws are the busiest spec in the game at four hits, which is the cap
  any catch-all group has to cover.
- A weapon attacks every four ticks at the fastest, which is what lets a spec's own hit be told
  apart from the auto-attack behind it.

### Recording your own

A filtered slice of the client log is worth keeping in the repository so the parsing can be tested
against strings the game actually emitted. The debug toggle from §10 — every Doom varplayer change
and every delve transition — is what produces it, and one session covering a handful of trips (a
long run, a short one, some deaths, a walk in and straight back out, and a delve 1 clear followed
by claiming and leaving) covers every case above.

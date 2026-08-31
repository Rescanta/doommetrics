# Doom of Mokhaiotl Metrics

Times every delve in a Doom of Mokhaiotl run, shows your deep delve completions per hour, counts
what your gear gave back while you were down there, and keeps a lifetime record of every run and
every tenth delve you have reached.

## How delves are timed

The game announces both ends of every delve in chat, and the closing line carries the delve number
and the fight length to a tenth of a second. That is what drives the plugin, so the delve number
on the overlay is the one the game just announced and the times agree with the game's own.

A run starts when you drop into delve 1 and is then anchored on the moment the game starts its own
clock, about two seconds after the chat line. Without that the first delve would carry the walk in
and read a couple of seconds longer than the duration the game reports for the same delve.

Delve segments are contiguous with no gaps. A delve's time runs from the moment the previous delve
was cleared, so restocking, eating and dropping down the hole are charged to the delve they
precede. That makes the segments sum to the total run time. The clock is wall clock and never
pauses.

A run ends when you die, when you claim loot or leave from the end of delve panel, or when you
otherwise leave the cave. **Dying part way into a delve costs you nothing that was already
banked** - the reported total is the time through the previous delve, and the partial delve is
discarded from every figure.

A connection that drops does not end the run. Hopping and logging out put you back outside the
entrance, but a reconnect can land you in the delve you were already in, so the plugin waits until
it can see where the game has put you: back in the delve and the run carries on, back outside and
it ended where you were last seen.

## The two paces

Pick one in the config; it drives both the overlay and the chat messages.

| Mode | Formula | Answers |
|---|---|---|
| **Deep pace** (default) | `3600 / mean(delve 9+ times)` | How fast are my deep delves right now |
| **Run pace** | `deep delves (8+) / total run time` | How many deep delves per hour am I actually banking |

Delve 8 counts as a deep delve for Run pace, but is excluded from the Deep pace average because
it has a different amount of health to 9 and above. Neither floor is a setting. Where deep starts
and where the health changes are facts about the fight rather than preferences, so they are
constants.

For a run to delve 20 where delves 1-7 took 8:00, delve 8 took 2:00 and delves 9-20 took 18:00:

- **Deep pace** is `40.0/hr` - twelve delves at a flat 1:30 each.
- **Run pace** is `27.9/hr` - thirteen deep delves banked across the full 28:00, warm-up included.

Run pace starts low and climbs as the shallow delves amortise (13.8/hr at delve 10, 23.4/hr at
delve 15, 27.9/hr at delve 20). Deep pace stays flat as long as your delve times do.

Both are built on the contiguous segments, so the time you spend restocking counts against you.
A delve fought in 1:28 after a two minute restock costs 3:28 of pace. The fight length the game
reported is shown separately in the chat message.

## Overlay

While a run is going:

```
Doom Metrics
Delve             14
Time           21:40
Deep pace    40.0/hr
```

Once it ends the panel stays up for 30 minutes rather than vanishing, so the numbers are still
there when you get back from your gravestone:

```
Doom Metrics
Died on      Delve 7
Cleared            6
Time           11:17
Deep pace          -
```

The time shown for a finished run is the time through the last delve you cleared, so it always
matches the pace beside it. Right click the overlay and pick **Clear** to dismiss it early, or set
the linger to 0 to hide it straight away. Each row can be turned off.

A run the plugin joined part way through - by being enabled mid trip - labels its timer `Time*`,
because its start time is a guess.

## The delve you are aiming for

Switch on **Show target delve** and the overlay and the side panel gain two rows: the delve you set
as a target, and how much longer this run has to go to reach it.

```
Doom Metrics
Delve             14
Time           21:40
Deep pace    40.0/hr
Target            50
Predicted      54:00
```

The prediction is what your delve 9+ average says the delves between here and there will take, so
it reads `-` until this run has cleared a delve 9, and `Reached` once the target is behind you. The
delve in progress is charged against it as it goes, so the figure counts down second by second
rather than sitting still between clears, and it never drops below what the delves still to come
must take.

Two things a flat average cannot know are left in on purpose, because every other figure here is a
flat average too: delves 1-8 are quicker than the mean, so a target set during the warm-up reads
long, and delves get slower the deeper they go, so a distant target reads short.

Landing on the target is announced in chat whatever the chat interval says, including when the
messages are switched off altogether.

## Counters

The plugin can also count what your gear and spellbook gave back. Every counter is off by default;
tick the ones you want under **Counters** and they appear on the overlay under the pace, in the
side panel's table, and as a line on the history chart.

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

Each figure is drawn in the colour of what it is counted in - hitpoints red, prayer blue, damage
yellow - so which lines are which is legible without reading the labels. A counter that has not
fired yet stays grey at zero rather than disappearing, so the overlay does not resize under you
mid-delve and a spec you expected to be firing is visibly not.

**Group counters** decides how the ticked ones are drawn. `Separate` gives each its own line;
`Combined` folds them into one line per group, so ticking the ancient godsword and the blowpipe
gives a single `Spec heals` figure.

```
Separate                       Combined
Barrage        1,204           Spell heals    1,204
AGS              316           Spec heals       316
Eldritch         180           Prayer           180
ZCB           12,470           Spec dmg      12,470
```

### What is not counted

The game does not say what caused a heal. A blood barrage heal, a blowpipe spec heal and a bite of
a saradomin brew are the same hitpoints going up. What the game does say is when your special
attack energy moved, what was equipped when it did, and when a blood spell landed, so the plugin
credits an effect to whichever of those it can be pinned on and **drops anything it cannot**.

Brews, food, regeneration and prayer potions are therefore missing from these figures, and that is
the point rather than a shortcoming: a counter that swallowed them would report sustain your gear
never earned. Every number here is a floor - what could be proven - and never an over-count.

## Chat

A message is posted whenever the delve number is a multiple of the configured interval, skipping
shallow delves - so the default of 5 reports at delve 10, 15, 20 and so on.

```
[Doom] Delve 20 in 1:30.0 | 28:00 elapsed | 27.9/hr
[Doom] Target reached | Delve 50 in 1:31.2 | 1:14:20 elapsed | 38.4/hr
[Doom] Cleared delve 20 | 28:00 | 27.9/hr
[Doom] Died on delve 21 | cleared 20 in 28:00 | 27.9/hr
```

The first figure is the fight itself as the game timed it; the elapsed figure is the whole run.
Set the interval to 0 to turn the messages off.

A pace of `-` means there is nothing to average yet - Deep pace needs a delve 9 or deeper.

## Side panel

Behind the chevron icon, top to bottom:

- **Current run** - the same rows as the overlay, so the numbers are somewhere other than over the
  game world.
- **This session** - how long this sitting has been going, its deep pace, how many deep delves it
  has banked, and the counters you have ticked. A sitting ends after half an hour without a run,
  which is long enough that banking and walking back never break it and short enough that coming
  back tomorrow starts you clean.
- **Lifetime** - the same rate and delve count over everything this character has ever done.
- **Milestones** - the lifetime table, below.
- **Open history** - the history window.

## Milestones

Every tenth delve gets a row in a lifetime table.

```
Milestones
Delve     KC        PB
10        77    9:00.0
20        76   19:00.0
...
170        1 2:52:00.0
```

A row appears the first time you clear that delve and never goes away, so the rows are the
milestones you have reached. **KC** counts the clears. **PB** is the shortest time from the start
of a run through to that clear - the same span the run timer measures, restocking included, not
the sum of the fight lengths. One run to delve 172 therefore touches every row from 10 to 170,
because it cleared every delve below the one it died on.

The table is stored against the logged-in character, so an alt keeps its own, and it survives
client restarts and updates. Times are held in game ticks, the unit the game counts delves in, and
shown as hours, minutes, seconds and a tenth. Tick resolution means that tenth only ever lands on a
multiple of six.

A personal best beaten since the client started is shown in green.

### Delves you reached before installing

The game remembers your deepest delve ever, so the first time a character logs in the rows up to
it are marked as reached. They arrive with no KC and no PB - nothing is invented, they just stop a
returning player being told they have never been past delve 10.

### Runs the plugin joined part way through

A run the plugin did not see from delve 1 has a start time that is too late, and left alone would
hand out a personal best nobody earned. Instead its time is measured from a moment the run
provably had not begun by: you cannot drop back into the Doom past delve 1, so the run started
after you logged in, which in turn was after the client started. The clear still counts towards KC.

That makes the time too long rather than too short, and a time that is too long simply never wins.
If you logged in at the cave and switched the plugin on mid-trip the bound is tight enough that a
genuine best still stands; if the client had been open for hours it is loose, and that run quietly
fails to set one.

## History

Every finished run is written down, and **Open history** puts the record in a window of its own -
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

One dot per run, oldest first, with a rolling average through them - a plain line is a solid band
once there are a few hundred runs, and the trend disappears into it. The dropdown picks what the
dots measure: how deep the run got, or any one of the counters. Beside the chart are this
character's lifetime counter totals and the milestone table.

Runs are kept in `.runelite/doommetrics/`, one file per character, one JSON line per run. A line
holds when the run ended, how deep it got, how long that took, how it ended, and what the counters
recorded. Appending costs the same on the ten thousandth run as on the first, and a write torn by a
crash costs the last line rather than the whole history, so nothing is ever discarded to keep the
file small - twenty thousand runs is about a megabyte.

The notable drops from each run - the eye, avernic treads, mokhaiotl cloth and the pet - are
written to that file too, though nothing displays them yet. Only those are listed: the supplies and
currency the Doom hands out say nothing about how the trip went. The pile is read when you claim
it, never before, because an unclaimed pile is not yet yours.

## Config

| Setting | Default | Notes |
|---|---|---|
| Pace | Deep pace | Which figure the overlay and chat show |
| Chat every N delves | 5 | 0 disables the messages |
| Announce run end | on | Summary on claim, leave or death |
| Show delve number / run timer / pace | on | Overlay rows |
| Keep result for | 30 min | How long a finished run stays on screen; 0 hides it at once |
| Show target delve | off | Adds the target and predicted rows |
| Target delve | 50 | The delve being aimed for |

### Counters

| Setting | Default | Notes |
|---|---|---|
| Group counters | Separate | One line per counter, or one per group |
| Blood barrage heal | off | Hitpoints healed by blood spells |
| Other spell heal | off | Hitpoints healed by your other spells |
| AGS heal | off | Hitpoints healed by the ancient godsword spec |
| Blowpipe heal | off | Hitpoints healed by the blowpipe spec |
| Other spec heal | off | Hitpoints healed by your other specs |
| Eldritch prayer | off | Prayer points restored by the eldritch staff spec |
| ZCB damage | off | Damage dealt by the zaryte crossbow spec |
| Other spec damage | off | Damage dealt by your other specs |

### Advanced

| Setting | Default | Notes |
|---|---|---|
| Debug logging | off | Logs every Doom varplayer change and delve transition |

## Development

`src/test/resources/logs` holds a filtered slice of a real client log covering several trips,
along with a README recording what each Doom varplayer turned out to mean. The delve message
parsing is tested against strings taken from it, so a change to the game's wording fails a test
rather than quietly miscounting.

```
./gradlew test           run the suite
./gradlew preview        the overlay, panel and history window, with no game under them
./gradlew previewShots   a picture of every one of those states, into build/preview
```

The preview harness draws the interfaces against fixed scenes - mid-run, just died, everything
switched off, a character with nothing behind them - so a change to how they read can be looked at
without going delving for it. `previewShots` writes the same set to disk, which makes a before and
an after of every state at once, including the ones you would not have thought to open.

## License

BSD-2-Clause.

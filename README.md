# Doom of Mokhaiotl Metrics

Times every delve in a Doom of Mokhaiotl run, shows your deep delve completions per hour, counts
what your gear gave back while you were down there, breaks the run down delve by delve in a window
of its own, and keeps a lifetime record of every tenth delve you have reached.

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

A run ends when you die, when you claim loot - to your inventory or straight to the bank - or
leave from the end of delve panel, or when you
otherwise leave the cave. **Dying part way into a delve costs you nothing that was already
done** - the reported total is the time through the previous delve, and the partial delve is
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
| **Full pace** | `deep delves (8+) / total run time` | How many deep delves per hour am I actually completing |

Delve 8 counts as a deep delve for Full pace, but is excluded from the Deep pace average because
it has a different amount of health to 9 and above. Neither floor is a setting. Where deep starts
and where the health changes are facts about the fight rather than preferences, so they are
constants.

For a run to delve 20 where delves 1-7 took 8:00, delve 8 took 2:00 and delves 9-20 took 18:00:

- **Deep pace** is `40.0/hr` - twelve delves at a flat 1:30 each.
- **Full pace** is `27.9/hr` - thirteen deep delves completed across the full 28:00, shallow delves included.

Full pace starts low and climbs as the shallow delves amortise (13.8/hr at delve 10, 23.4/hr at
delve 15, 27.9/hr at delve 20). Deep pace stays flat as long as your delve times do.

Both are built on the contiguous segments, so the time you spend restocking counts against you.
A delve fought in 1:28 after a two minute restock costs 3:28 of pace. The fight length on its own is
what the game posts in chat, and what the run detail's time strip draws as the kill time.

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
the linger to 0 to hide it straight away - either way the run detail window, if you have it open,
goes on showing the run. Each row can be turned off.

A run the plugin joined part way through - by being enabled mid trip - labels its timer `Time*`,
because its start time is a guess.

## One square instead

Set **Display** to `Infobox` and the panel comes down, replaced by a single infobox: the plugin
icon with one figure over it, sat in the infobox bar with everything else you have up there.

A square holds one number, so **Infobox figure** picks which. It can be the delve you are on, the
run timer, the pace, the time left to your target delve or the predicted time of the whole run to
it, any of the eight counters, or any of the five counter headings with its sources summed. The
figures are shortened to fit: `1.2k` for a counter past a thousand, `1h23` for a run past the hour
and `10h` past ten of them, and `40.1` for a pace. What was dropped to make them fit is in the
tooltip - the unit, the full precision, and whether the run is one the plugin saw the start of.

**Time to target** is the panel's `To go` row in a square, counting down to the delve set under
**Target delve**, and **Predicted run time** is its `Total` row. Both read that setting whether or
not **Show target delve** is switched on, so the delve you are aiming for is set in one place
whichever is drawing it, and both show a dimmed `-` until this run has cleared a delve 9 to
average. Once the target is behind you Time to target has nothing left to count down and its square
comes down, while Predicted run time keeps the time the run took to get there.

Counters keep the colours they have on the panel, red for hitpoints, blue for prayer and yellow
for damage, and one still at zero is drawn grey - so a spec you expected to be firing is visibly
not. A counter that has just gained shows the gain for a few seconds, as on the panel - see
[Counters](#counters). The counter checkboxes have no say here: they choose which lines the panel
draws, and a square has one line. Right click it and pick **Clear** to dismiss a finished run, as on the panel.

**Display** set to `Off` draws nothing over the game at all. Nothing else changes: delves are
still timed, the counters still count, the chat messages still arrive, and the side panel and the
run detail still fill up.

## The delve you are aiming for

Switch on **Show target delve** and the overlay and the side panel gain the delve you set as a
target and a prediction for it. **Prediction** picks which: `Full run`, the default, shows `Total` -
what the whole run will have taken when it lands on the target; `Remaining` shows `To go` - how much
longer this run has to go; `Both` shows the two.

```
Doom Metrics
Delve             14
Time           21:40
Deep pace    40.0/hr
Target            50
Total        1:15:40
```

The prediction is what your delve 9+ average says the delves between here and there will take, so
it reads `-` until this run has cleared a delve 9. The delve in progress is charged against it as it
goes, so `To go` counts down second by second rather than sitting still between clears, and it never
drops below what the delves still to come must take. `Total` is the time so far with `To go` added,
so it holds still while a delve keeps to the average and climbs while one overruns it.

Once the target is behind you the row reads `Target reached`, `To go` is dropped, and `Total` keeps
the time the run actually took to get there. A run the plugin joined part way through shows
`Total*`, since its start is a guess, and `Reached` in place of a time if the target was cleared
before it was joined.

Two things a flat average cannot know are left in on purpose, because every other figure here is a
flat average too: delves 1-8 are quicker than the mean, so a target set during delves 1-8 reads
long, and delves get slower the deeper they go, so a distant target reads short.

Landing on the target is announced in chat whatever the chat interval says, including when the
messages are switched off altogether.

## Counters

The plugin can also count what your gear and spellbook gave back. Every counter is off by default;
tick the ones you want under **Counters** and they appear on the overlay under the pace and in the
side panel's table. The run detail chart draws all eight whatever the checkboxes say - it is a
window you opened to look at one run in full, and a counter you had not thought to tick is exactly
the thing worth finding there.

| Counter | Group | Counted in |
|---|---|---|
| Blood barrage | Spell healing | hitpoints healed |
| Ancient godsword | Spec healing | hitpoints healed |
| Blowpipe | Spec healing | hitpoints healed |
| Eldritch staff | Prayer restored | prayer points restored |
| Zaryte crossbow | Spec damage | damage dealt |
| Scythe of vitur | Punish damage | damage dealt |
| Noxious halberd | Punish damage | damage dealt |
| Crystal halberd | Punish damage | damage dealt |

Every counter names one weapon or spell. What falls outside them - other healing spells, other
specs' heals and damage, and punishes with any other melee weapon - is still tallied and saved with
the run, but is not drawn anywhere, and is not added into a heading's figure either: a combined
line or a heading's infobox square adds up the counters listed under it and nothing else.

**Punish damage** is what a melee punish hit for. When the boss prays against magic and ranged,
the melee swing that answers it lands with full accuracy and brings strength-bonus hitsplats in
behind it, and the swing and those hitsplats are both counted, under the weapon that swung. The
scythe and both halberds have a row each; a punish with any other melee weapon is tallied but not
shown. A spec swung at a punish, the halberds' included, is
counted here rather than under spec damage.

A swing counts if the boss was praying when it was made, or if it cut the boss's beam off. The
second catches a punish landed so early that the prayer never shows: it brings no strength-bonus
hitsplats, but its hits are the punish all the same. Only what lands in the three ticks after the
swing is counted, and no weapon can swing again inside that, so nothing thrown or cast once the
punish is over is taken for part of it.

Each figure is drawn in the colour of what it is counted in - hitpoints red, prayer blue, damage
yellow - so which lines are which is legible without reading the labels.

A counter that has not counted anything yet is left off the overlay, so you can tick everything
your gear might use and only see the lines that are firing; each one appears the first time it
counts. Untick **Hide counters at 0** to draw every ticked counter from the start, grey at zero -
the overlay then never resizes mid-delve, and a spec you expected to be firing is visibly not.

A counter that has just gained shows the gain for five ticks before going back to the run's
total. A scythe punish that hits for 30 and brings 67 in strength-bonus hitsplats turns `500` into
`+97`, then `597`. Anything landing while a gain is on show adds to it and starts the five ticks
over, so one punish reads as one gain rather than as its hitsplats one at a time.

**Group counters** decides how the ticked ones are drawn. `Separate` gives each its own line;
`Combined` folds them into one line per group, so ticking the ancient godsword and the blowpipe
gives a single `Spec heals` figure.

```
Separate                       Combined
Barrage        1,204           Spell heals    1,204
AGS              316           Spec heals       316
Eldritch         180           Prayer           180
ZCB           12,470           Spec dmg      12,470
Scythe         1,836           Punish dmg     1,836
```

**Icons for counters** draws each separate counter as the icon of what it counts instead of its
name: the blood barrage spell, the zaryte crossbow, the scythe and so on, as the game draws them.
An icon is a line of text high,
so switching it on moves no rows. A combined line keeps its heading, since it sums several
sources. It is off by default. With it on, the infobox square wears the icon too when it holds a
single counter; a delve number, a clock or a group total keeps the plugin's own icon.

### What is not counted

The game does not say what caused a heal. A blood barrage heal, a blowpipe spec heal and a bite of
a saradomin brew are the same hitpoints going up. What the game does say is when your special
attack energy moved, what was equipped when it did, and when a blood spell landed, so the plugin
credits an effect to whichever of those it can be pinned on and **drops anything it cannot**.

Brews, food, regeneration and prayer potions are therefore missing from these figures, and that is
the point rather than a shortcoming: a counter that swallowed them would report sustain your gear
never earned. Every number here is a floor - what could be proven - and never an over-count.

Spec and punish damage are only counted on the boss itself, standing or burrowed. Larvae, volatile earth and
the boss behind its demonic shield are all worth a spec, but not for the damage, so a spec fired at
one of them is spent and adds nothing. Nor is the auto-attack either side of a spec: a hit only
counts if it lands when that weapon's spec could have.

## Chat

A message is posted whenever the delve number is a multiple of the configured interval, skipping
shallow delves - so the default of 5 reports at delve 10, 15, 20 and so on.

```
Doom delve 20 cleared, run time: 28:00, deep pace: 40.0/hr.
Doom target delve 50 reached! Run time: 1:14:20, deep pace: 38.4/hr.
Doom run over: cleared delve 20 in 28:00, full pace: 27.9/hr.
Doom run over (died): cleared delve 20 in 28:00, full pace: 27.9/hr.
```

They are worded the way the game words its own delve messages, with the figures in the chat's
highlight colour. The run time is the whole run; the fight on its own is in the game's message just
above. The pace is the one **Pace** is set to, and is named after it - except at the end of a run,
which always gives its full pace, since a run that is over is not adding deep delves at any speed.

Set the interval to 0 to turn the delve messages off. Reaching the target is announced whatever the
interval says, and the end of a run has its own **Announce run end** setting.

A pace of `-` means there is nothing to average yet - Deep pace needs a delve 9 or deeper.

## Side panel

Behind the chevron icon, top to bottom:

- **Current run** - the same rows as the overlay, so the numbers are somewhere other than over the
  game world.
- **This session** - how long this sitting has been going, its deep pace, how many deep delves it
  has completed, and the counters you have ticked. A sitting ends after half an hour without a run,
  which is long enough that banking and walking back never break it and short enough that coming
  back tomorrow starts you clean.
- **Lifetime** - the same rate and delve count over everything this character has ever done.
- **Milestones** - the lifetime table, below.
- **Open run detail** - this run, delve by delve, in a window of its own.

The counters in the panel are always listed by icon, with the name one hover away.

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

## Run detail

**Open run detail** puts one run in a window of its own - the one you are on, or the last one you
finished - broken down delve by delve. A side panel has no room for a chart; this does.

```
This run              Per delve
Delve      Time       Counted per delve
  24      21:02       140 |            ZCB /\   /\
Deep pace  58.1/hr        |  __       /  \_/  \
                       70 | /  \_ Barrage      \
Counters                  |/     \__/‾‾\__/‾‾\__\
             This run    0 |__Eldritch__________
Spell healing               |
 ■ Blood barrage    806     | 2:00
Spec healing                | ▁▂▃▃▄▄▅▅▆▆▇▇  Full time
 ■ Ancient godsword  58     | ▁▁▂▂▃▃▄▄▅▅▆▆  Kill time
 ■ Blowpipe          47   0:00
 ...                        1     5    10    15
                                    Delve
```

The upper plot is the counters, a line each, over the delves the run has completed. They share one
scale because none of them clears a few hundred on a single delve, so a barrage heal and a Zaryte
spec are like sizes and can be read against each other. Which unit a line is counted in is on the
legend, under the heading it is listed beneath - hitpoints, prayer points or damage, the same five
headings the side panel uses.

The chart's colours are eight hues checked as a set for colour blindness, one for each of the eight
counters, so no two lines share a colour.

The lower strip is the clock: how long each delve took, and under it the fight the game timed. The
band between them is everything the delve cost that was not the fight - the restocking, the walk
in and the drop down the hole. It is a second plot rather than a second scale on the first, because
seconds and hitpoints have no honest common axis, and lining the two up on one delve axis is the
whole point of stacking them.

**Point at a delve** and a line marks it across both plots while the column beside the counters
switches from the run's totals to that delve's. Every figure on the chart is therefore also written
down, and reading one never depends on landing the pointer on a two pixel dot.

**Point at a counter's name** to bring its line forward and push the others back, and click it
to take the line off altogether - which also gives the counters left on the plot the height they
were sharing with it. A colour belongs to a counter for as long as the window is open, so switching
one off never repaints the rest. With **Hide counters at 0** on, as it is by default, a counter the run has not
counted anything on starts switched off, so the chart is not crowded with flat lines along the
bottom; it comes on by itself once it counts, and a click puts it on sooner. The legend lists each counter by its icon; hovering the row names
it.

### Drops

The eye, avernic treads, mokhaiotl cloth and the pet are drawn as their icons in a lane over the
counters, each above the delve it came off, and listed under **Drops** beside the chart. A drop is
placed when the run first learns there is one more of it than before: from the loot pile, from the
"Your loot contains" warning the game puts up as you try to descend - one per copy, every try - or,
for the pet, from the line the game posts as you claim. Only a count going up places anything, so an
eye off delve 10 is on delve 10 alone however many times you are warned about it, and a second eye
off delve 20 is on delve 20 alone.

A drop you did not walk out with - still in the pile when you died, or left behind - stays where
it dropped, faded. The pet is no exception: the game only hands it over with the claim, so a death
loses it with the rest of the pile. Drops close enough together to overlap are stacked, and
pointing at an icon or a row in the list names it, and says how a lost one was lost.

### Long runs

The world record is past delve 260 and the chart is built to go further. Past eighty delves the
markers come off - there is no longer room to hit one - and each line becomes a rolling average
with the delve-by-delve line left underneath at a fraction of the weight. A counter varies a good
deal from one delve to the next, and a dozen lines of that at three hundred delves fill in as a solid
band with no trend left in it. The average carries the trend the lines no longer can; the lines are
kept because the spread they show is real, and a chart of averages alone would say a delve cost
what the delves around it cost. The caption says which is which, and the exact figure for any one
delve is still the one you get by pointing at it.

### What is not here

Nothing is read back from disk, so the window is empty until this session has a run in it, and a
run is gone once the next one starts. What survives a restart is the milestone table and the
lifetime rate, both in the side panel where they always were.

Finished runs are still written to `.runelite/doommetrics/`, one file per character, one JSON line
per run: when the run ended, how deep it got, how long that took, how it ended, what the counters
recorded, and the notable drops - the eye, avernic treads, mokhaiotl cloth and the pet. Nothing
displays that file. It is kept because a run is impossible to recover once it is over and the
record costs about sixty bytes, so twenty thousand runs is about a megabyte. Appending costs the
same on the ten thousandth run as on the first, and a write torn by a crash costs the last line
rather than the whole file.

## Config

| Setting | Default | Notes |
|---|---|---|
| Pace | Deep pace | Which figure the overlay and chat show |
| Chat every N delves | 5 | 0 turns the delve messages off; reaching the target is still announced |
| Announce run end | on | Summary on claim, leave or death |
| Hide plugin name | off | Leaves the Doom Metrics title off the top of the overlay |
| Display | Panel | The panel of rows, one infobox square, or nothing drawn over the game |
| Infobox figure | Delve | Which single figure the square holds |
| Show delve number / run timer / pace | on | Overlay rows |
| Keep result for | 30 min | How long a finished run stays on screen; 0 hides it at once |
| Show target delve | off | Adds the target and predicted rows |
| Target delve | 50 | The delve being aimed for |
| Prediction | Full run | Which predicted times the target rows show: to go, the full run, or both |

### Counters

| Setting | Default | Notes |
|---|---|---|
| Icons for counters | off | Draw each counter as its weapon or spell icon instead of its name, in the overlay and the infobox |
| Hide counters at 0 | on | Leave a counter off the overlay until it has counted something, and start it switched off on the run detail chart |
| Group counters | Separate | One line per counter, or one per group |
| Blood barrage heal | off | Hitpoints healed by blood spells |
| AGS heal | off | Hitpoints healed by the ancient godsword spec |
| Blowpipe heal | off | Hitpoints healed by the blowpipe spec |
| Eldritch prayer | off | Prayer points restored by the eldritch staff spec |
| ZCB damage | off | Damage dealt by the zaryte crossbow spec |
| Scythe punish | off | Damage your scythe dealt punishing the boss's prayer |
| Noxious halberd punish | off | Damage your noxious halberd dealt punishing the boss's prayer, its spec included |
| Crystal halberd punish | off | Damage your crystal halberd dealt punishing the boss's prayer, its spec included |

### Advanced

| Setting | Default | Notes |
|---|---|---|
| Debug logging | off | Logs delve transitions, Doom varplayer changes and what each counter was credited, and around a delve's end the sounds, objects, menu clicks, interface text, varbits and scripts the game sends |

## Development

`src/test/resources/logs` holds a filtered slice of a real client log covering several trips,
along with a README recording what each Doom varplayer turned out to mean. The delve message
parsing is tested against strings taken from it, so a change to the game's wording fails a test
rather than quietly miscounting.

```
./gradlew test           run the suite
./gradlew preview        the overlay, panel, detail window and chat messages, with no game under them
./gradlew previewShots   a picture of every one of those states, into build/preview
```

The preview harness draws the interfaces against fixed scenes - mid-run, just died, everything
switched off, a character with nothing behind them - so a change to how they read can be looked at
without going delving for it. The chat messages are drawn as the chatbox would show them, every
kind the plugin posts filled with the scene's numbers, so their wording and colours can be judged
without waiting for the delve that sends each one. `previewShots` writes the same set to disk, which makes a before and
an after of every state at once, including the ones you would not have thought to open.

## License

BSD-2-Clause.

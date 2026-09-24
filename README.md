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
pauses. The run detail window divides the same time at a different point, to show each delve the
way it is played - see [Run detail](#run-detail).

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

It is drawn as one of the game's own panels: an orange title with the boss's icon over an ember
line, names in the game's orange, figures in white, the delve in yellow so it stands out by colour
rather than size. A faint rule separates the run's lines from the counters under them.

Once it ends the panel stays up for 30 minutes rather than vanishing, so the numbers are still
there when you get back from your gravestone. A death is written in red:

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

A run the plugin joined part way through - by being enabled mid trip, or by a session reset -
labels its timer `Time*`, because its start time is a guess. One joined part way into a delve
leaves that delve out of its pace, since nobody saw it start, so the pace reads `-` until the run
clears another.

## One square instead

Set **Display** to `Infobox` and the panel comes down, replaced by a single infobox: the plugin
icon with one figure over it, sat in the infobox bar with everything else you have up there.

A square holds one number, so **Infobox figure** picks which. It can be the delve you are on, the
run timer, the pace, the time left to your target delve or the predicted time of the whole run to
it, any of the ten counters, or any of the three counter headings with everything under it
summed - the sources with no counter of their own included. The
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
[Counters](#counters). The Healing, Prayer and Damage settings have no say here: they choose which
lines the panel draws, and a square has one line. Right click it and pick **Clear** to dismiss a finished run, as on the panel.

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
[=====---------------]
```

A thin bar under the target's lines fills as delves are cleared towards it, and turns green once it
is reached. The side panel and the run detail window draw the same bar.

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

The plugin can also count what your gear and spellbook gave back, under three headings - one for
each thing a figure can be counted in. The side panel's table and the run detail chart always
carry all ten counters; the overlay draws only what you ask of it under **Counters**, and is off
for all three headings by default.

| Counter | Heading | Counted in |
|---|---|---|
| Blood barrage | Healing | hitpoints healed |
| Ancient godsword | Healing | hitpoints healed |
| Blowpipe | Healing | hitpoints healed |
| Saradomin godsword | Healing | hitpoints healed |
| Eldritch staff | Prayer restored | prayer points restored |
| Saradomin godsword | Prayer restored | prayer points restored |
| Zaryte crossbow | Spec & punish damage | damage dealt |
| Scythe of vitur | Spec & punish damage | damage dealt |
| Noxious halberd | Spec & punish damage | damage dealt |
| Crystal halberd | Spec & punish damage | damage dealt |

Every counter names one weapon or spell. What falls outside them - other healing spells, other
specs' damage, and punishes with any other melee weapon - is still tallied and saved with
the run, and has no line of its own anywhere: there is no counter for "some other melee weapon",
so a row for it would be a row nobody asked for.

A **heading's figure is the whole group**, those included. The headings in the side panel's table,
a total overlay line, a heading's infobox square and a grouped run detail line all read what the
group counted rather than what its rows name, so punishing with an ancient godsword - which has no
counter of its own - shows up in the damage total and in no other figure. Where the two differ, the
heading's tooltip says by how much and what it was counted under.

**Punish damage**, under the damage heading, is what a melee punish hit for. When the boss prays against magic and ranged,
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

A counter that has not counted anything yet is left off the overlay, so a heading drawn counter by
counter only shows the gear you are actually using; each line appears the first time it counts.
Untick **Hide counters at 0** to draw every counter from the start, grey at zero - the overlay
then never resizes mid-delve, and a spec you expected to be firing is visibly not.

A counter that has just gained shows the gain for five ticks before going back to the run's
total. A scythe punish that hits for 30 and brings 67 in strength-bonus hitsplats turns `500` into
`+97`, then `597`. Anything landing while a gain is on show adds to it and starts the five ticks
over, so one punish reads as one gain rather than as its hitsplats one at a time.

**Healing**, **Prayer** and **Damage** each pick how their heading is drawn on the overlay:
`Off` draws nothing, `Total` draws one line with everything counted under the heading, and `Each`
draws a line per counter. They are set one heading at a time, so healing can be a single figure
while damage is broken down by weapon:

```
Each                    Total                   Healing Total, Damage Each
Barrage        1,204    Healing        1,520    Healing        1,520
AGS              316    Prayer           180    ZCB           12,470
Eldritch         180    Damage        14,306    Scythe         1,836
ZCB           12,470
Scythe         1,836
```

A total line also carries the sources with no counter of their own, so it can read higher than
the counters under it add up to.

**Counter style** picks how a counter's line is led. `Names` spells out what it counts. `Icons`
draws the icon of what it counts in place of the name - the blood barrage spell, the zaryte
crossbow, the scythe and so on, as the game draws them. An icon is a line of text high, so
switching to it moves no rows. `Icon grid` fits two of those to a line, which halves the lines a
heading drawn counter by counter takes; a figure past 9,999 is shortened to fit its half, as the
infobox shortens it (`25k`, `+1.2k`). A total line keeps its name and a line of its own in either
icon style, since it sums several sources and no one weapon stands for them; its unit's skill icon
goes before the name instead.

```
Healing        1,520
[eldritch] 180  [zcb] 12k
[scythe] 1,836  [nox]  632
```

With either icon style, the infobox square wears the icon too when it holds a single counter; a
delve number, a clock or a heading total keeps the plugin's own icon.

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
counts if it lands when that weapon's spec could have - a tick after the spec for a melee weapon,
two or more for anything that has to fly. Only the specs that heal (the ancient and Saradomin
godswords, the toxic blowpipe) are credited with a heal; a heal after any other spec is left out,
since it can only have been food, a brew or another spec's.

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

Behind the chevron icon, a stack of cards, top to bottom:

- **Current run** - the same figures as the overlay, so the numbers are somewhere other than over
  the game world: the delve and the clock large, a bar filling towards the target delve when one is
  set, and the pace and predictions as tiles under them. A pill on the card's title says whether
  the run is live, ended or died.
- **Session & lifetime** - a tile each. **Session** is how long this sitting has been going, its deep pace, how many deep delves it
  has completed. A session lasts until you close the client, log
  in as a different character, or press **Reset session**; logging out and back in carries it on,
  and its length does not count the time spent logged out. After half an hour without a run the
  rows go blank so they do not look current, and the next run brings the session back.
  **Lifetime** is the same rate and delve count over everything this character has ever done.
  A bar under each rate is filled against the faster of the two, so the longer bar says whether
  tonight is beating your usual. Both tiles, and the lifetime combat counters under them, are added to as each delve is
  cleared rather than when the run ends, so closing the client mid-run keeps every delve cleared
  before it. A run picked up part way into a delve leaves that clear out of the rates, since
  nobody saw when that delve began.
- **Combat** - what your gear gave back. Three tiles lead it with each heading's total -
  everything counted under it, including the sources with no row - so the card answers how much
  sustain there was before it says which source found it. Under them is a row per source, beneath
  the heading it belongs to - each heading led by its skill icon, hitpoints, prayer or strength -
  with a bar behind each row filled against the largest figure counted in the same unit. The
  **Session** and **Lifetime** tabs on the card's title put the
  same figures on this sitting's tally and on the character's; the bars refill against whichever is
  on show, so each says which source is carrying it on its own terms. Lifetime is worth reading
  between runs - it is the one figure here that does not go quiet when a sitting ends. Click a
  heading to fold its rows away; its total stays in the tile, and the fold is remembered across
  restarts. With **Hide counters at 0** on, as it is by default, a counter that has counted nothing
  is left out here as it is on the overlay, and comes up the moment it counts; **Show all** under
  the rows puts every one back.
- **Resets** - how the runs aimed at your target delve are going, below.
- **Milestones** - the lifetime table, below.
- **Open run detail** - this run, delve by delve, in a window of its own.
- **Reset session** - starts the session over and drops the run in progress, after asking, letting
  go of everything the plugin held about both. The dropped run is not written to history, but the
  delves it already cleared stay in the lifetime figures and milestones. Turning the plugin off
  lets go of everything it holds in memory, lifetime figures included; what is saved stays saved
  and is read back when the plugin is next on and a character is logged in.

The counters in the panel are listed by name with their icon beside it. An inventory sprite
shrunk to a line of text cannot tell three polearms apart, so the picture is there to find a row
by rather than to name it; what feeds each counter is still a hover away.

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

A deep table is kept short: every ten delves down to 50, then every 50 (100, 150, 200 ...), with
your target delve's row - in orange - and your deepest row always on show. **Show all** under the
table opens the rest, and **Show fewer** closes it again.

### Delves you reached before installing

The game remembers your deepest delve ever, so the first time a character logs in the rows up to
it are marked as reached. They arrive with no KC and no PB - nothing is invented, they just stop a
returning player being told they have never been past delve 10.

### Runs the plugin joined part way through

A run the plugin did not see from delve 1 has a start time that is too late, and left alone would
hand out a personal best nobody earned. Instead its time is measured from a moment the run
provably had not begun by: you cannot drop back into the Doom past delve 1, so the run started
after you logged in. The clear still counts towards KC.

That makes the time too long rather than too short, and a time that is too long simply never wins.
If you logged in at the cave and switched the plugin on mid-trip the bound is tight enough that a
genuine best still stands; if the client had been logged in for hours it is loose, and that run
quietly fails to set one.

## Resets

For a player who resets at a set depth, a character's deep delve count is not much to watch go
up. The Resets card follows the runs aimed at your **Target delve**, rounded down to a milestone
row, so a target of 105 is followed as 100. It follows the setting whether or not the target is
shown on the overlay.

```
Delve 100 resets
Reached  62%            Average time  1:52:10
184 / 297 runs          best 1:41:30
Died short                       41
This session             6 (2.1/hr)
Last 10           1:49:05 (-3:05)
Uniques           3 (1 per 99 runs)
```

- **Reached** - runs that cleared the target, out of every run started. Walking into delve 1 and
  straight back out is not counted as a run.
- **Average time** - from the start of a run to clearing the target, the same span as a
  milestone's PB, which is shown under it.

Only runs the plugin watched from delve 1 count towards these two. A run it joined part way
through - after a session reset, or the plugin being turned on mid-trip - may be a trip it has
already counted, and its time is only an upper bound; it still adds to the milestone KC, and can
still set a PB.
- **Died short** - deaths before clearing the target, the target delve itself included.
- **This session** - targets cleared this sitting, and how many that is an hour once the sitting
  is ten minutes old.
- **Last 10** - the latest ten runs' average time to the target, in green when it is quicker than
  your average and red when it is slower. Changing the target starts this list over.
- **Uniques** - uniques claimed, and how many runs there have been for each. A pet you already
  own counts too: the game still announces it and adds it to the collection log.

Everything on the card is a running count kept in the milestone table, not a list of runs, so it
takes the same room after ten runs as after ten thousand and is synced with the rest of your
settings. It counts from the version that added it: the KC and PB you already had are kept, but
nothing before that can be counted towards reach, deaths or averages.

## Run detail

**Open run detail** puts one run in a window of its own - the one you are on, or the last one you
finished - broken down delve by delve. A side panel has no room for a chart; this does.

```
This run       Live   Per delve
Delve      Time       Counted per delve
  24      21:02       140 |            ZCB /\   /\
Deep pace  58.1/hr        |  __       /  \_/  \
Target       23 / 50   70 | /  \_ Barrage      \
Counters                  |/     \__/‾‾\__/‾‾\__\
  Sources | Grouped     0 |__Eldritch__________
             This run     |
Healing         1,035     | 2:00
 ■ Blood barrage  806     | ▁▂▃▃▄▄▅▅▆▆▇▇  Full time
 ■ Ancient godsword 58    | ▁▁▂▂▃▃▄▄▅▅▆▆  Kill time
 ■ Blowpipe        47   0:00
 ...                      1     5    10    15
                                  Delve
```

The run's figures head the sidebar, with the same Live, Ended or Died word as the side panel and
the bar towards the target, then the drops and the legend; the chart takes the rest of the window.
Hovering a delve writes its number and its full and kill times on the chart's top edge, named in
orange.

The upper plot is the counters, a line each, over the delves the run has completed. They share one
scale because none of them clears a few hundred on a single delve, so a barrage heal and a Zaryte
spec are like sizes and can be read against each other. Which unit a line is counted in is on the
legend, under the heading it is listed beneath - hitpoints, prayer points or damage, the same three
headings the side panel uses.

The chart's colours are eight hues checked as a set for colour blindness, one for each of eight
counters. The Saradomin godsword's two, healing and prayer, reuse the noxious halberd's violet and
the scythe's green - each checked against the lines listed either side of it and against the other
- and are drawn dashed, with a split swatch in the legend, so no two lines look alike. Grouped, the three headings take the first three of the
same hues, for the same reason: only neighbouring slots were checked against each other, so a set drawn
together has to be a run of them from the first.

**Sources** and **Grouped**, on the Counters heading, pick how the run is read. Sources is a line
per counter; Grouped is a line per heading, the counters under it added up, which is how a run long
enough to fill the plot stays legible - three lines instead of ten, and each of them the figure
you were going to add up anyway. A grouped line counts what no counter names as well, so it is the
only place on the chart a punish thrown with an unnamed weapon appears. Which lines you have clicked off
is remembered for each of the two, so switching back finds the chart as you left it. This is a
setting of the window rather than of the plugin: the overlay's own **Healing**, **Prayer** and
**Damage** settings are untouched by it.

Reading by source, click a heading in the legend to fold its rows away and leave just its total,
as in the side panel. Folding only tidies the legend: the chart keeps those lines, and clicking a
row is still what takes one off. The fold is remembered across restarts, separately from the side
panel's.

The lower strip is the clock: each delve's full time, and under it the kill the game timed. A delve
here is the kill and then getting ready for the next, so its full time runs from the delve starting
to the next one starting, and the band between the two lines is the wait after the kill - the
restocking, the specs fired before going down, the drop down the hole. What is counted in that wait
is on the delve just killed as well: an eldritch spec fired at a leftover volatile earth after
delve 11 is delve 11's. A delve's column is final once the next delve starts, or once the run ends
in its wait, a claim included. The delve you die on has no kill, so it is not charted, and what it
counted is in the overlay and the side panel but not here.

Only the run detail divides a run this way. Pace, predictions and the chat messages charge a wait
to the delve that follows it, so a delve's time is known the moment it is cleared; over a run the
two come to the same time. The strip is a second plot rather than a second scale on the first,
because seconds and hitpoints have no honest common axis, and lining the two up on one delve axis is
the whole point of stacking them.

**Point at a delve** and a line marks it across both plots while the column beside the counters
switches from the run's totals to that delve's. Every figure on the chart is therefore also written
down, and reading one never depends on landing the pointer on a two pixel dot.

**Point at a counter's name** to bring its line forward and push the others back, and click it
to take the line off altogether - which also gives the counters left on the plot the height they
were sharing with it. A colour belongs to a counter for as long as the window is open, so switching
one off never repaints the rest. With **Hide counters at 0** on, as it is by default, a counter the run has not
counted anything on starts switched off, so the chart is not crowded with flat lines along the
bottom; it comes on by itself once it counts, and a click puts it on sooner. The legend lists each counter by name with its icon beside it, as the side panel does; hovering
the row says what feeds it.

### Drops

The eye, avernic treads, mokhaiotl cloth and the pet are drawn as their icons in a lane over the
counters, each above the delve it came off, and listed under **Drops** beside the chart. A drop is
placed when the run first learns there is one more of it than before: from the loot pile, from the
"Your loot contains" warning the game puts up as you try to descend - one per copy, every try - or,
for the pet, from the line the game posts as you claim. Only a count going up places anything, so an
eye off delve 10 is on delve 10 alone however many times you are warned about it, and a second eye
off delve 20 is on delve 20 alone.

A hole left glowing by a unique you have not seen yet is drawn as a gold question mark on the
delve it was cleared by, and says in the list what it could be. The glow is the only word the game
gives a client before you investigate the pile, try to descend or claim, so the mark is what stands
there until one of those names the drop - at which point the real item takes its place, on the
delve the glow put it on. Only one is ever drawn at a time: the hole goes on glowing for as long as
a unique sits unclaimed in the pile, so a glow over one you already know about is saying nothing
new. The pet is the exception: only the first one a character is ever given lights the hole, and
every duplicate after that lands in the pile without lighting anything, so a pet the hole never
glowed for is not held against a later glow. A mark still standing when the run ends was a drop the
run lost, since every way of walking out with it would have named it. A run picked up part way through - the plugin switched on, or the
client restarted, mid-delve - marks a glowing hole on the delve it was cleared by, which is the
only delve such a run can name: the drop may well have come off one nobody watched.

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

### Overlay

| Setting | Default | Notes |
|---|---|---|
| Display | Panel | The panel of rows, one infobox square, or nothing drawn over the game |
| Infobox figure | Delve | Which single figure the square holds |
| Hide plugin name | off | Leaves the Doom Metrics title off the top of the overlay |
| Show delve number / run timer / pace | on | Overlay rows |
| Keep result for | 30 min | How long a finished run stays on screen; 0 hides it at once |

### Pace & target

| Setting | Default | Notes |
|---|---|---|
| Pace | Deep pace | Which figure the overlay and chat show |
| Show target delve | off | Adds the target and predicted rows |
| Target delve | 50 | The delve being aimed for |
| Prediction | Full run | Which predicted times the target rows show: to go, the full run, or both |

### Counters

| Setting | Default | Notes |
|---|---|---|
| Healing | Off | Blood spells and the ancient godsword and blowpipe specs: off, one total line, or a line each |
| Prayer | Off | The eldritch staff and Saradomin godsword specs: off, one total line, or a line each |
| Damage | Off | The zaryte crossbow spec and your scythe and halberds punishing: off, one total line, or a line each |
| Counter style | Names | Lead each counter's line with its name, its icon, or its icon two to a line; the infobox takes the icon too |
| Hide counters at 0 | on | Leave a counter off the overlay and the side panel until it has counted something, and start it switched off on the run detail chart |

### Chat

| Setting | Default | Notes |
|---|---|---|
| Chat every N delves | 5 | 0 turns the delve messages off; reaching the target is still announced |
| Announce run end | on | Summary on claim, leave or death |

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

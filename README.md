# Doom of Mokhaiotl Metrics

Times every delve in a Doom of Mokhaiotl run and shows your deep delve completions per hour.

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

## The two paces

Pick one in the config; it drives both the overlay and the chat messages.

| Mode | Formula | Answers |
|---|---|---|
| **Deep pace** (default) | `3600 / mean(delve 9+ times)` | How fast are my deep delves right now |
| **Run pace** | `deep delves (8+) / total run time` | How many deep delves per hour am I actually banking |

Delve 8 counts as a deep delve for Run pace, but is excluded from the Deep pace average because
it has a different amount of health to 9 and above. Both floors are configurable.

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
Run            21:40
Deep pace   40.0/hr
```

Once it ends the panel stays up for 30 minutes rather than vanishing, so the numbers are still
there when you get back from your gravestone:

```
Doom Metrics
Died on      Delve 7
Cleared            6
Run            11:17
Deep pace          -
```

The time shown for a finished run is the time through the last delve you cleared, so it always
matches the pace beside it. Right click the overlay and pick **Clear** to dismiss it early, or set
the linger to 0 to hide it straight away. Each row can be turned off.

A run the plugin joined part way through - by being enabled mid trip - labels its timer `Run*`,
because its start time is a guess.

## Chat

A message is posted whenever the delve number is a multiple of the configured interval, skipping
shallow delves - so the default of 5 reports at delve 10, 15, 20 and so on.

```
[Doom] Delve 20 in 1:30.0 | 28:00 elapsed | 27.9/hr
[Doom] Cleared delve 20 | 28:00 | 27.9/hr
[Doom] Died on delve 21 | cleared 20 in 28:00 | 27.9/hr
```

The first figure is the fight itself as the game timed it; the elapsed figure is the whole run.
Set the interval to 0 to turn the messages off.

A pace of `-` means there is nothing to average yet - Deep pace needs a delve 9 or deeper.

## Config

| Setting | Default | Notes |
|---|---|---|
| Pace | Deep pace | Which figure the overlay and chat show |
| Chat every N delves | 5 | 0 disables the messages |
| Announce run end | on | Summary on claim, leave or death |
| Show delve number / run timer / pace | on | Overlay rows |
| Keep result for | 30 min | How long a finished run stays on screen; 0 hides it at once |
| Deep delve from | 8 | Gates the chat messages, numerator for Run pace |
| Average pace from | 9 | First delve in the Deep pace average |
| Debug logging | off | Logs every Doom varplayer change and delve transition |

## Development

`src/test/resources/logs` holds a filtered slice of a real client log covering several trips,
along with a README recording what each Doom varplayer turned out to mean. The delve message
parsing is tested against strings taken from it, so a change to the game's wording fails a test
rather than quietly miscounting.

## License

BSD-2-Clause.

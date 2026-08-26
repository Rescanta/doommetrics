# Doom of Mokhaiotl Metrics

Times every delve in a Doom of Mokhaiotl run and shows your deep delve completions per hour.

## How delves are timed

Delve segments are contiguous with no gaps. A delve's time runs from the moment the previous
delve was cleared, so restocking, eating and walking to the hole are charged to the delve they
precede. The first segment starts when the run does. The clock is wall clock and never pauses.

A run ends when you claim loot, leave, or die. **Dying part way into a delve costs you nothing
that was already banked** - the reported total is the time through the previous delve, and the
partial delve is discarded from every figure.

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

## Overlay

```
Doom Metrics
Delve             14
Run            21:40
Deep pace   40.0/hr
```

Shown only while a run is active. Each row can be turned off.

## Chat

A message is posted whenever the delve number is a multiple of the configured interval, skipping
shallow delves - so the default of 5 reports at delve 10, 15, 20 and so on.

```
[Doom] Delve 20 | 28:00 elapsed | 27.9/hr
[Doom] Run complete: delve 20 | 28:00 | 27.9/hr
[Doom] Died on delve 21 | cleared 20 in 28:00 | 27.9/hr
```

Set the interval to 0 to turn the messages off.

## Config

| Setting | Default | Notes |
|---|---|---|
| Pace | Deep pace | Which figure the overlay and chat show |
| Chat every N delves | 5 | 0 disables the messages |
| Announce run end | on | Summary on claim, leave or death |
| Show delve number / run timer / pace | on | Overlay rows |
| Deep delve from | 8 | Gates the chat messages, numerator for Run pace |
| Average pace from | 9 | First delve in the Deep pace average |
| Debug logging | off | Logs every Doom varplayer change and delve transition |

## License

BSD-2-Clause.

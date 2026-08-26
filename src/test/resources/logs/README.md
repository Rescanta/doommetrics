# Recorded Doom of Mokhaiotl events

`doom-delve-events-2026-08-26.log` is a filtered slice of a real `~/.runelite/logs/client.log`,
covering several trips into the Doom of Mokhaiotl on 2026-08-26: a 15 delve run, a 13 delve run,
three deaths, a walk in and straight back out, and a delve 1 clear followed by claiming and leaving.

It exists so the delve tracking can be checked against strings the game actually emitted rather than
ones typed from memory. `DelveMessageTest` quotes from it directly.

## What is in it

Three kinds of line, in the order the client saw them:

- `Chat message type GAMEMESSAGE: ...` - what the game announced, with its colour templates
  (`@mes_hl_red@`, `</col>`) left intact, because those are exactly what the parser has to survive.
- `Doom varp N -> V` - every Doom varplayer change, logged by the plugin's debug toggle.
- Other `DoomMetricsPlugin` lines - what the plugin decided at the time. Note that lines before
  roughly 21:20 come from the older, broken build, so they are a record of the bug rather than of
  correct behaviour.

## Delve boundaries

Each delve is bracketed by two game messages. The closing one carries the delve number and the
fight length to a tenth of a second:

```
@mes_hl_red@Delve level: 3</col>
Delve level: 3 duration: @mes_hl_red@1:05.40</col>. Personal best: @mes_hl_red@0:37.80</col>
```

Cases in here worth keeping a parser honest:

| Line | Why it matters |
| --- | --- |
| `Delve level: 8+ (15)` | past delve 8 the real number moves into the brackets |
| `... duration: 0:23.40 (new personal best)` | no `Personal best:` clause on this variant |
| `Delve level 1 - 8 duration: 9:35.40` | no colon after `level`; a milestone, not a delve clear |
| `Total duration: 4:47.40` | running total posted after delves 1-7, not an end of trip marker |

## Varplayers

Decoded by lining these up against the chat messages above:

| Varp | Name | Meaning |
| --- | --- | --- |
| 4798 | `DOM_LAST_DELVE_LEVEL` | delve just cleared, minus one. Persists across logins, so it is set during the login varp flood - never treat it as a run start. |
| 4803 | `DOM_TOTAL_DURATION` | cumulative fight ticks this trip, excluding time between delves. Resets just before entry. |
| 4804 | `DOM_LAST_LEVEL_DURATION` | ticks the delve just cleared took. `151` ticks matches the `1:30.60` the game reported. |
| 4805 | `DOM_LEVEL_START_TIME` | server tick the current delve began. Lands about two seconds after the chat line and is the value the game's own duration is measured from. |
| 4828 | `DOM_CURRENT_LEVEL_TEMP` | delves descended to this trip. Zero for the whole of delve 1, and cleared on leaving. |

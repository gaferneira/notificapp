# Delta for Snooze Scheduling

## ADDED Requirements

### Requirement: Throttle mode delivers the first match and opens a window
The system SHALL support a `THROTTLE` snooze mode configured with a window duration (5m / 10m / 30m / 1h / custom). When a rule with an enabled `THROTTLE`-mode action matches and no prior delivery is recorded for that rule+app scope within the window, the match SHALL deliver (post normally) and start a new window timed from that delivery.

#### Scenario: First-ever match always delivers
- **WHEN** a `THROTTLE`-mode rule+app scope has no prior recorded delivery
- **THEN** the match delivers immediately
- **AND** a new window opens starting at that delivery time

#### Scenario: Match after the window has elapsed delivers and reopens
- **WHEN** a match arrives for a rule+app scope whose last delivery was strictly before `now - window`
- **THEN** the match delivers
- **AND** a fresh window opens from this new delivery

### Requirement: Throttle mode drops matches within an open window
Every match for the same rule+app scope arriving after the first delivery and before the window elapses SHALL be dropped (via `cancel()`) and SHALL NOT extend, reschedule, or queue the window; the original window's end time SHALL NOT change until the next delivered match reopens it.

#### Scenario: Match strictly inside the window is dropped
- **WHEN** a match arrives for a rule+app scope whose last delivery was less than `window` ago
- **THEN** the match is dropped
- **AND** the window's end time is unchanged

#### Scenario: Match exactly at the window boundary delivers
- **WHEN** a match arrives at exactly `lastDelivery + window`
- **THEN** the window is treated as elapsed
- **AND** the match delivers and opens a fresh window

### Requirement: Throttle scope is per rule and source app
Throttle state SHALL be scoped to the combination of rule ID and source app package, so each app a rule targets keeps an independent window/timer. A burst of matches from one app SHALL NOT suppress matches from a different app under the same rule.

#### Scenario: Independent windows per targeted app
- **WHEN** a `THROTTLE`-mode rule targets apps A and B
- **AND** app A delivers and opens its window
- **THEN** a concurrent match from app B still delivers, unaffected by app A's window

#### Scenario: Unresolvable source package falls back to rule-only scope
- **WHEN** a notification's source package cannot be resolved
- **THEN** the system SHALL use a rule-only scope key (no app component) for that match's throttle check
- **AND** throttling still functions deterministically for that fallback scope

### Requirement: Throttle check-and-set is atomic under concurrent matches
When two or more matches for the same rule+app scope arrive near-simultaneously while no window is open, the system SHALL guarantee that exactly one match wins the check-and-set and delivers; all others SHALL observe the newly-opened window and be dropped.

#### Scenario: Concurrent near-simultaneous matches
- **WHEN** two matches for the same rule+app scope with no open window are evaluated concurrently
- **THEN** exactly one of them delivers and opens the window
- **AND** the other is dropped as if it arrived inside that window

### Requirement: Throttle state persists across restart
Throttle state SHALL be durable across app/process restart using the hybrid in-memory-plus-database-lookback tracker: the in-memory map is the fast path, and a DB lookback (last delivered execution for the rule+app scope) backs it after a restart clears memory. A window open before restart SHALL still suppress matches after restart, for its remaining duration.

#### Scenario: Restart mid-window still suppresses
- **WHEN** the process restarts while a throttle window is open for a rule+app scope
- **AND** a match for that scope arrives before the window would have elapsed
- **THEN** the DB lookback recovers the last delivery time
- **AND** the match is dropped

### Requirement: Editing throttle config resets the timer
Editing the throttle window duration, or disabling and then re-enabling the `THROTTLE` action, SHALL reset that rule+app scope's timer so the next match after the edit always delivers, regardless of any window that was in flight at the time of the edit.

#### Scenario: Changing the window duration resets the timer
- **WHEN** a user edits the window duration of an enabled `THROTTLE` action while a window is open
- **AND** a new match arrives for that rule+app scope after the edit
- **THEN** the match delivers
- **AND** a fresh window opens under the new duration

#### Scenario: Disable then re-enable resets the timer
- **WHEN** a user disables a `THROTTLE` action and re-enables it while a window would otherwise still be open
- **AND** a new match arrives for that rule+app scope after re-enabling
- **THEN** the match delivers
- **AND** a fresh window opens

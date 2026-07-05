# ADR 010 – Action Execution via ActionExecutor Multibindings

## Status
Accepted (implemented 2026-07-05 — see `docs/roadmap_tech_debt.md` TD-4/TD-5)

## Context
Rule actions are executed in a `when (action.type)` block inside `NotificappListenerService` with a silent `else` for unimplemented types. Every new action (webhook, alarm, AI) requires editing the OS-bound service. Executions are recorded as "triggered" before anything runs, so history can claim an action happened when it failed. Dismiss/snooze additionally require the live `NotificationListenerService` instance, which Hilt cannot inject elsewhere.

## Decision
1. **One `ActionExecutor` per `ActionType`**, registered via Hilt multibindings (`@Binds @IntoMap @ActionTypeKey(...)`). An `ActionDispatcher` looks up the handler for each enabled action; a missing handler yields an explicit `SKIPPED`, never a silent no-op. Adding an action type = one new class + one binding line; no existing execution code changes.

2. **`SystemNotificationController`** — a narrow interface (`cancel(sbnKey)`, `snooze(sbnKey, durationMs)`) implemented by the listener service and published to a `@Singleton` holder (`AtomicReference`) in `onListenerConnected()` / cleared in `onListenerDisconnected()`. System-dependent executors consume the holder and return `SKIPPED` when the listener is not connected. Executors are thereby testable with fakes.

3. **Per-action outcomes** (`SUCCESS` / `FAILED` / `SKIPPED`) are returned by the dispatcher, stored on `RuleExecution` (JSON column `action_outcomes`, additive Room migration), and surfaced in the Notification Detail screen. Actions execute before the execution record is saved so the record reflects what actually happened.

4. **Action configuration stays schemaless**: `RuleAction.config: Map<String, String>` is a deliberate choice — new action types need no migration. Convention: access config only through typed readers per action type (e.g., `getSnoozeDurationMinutes()`), never raw key lookups at call sites.

## Consequences

**Positive:**
- Webhook (Phase 4), alarm, and AI actions become additive changes, not service surgery
- Execution history is truthful; failures and skips are visible to the user (a "never fail silently" guiding principle in `docs/roadmap.md`)
- Executors unit-test with fake controllers; no emulator required
- Outcome data captured from day one — retrofitting it after webhooks would mean unfillable history

**Negative:**
- Multibinding indirection is less discoverable than a `when` block (mitigated by the `ActionModule` listing all bindings in one file)
- The holder pattern introduces nullable system access that every system-dependent executor must handle (by design — the listener genuinely may be disconnected)
- Stringly-typed config trades compile-time safety for schema flexibility; the typed-reader convention is discipline, not enforcement

## 1. Database & Model Updates

- [x] 1.1 Add `config` TEXT column to `RuleActionEntity` with a Room `TypeConverter` for `Map<String, String>` ↔ JSON serialization
- [x] 1.2 Update `RuleActionMapper` to map `config` between entity and domain model (round-trip without data loss)
- [x] 1.3 Add `sbnKey` field to domain `Notification` model and `NotificationEntity`, update notification mappers
- [x] 1.4 Create `SnoozedNotificationEntity` with table `snoozed_notifications` (id, notification_id, sbn_key, wake_up_time, rule_id, title, content, package_name, created_at)
- [x] 1.5 Create `SnoozedNotificationDao` with insert, delete, and query methods
- [x] 1.6 Write Room database migration to add `config` column, `sbn_key` column, and `snoozed_notifications` table
- [x] 1.7 Register new entity and DAO in the Room database class

## 2. Action Executor

- [x] 2.1 Create `ActionExecutor` interface in domain layer with `suspend fun execute(notification: Notification, actions: List<RuleAction>)`
- [x] 2.2 Create `AndroidActionExecutor` implementation in the notification feature package, injected via Hilt
- [x] 2.3 Implement dismiss action: call `cancelNotification(sbnKey)` on the `NotificationListenerService`
- [x] 2.4 Implement snooze action: dismiss notification, schedule alarm, and insert snoozed notification entry
- [x] 2.5 Handle execution failures gracefully — log errors, continue with remaining actions

## 3. Snooze Scheduling

- [x] 3.1 Create `SnoozeScheduler` class that wraps `AlarmManager` for scheduling/cancelling snooze alarms
- [x] 3.2 Implement `SCHEDULE_EXACT_ALARM` permission check with fallback to inexact alarms on Android 12+
- [x] 3.3 Create `SnoozeAlarmReceiver` BroadcastReceiver that re-posts the notification when the alarm fires
- [x] 3.4 Create a dedicated notification channel (`snoozed_notifications`) for re-posted notifications
- [x] 3.5 Implement snooze cancellation (remove alarm + delete database entry)
- [x] 3.6 Register `SnoozeAlarmReceiver` in `AndroidManifest.xml` and add `SCHEDULE_EXACT_ALARM` permission

## 4. Service Integration

- [x] 4.1 Update `NotificappListenerService` to capture and pass `StatusBarNotification.key` when saving notifications
- [x] 4.2 Update `NotificappListenerService.processRules()` to call `ActionExecutor` with triggered actions after `RuleEngine.process()`
- [x] 4.3 Thread the full `RuleAction` objects (not just IDs) from `RuleEngine` execution results to the service layer


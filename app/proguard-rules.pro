# Room, WorkManager and Compose provide their consumer rules. Keep worker names
# stable because persisted WorkManager jobs refer to them by class name.
-keep class ru.daybyday.app.notifications.NotificationWorker { *; }

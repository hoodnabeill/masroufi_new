# kotlinx.serialization ships its own consumer rules; keep @Serializable companions used reflectively by navigation routes.
-keepclassmembers @kotlinx.serialization.Serializable class com.masroufi.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance stores the callback's class name in the widget's PendingIntent and loads it by name when a chip is tapped.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# WorkManager saves a worker's class name in its database; renaming would orphan jobs scheduled by an older build.
-keepnames class * extends androidx.work.ListenableWorker
# Hilt entry points are looked up through generated components; keep the interface the widgets query.
-keep @dagger.hilt.EntryPoint interface com.masroufi.app.**

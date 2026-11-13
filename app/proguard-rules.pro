# kotlinx.serialization ships its own consumer rules; keep @Serializable companions used reflectively by navigation routes.
-keepclassmembers @kotlinx.serialization.Serializable class com.masroufi.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

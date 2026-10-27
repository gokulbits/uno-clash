# R8 understands kotlinx.serialization on its own, but the protocol's sealed message hierarchies are
# resolved through their generated serializers, so keep those and the companions they hang off.
-keepclassmembers class dev.colorclash.** {
    public static ** Companion;
    *** serializer(...);
}
-keep,includedescriptorclasses class dev.colorclash.**$$serializer { *; }
-keep class dev.colorclash.protocol.ClientMessage$* { *; }
-keep class dev.colorclash.protocol.ServerMessage$* { *; }
-keep class dev.colorclash.protocol.PlayerIntent$* { *; }

# Enums cross the wire by name (JoinError, CardColor, Face, Direction, BotLevel, DrawReason).
-keepclassmembers enum dev.colorclash.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Ktor brings an slf4j API along with no binding in the app.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# ZXing's readers are only reached from MultiFormatReader, which R8 can't always follow. Kept whole
# rather than risk the scan screen working in debug and not in release.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

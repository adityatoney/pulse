# R8 / ProGuard rules
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keep class com.pulse.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Convex
-keep class dev.convex.** { *; }

# Health Connect
-keep class androidx.health.** { *; }

# Protobuf
-keep class com.google.protobuf.** { *; }
-keep class com.pulse.data.proto.** { *; }

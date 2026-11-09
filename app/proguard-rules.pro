# Pleos Playground SDKs bind to system services via AIDL and (de)serialize data with
# Gson / kotlinx.serialization and Kotlin reflection. Keep their public surface intact
# so R8 does not strip or rename classes the SDK looks up at runtime.
-keep class ai.pleos.playground.** { *; }
-keep interface ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# Gson (transitive dependency of the Vehicle SDK)
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn sun.misc.**

# kotlinx.serialization (used by navi-data models)
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }

# Kotlin reflection metadata (kotlin-reflect is a runtime dependency of the Vehicle SDK)
-keep class kotlin.Metadata { *; }

# Keep line numbers for readable crash logs in release
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Gson TypeToken inside the Pleos SDK (capability responses). R8 full mode otherwise strips the
# generic signature of the anonymous TypeToken subclasses: "TypeToken must be created with a type
# argument" at runtime, seen on the emulator 2026-11-09 (HVAC power/fan/AC/recirc never arrived).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

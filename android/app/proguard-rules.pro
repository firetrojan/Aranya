# Aranya ProGuard rules.
#
# The release build uses R8 with resource shrinking. Compose, Room and kotlinx.serialization
# all ship consumer rules, so very little has to be kept by hand. Rules are added only where
# a class is referenced reflectively or by name, which R8 cannot see.

# Room generates DAO implementations by name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# kotlinx.serialization: generated serializers are looked up reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.aranya.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class org.aranya.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the source registry snapshot readable; it is assets, not code, so no rule needed.

# Ktor uses reflection for some engine features.
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**

# Keep line numbers so crash reports from the field stay readable, but hide the original
# source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

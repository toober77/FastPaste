# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Kotlin metadata for reflection/serialization used by Room/Compose
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class ** {
    @androidx.room.* <methods>;
}

# Compose / Kotlin
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# kotlinx.serialization: the library ships consumer rules, but keep the backup model's
# generated serializers explicitly so export/import survive R8 in release builds.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.github.toober77.fastpaste.Backup*$$serializer { *; }
-keepclassmembers class com.github.toober77.fastpaste.Backup* {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.github.toober77.fastpaste.Backup* {
    kotlinx.serialization.KSerializer serializer(...);
}

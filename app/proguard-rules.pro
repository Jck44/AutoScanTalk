# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
-keep class androidx.room.util.TableInfo { *; }
-keep class androidx.room.util.TableInfo$Column { *; }
-keep class androidx.room.util.TableInfo$ForeignKey { *; }
-keep class androidx.room.util.TableInfo$Index { *; }

# Gson
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.TypeAdapter
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keep,allowobfuscation,allowoptimization @kotlinx.serialization.Serializable class * {
    <fields>;
}
-keepclassmembers class com.andreas_kratzer.ghosttalk.** {
    @kotlinx.serialization.SerialName <fields>;
}
# Keep the serializer object for @Serializable classes
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    *** $serializer;
}

# Keep models to avoid serialization/reflection issues
-keep class com.andreas_kratzer.ghosttalk.model.** { *; }
-keep class com.andreas_kratzer.ghosttalk.model.importexport.** { *; }

# Google Drive API
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.model.** { *; }

# TTS & MediaPlayer
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Handle missing JDK classes on Android (often from Apache HttpClient or Google API Client)
-dontwarn javax.naming.**
-dontwarn org.apache.http.**
-dontwarn com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
-dontwarn com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

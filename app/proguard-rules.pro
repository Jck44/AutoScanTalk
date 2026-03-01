# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

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

# Keep models to avoid Gson issues
-keep class com.andreas_kratzer.ghosttalk.model.** { *; }
-keep class com.andreas_kratzer.ghosttalk.model.importexport.** { *; }

# TTS & MediaPlayer (Keep if reflection is used, but mostly safe)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
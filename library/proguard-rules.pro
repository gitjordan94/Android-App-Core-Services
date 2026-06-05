-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile
-keep public class * extends java.lang.Exception

-dontwarn org.jetbrains.annotations.**
-keep class kotlin.Metadata { *; }

# Crashlytics
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**

# AppsFlyer
-keep class com.appsflyer.** { *; }

# Google Play Install Referrer
-keep public class com.android.installreferrer.** { *; }

# Google Play Services
-keep class com.google.android.gms.ads.** { *; }

# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
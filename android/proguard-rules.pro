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

# Keep Capacitor plugin classes
-keep class com.getcapacitor.** { *; }
-keep class * extends com.getcapacitor.Plugin { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }

# Keep our USB Serial plugin classes
-keep class dev.emmanuelrobinson.capacitorusbserial.** { *; }

# Keep USB Serial for Android library classes
-keep class com.hoho.android.usbserial.** { *; }
-keep interface com.hoho.android.usbserial.** { *; }

# Keep USB related Android classes
-keep class android.hardware.usb.** { *; }

# Keep reflection-based access for Capacitor
-keepattributes *Annotation*
-keepclassmembers class ** {
    @com.getcapacitor.annotation.CapacitorPlugin *;
    @com.getcapacitor.PluginMethod *;
}

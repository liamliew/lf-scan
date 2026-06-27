-keep class com.lfcreative.lfscan.data.model.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Proguard rules for Chunbo Medical Android
-keep class com.chunbo.medical.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

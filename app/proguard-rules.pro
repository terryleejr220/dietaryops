# ProGuard / R8 rules for DietaryOpsManager

# Keep attributes required for reflection, annotations, serialization, and stack traces
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable

# Allow R8 to optimize source file names while preserving line numbers for Play Console de-obfuscation
-renamesourcefileattribute SourceFile

# Jetpack Compose
-keepclassmembers class * extends androidx.compose.ui.node.ModifierNodeElement {
    <init>(...);
}
-keep class androidx.compose.animation.core.KeyframesSpec$KeyframeEntity { *; }
-dontwarn androidx.compose.**

# Retrofit & OkHttp
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <fields>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepclassmembers enum * { *; }
-keepclassmembers class * implements com.google.gson.TypeAdapter { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
}
-keep class *_Impl { *; }
-dontwarn androidx.room.**

# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# Firebase (Auth, Firestore, Config, Analytics)
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <methods>;
}

# Preserve model, remote DTOs, and local data classes used for JSON serialization, Firestore, and Room
-keep class com.dietaryops.manager.data.model.** { *; }
-keepclassmembers class com.dietaryops.manager.data.model.** { *; }
-keep class com.dietaryops.manager.data.remote.** { *; }
-keepclassmembers class com.dietaryops.manager.data.remote.** { *; }
-keep class com.dietaryops.manager.data.local.** { *; }
-keepclassmembers class com.dietaryops.manager.data.local.** { *; }
-keep class com.centuryvilla.deliveryscanner.data.model.** { *; }
-keepclassmembers class com.centuryvilla.deliveryscanner.data.model.** { *; }
-keep class com.inventoryscanner.dietarymanager.data.model.** { *; }
-keepclassmembers class com.inventoryscanner.dietarymanager.data.model.** { *; }

# Keep default public constructors for data transfer objects
-keepclassmembers class * {
    public <init>();
}

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

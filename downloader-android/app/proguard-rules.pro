# youtubedl-android (يستخدم Jackson بالانعكاس)
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**
-dontwarn java.beans.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# commons-compress / commons-io: تسجّل أصنافها بالانعكاس (بدون هذه القاعدة يفشل فكّ Python عند أول تشغيل)
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

# ML Kit Translation: مكوّناته تُسجَّل وتُنشأ بالانعكاس (بدون هذه القواعد يفشل Translation.getClient بعد التصغير)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-dontwarn com.google.mlkit.**

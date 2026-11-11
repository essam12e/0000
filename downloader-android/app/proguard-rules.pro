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

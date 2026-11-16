# sing-box libbox is loaded reflectively from the optional AAR.
-keep class io.nekohasekai.libbox.** { *; }
-keepclassmembers class io.nekohasekai.libbox.** { *; }
-dontwarn io.nekohasekai.libbox.**

# kotlinx.serialization models and generated serializers.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.pulseroute.vpn.**$$serializer { *; }
-keepclassmembers class com.pulseroute.vpn.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.pulseroute.vpn.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio platform warnings.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

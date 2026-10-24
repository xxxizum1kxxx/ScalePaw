# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.gpo.scalepaw.** {
    *** Companion;
}
-keepclasseswithmembers class com.gpo.scalepaw.** {
    kotlinx.serialization.KSerializer serializer(...);
}

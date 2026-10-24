# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.izum1k.scalepaw.** {
    *** Companion;
}
-keepclasseswithmembers class com.izum1k.scalepaw.** {
    kotlinx.serialization.KSerializer serializer(...);
}

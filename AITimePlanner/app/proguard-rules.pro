# Стандартные правила для Room/Hilt/Compose не требуют особых настроек.
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.aiplanner.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class app.aiplanner.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

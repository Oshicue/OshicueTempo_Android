# Oshicue Tempo release rules.
# kotlinx.serialization is used through the JsonElement API only, and the app
# keeps its state in plain classes, so the default AGP/Compose rules suffice.

# Keep the receivers referenced from the manifest by name.
-keep class com.oshicue.tempo.data.TempoAlarmReceiver { *; }
-keep class com.oshicue.tempo.data.TempoBootReceiver { *; }

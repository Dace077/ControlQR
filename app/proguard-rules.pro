# Room genera implementaciones por reflexión sobre nombres de clase.
-keep class com.controlqr.acceso.data.db.** { *; }

# ML Kit barcode (modelo empaquetado)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Modelos serializados a JSON manualmente
-keep class com.controlqr.acceso.core.qr.** { *; }

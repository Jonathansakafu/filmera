# MediaPipe uses reflection/JNI into these packages.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
# tasks-vision ships AutoValue's annotation processor; its compile-time-only
# references are never loaded at runtime.
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

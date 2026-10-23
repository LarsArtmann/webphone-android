# Keep the WebRTC API surface (JNI reaches in by reflection).
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Firebase messaging service entry points.
-keep class software.larsartmann.webphone.push.FcmReceiver { *; }

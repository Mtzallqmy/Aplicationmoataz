-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keep class ai.alaser.app.terminal.NativePtyBridge {
    native <methods>;
}

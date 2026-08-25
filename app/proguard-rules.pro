-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    *** Companion;
}

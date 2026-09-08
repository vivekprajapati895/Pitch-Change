## NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**

## Rhino reflection targets
-keepclassmembers class * {
    @org.mozilla.javascript.annotations.JSFunction *;
}

## Media3
-dontwarn androidx.media3.**

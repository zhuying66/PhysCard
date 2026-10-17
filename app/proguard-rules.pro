# Official FSRS Java library is accessed reflectively by Jackson for optional JSON APIs.
-keep class io.github.openspacedrepetition.** { *; }
-keepclassmembers class io.github.openspacedrepetition.** { *; }
# lombok annotations are compile-time only and are referenced in the published bytecode.
-dontwarn lombok.**
-keep class lombok.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

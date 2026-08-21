# UniFFI's generated bindings call the Rust cdylib through JNA. JNA resolves
# these types and members at runtime, so they are an intentional reflection
# boundary rather than removable Java-only code.
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

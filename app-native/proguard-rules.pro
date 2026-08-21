# UniFFI's generated bindings call the Rust cdylib through JNA. JNA resolves
# these types and members at runtime, so they are an intentional reflection
# boundary rather than removable Java-only code.
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# ObjectBox FlexObjectConverter.shouldRestoreAsLong() does
# getDeclaredField("parentWidth") on FlexBuffers.Reference. The field is
# private, ObjectBox's own consumer rules do not keep it, and R8 otherwise
# renames it. Minified release builds then crash with:
#   FlexMapConverter could not determine FlexBuffers integer bit width
#   Caused by: NoSuchFieldException: No field parentWidth in class L…;
# while restoring Map<String, Object> columns (Message.metadata,
# Attachment.metadata / exif) — commonly from MessageRepo projecting
# dbAttachments.size on a transcript page.
-keep class io.objectbox.flatbuffers.FlexBuffers$Reference {
    <fields>;
}

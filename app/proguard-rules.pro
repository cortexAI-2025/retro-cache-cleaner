# Retro Cache Cleaner ProGuard rules
# Keep application class names for crash diagnostics
-keepattributes SourceFile,LineNumberTable

# Preserve the RetroProgressBar custom view (referenced from XML)
-keep class com.retrocache.cleaner.RetroProgressBar { *; }

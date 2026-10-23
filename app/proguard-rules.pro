# Markwon / commonmark render pipeline (reflection-heavy in places)
-keep class org.commonmark.** { *; }
-keep class io.noties.markwon.** { *; }
-dontwarn org.commonmark.**
-dontwarn io.noties.markwon.**

# HeadingSpan.level is read reflectively for the TOC
-keepclassmembers class io.noties.markwon.core.spans.HeadingSpan { int level; }

# Code editor + textmate grammars
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.sora.**

# LaTeX
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
-dontwarn ru.noties.jlatexmath.**


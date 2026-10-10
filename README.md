# Halo 2 Android native integration test

This packages the existing ARM64 Android native probe library.
It is NOT a playable port of Halo 2.

Build remotely using the included GitHub Actions workflow.
The original game's assets and Xbox API replacements are not included.


## Map header validation milestone

The Android app now reports the Halo 2 map header signature, format word, declared end-of-file, actual file length, index range, and basic bounds checks. This validates header bounds only; it does not yet parse the tag index, load geometry, render graphics, or run gameplay.

# iOS port — orchestrator fix list (open items found in review)

- [x] S2b: first-letters hint shows letters in isolated form; show the initial (joined) form like Android. Done by S4.
- [x] S2b demo: Al-Fatiha bismillah header follows Kotlin `splitBismillah` (reader uses it). Done in S5.
- [x] S2b: Madinah face U+06DF drawn as a dotted circle: the reader draws it as the sukun in the Madinah face (same length). Done in S5.
- [x] S1: `QhButton` hugs its content; add a full-width option and replace `RecallWideButton`. Done in S3: `QhButton(..., fullWidth: true)`, `RecallWideButton` deleted.
- [x] S1: switching from forced dark back to "system" may read a stale colour scheme. Done in S3: `QhThemeRoot` reads the device scheme from `SystemScheme` (window scene traits), not the environment.

# iOS port — orchestrator fix list (open items found in review)

- [x] S2b: first-letters hint shows letters in isolated form; show the initial (joined) form like Android (append U+200D ZWJ, or the same approach as Android `HifzAyahContent`). Owner of fix: S4 agent (lesson) — `Features/Common/HifzAyahContent.swift`. Done in S4: U+200D appended in the QuranText overlay and in `RecallHints.preview`.
- [ ] S2b demo: Al-Fatiha shows the bismillah header and ayah 1 bismillah; the reader must follow Kotlin `splitBismillah` (no header for surah 1 and 9). Owner: S5 agent.
- [ ] S2b: Madinah face draws U+06DF as a dotted filled circle (2:5); compare with Android reader in the same font. Owner: S5 agent.
- [x] S1: `QhButton` hugs its content; add a full-width option and replace `RecallWideButton`. Done in S3: `QhButton(..., fullWidth: true)`, `RecallWideButton` deleted.
- [x] S1: switching from forced dark back to "system" may read a stale colour scheme. Done in S3: `QhThemeRoot` reads the device scheme from `SystemScheme` (window scene traits), not the environment.

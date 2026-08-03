# Design QA — Quran Main Screen Rewrite

## Comparison target

- Source visual truth:
  `/Users/azizjon/.codex/generated_images/019fac2e-73b3-7f73-8db1-500506f32ab0/call_jV94iQQSs6GVpkpNt6w1Pgyb.png`
- Previous implementation baseline:
  `/var/folders/r5/9cpydy050tz4yvcqrnfj8b3h0000gn/T/screenshot_optimized_8b5077d1-e0bc-4d5a-b409-ccfbf28f73fd.jpg`
- Rewritten implementation:
  `/var/folders/r5/9cpydy050tz4yvcqrnfj8b3h0000gn/T/screenshot_optimized_a603ce57-84e3-4f0e-890f-5bc4f0435351.jpg`
- Source comparison:
  `/Users/azizjon/.codex/visualizations/2026/07/29/019fac2e-73b3-7f73-8db1-500506f32ab0/quran-main-redesign-source-comparison.png`
- Before/after comparison:
  `/Users/azizjon/.codex/visualizations/2026/07/29/019fac2e-73b3-7f73-8db1-500506f32ab0/quran-main-redesign-comparison.png`
- State: Russian locale, light appearance, Quran root, All filter,
  persisted Learning and Learned statuses.

## Viewport and normalization

- Source pixels: 853 × 1844.
- Implementation pixels: 368 × 800.
- Native viewport: iPhone 17 Simulator, 368 × 800.
- Density normalization: source resized with cover fit to 368 × 800; the
  implementation was captured at its native simulator pixel size.
- Device status bar is real system chrome and was not recreated as app content.

## Findings

- No actionable P0, P1, or P2 findings remain.
- The rewrite preserves the selected concept's information hierarchy while
  intentionally improving grouping: Continue Reading is now a clear action card,
  filter and result count form one control region, and surahs are grouped in a
  quiet rounded list surface.
- Persistent navigation remains visually separate from the scrollable list and
  the list retains enough bottom inset for the final row to clear the floating
  iOS tab bar.

## Required fidelity surfaces

- Fonts and typography: large-title hierarchy, bold surah names, quiet metadata,
  compact section labels, Arabic Quran type, truncation, and optical weights are
  readable and consistent at 368 px.
- Spacing and layout rhythm: 16 dp page grid, 12–15 dp section rhythm, 44+ dp
  interactive targets, inset dividers, 18 dp grouped corners, and compact 76 dp
  rows produce a denser but calmer scan path than the baseline.
- Colors and tokens: existing background, surface, border, primaryWeak, primary,
  success, info, and tertiary text tokens are used without introducing a second
  palette. Contrast remains clear in the verified light state.
- Image and asset fidelity: the screen has no photographic or branded raster
  assets. Existing platform/vector icons and the bundled Quran font are retained;
  no placeholder imagery, emoji, or handcrafted SVG assets were introduced.
- Copy and content: title, search hint, Continue Reading, filters, visible result
  count, ayah counts, Arabic surah names, and learning statuses are localized.

## Full-view and focused comparison

- The full-view combined input verifies hierarchy, margins, component sizes,
  grouping, colors, list density, and bottom navigation.
- A separate focused crop was not needed because both normalized 368 px panels
  keep the search, continuation card, segmented control, Arabic names, status
  indicators, row separators, and tab labels readable.

## Interaction evidence

- All and Learning filters were exercised; the result count changed from 114 to 2
  and only matching persisted statuses remained.
- Continue Reading opened Al-Baqarah in the reader.
- Search, row navigation, status persistence, and reader dismissal retain the
  same state flow used before the visual rewrite.
- Android and iOS Kotlin targets compile; the iOS app launches successfully.

## Comparison history

- Initial baseline: controls and list were visually flat, Continue Reading used
  two separators, and the list lacked a clear grouped boundary.
- Rewrite pass: extracted a dedicated screen content layout, introduced a compact
  continuation card, localized result header, rounded grouped list, numbered
  badges, inset dividers, and reduced row height.
- Post-fix evidence: the final implementation and both combined comparison files
  listed above show no remaining P0/P1/P2 layout or fidelity issue.

## Implementation checklist

- [x] Main screen structure rewritten into dedicated content and list components.
- [x] Search and filters remain platform-adaptive.
- [x] Continue Reading is a single clear touch target.
- [x] Filtered result count is visible and localized.
- [x] Surah list is visually grouped and scrolls under the floating iOS tab bar.
- [x] Empty, loading, filtered, learning, and learned states remain supported.
- [x] Android and iOS targets compile.
- [x] iOS visual and interaction checks pass.

## Follow-up polish

- P3: evaluate the same layout in dark mode on a physical OLED device.

final result: passed

package tj.app.quran_todo.ui.reader

/** One laid-out item of the reader list: its list index, top offset (0 = just under the top bar) and height, in pixels. */
internal data class ListItemSpan(val index: Int, val offset: Int, val size: Int)

/**
 * The ayah the person is reading, for the counter in the top bar, the place line and the saved reading position.
 *
 * Not the first item with any pixel on screen: the tail of the previous ayah's translation under the top bar made the counter say
 * "254" while 2:255 filled the screen. The ayah crossing a reading line a fifth of the way down the visible area counts instead.
 * At the end of the surah (nothing more to scroll) the last ayah on screen counts, since a short last ayah never reaches that line.
 *
 * [items] are the visible items in list order (item 0 is the header, ayah `i` is item `i + 1`); [visibleHeight] is the height between
 * the top bar and the player. Returns [ReaderViewModel.NotMeasured], [ReaderViewModel.HeaderOnTop] or an ayah index (0-based).
 */
internal fun ayahOnReadingLine(items: List<ListItemSpan>, visibleHeight: Int, atEnd: Boolean, total: Int): Int {
    if (items.isEmpty() || visibleHeight <= 0) return ReaderViewModel.NotMeasured
    val item = if (atEnd) {
        items.lastOrNull { it.index in 1..total && it.offset < visibleHeight } ?: items.last()
    } else {
        val line = visibleHeight / ReadingLineFraction
        items.firstOrNull { it.offset + it.size > line } ?: items.last()
    }
    return when {
        item.index == 0 -> ReaderViewModel.HeaderOnTop
        item.index <= total -> item.index - 1
        else -> total - 1
    }
}

/** The reading line sits at 1/[ReadingLineFraction] of the visible height. */
private const val ReadingLineFraction = 5

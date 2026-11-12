package tj.app.quran_todo.ui.reader

import kotlin.math.max

/**
 * Which ayah of the text reader has its row of actions open (UX_PLAN U19): at most one, kept as the list index of the ayah. A tap
 * moves the row to the ayah, a second tap on the same ayah closes it, and the row goes away when its ayah is scrolled far away.
 */
internal data class AyahActionRowState(val openIndex: Int? = null) {
    fun tapped(index: Int): AyahActionRowState = if (openIndex == index) closed() else AyahActionRowState(index)

    fun closed(): AyahActionRowState = if (openIndex == null) this else AyahActionRowState(null)

    /** [firstVisible]..[lastVisible] are the ayah indexes on screen; the row stays while its ayah is within [margin] of them. */
    fun scrolledAway(firstVisible: Int, lastVisible: Int, margin: Int = FarAway): AyahActionRowState {
        val open = openIndex ?: return this
        return if (open < firstVisible - margin || open > max(firstVisible, lastVisible) + margin) closed() else this
    }

    companion object {
        /** How many ayahs outside the screen still count as "near". */
        const val FarAway = 3
    }
}

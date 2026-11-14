package tj.app.quran_todo.ui.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.trustCopy
import tj.app.quran_todo.ui.i18n.trustCopyIos

class TrustCopyIosTest {
    @Test
    fun iosCopyNeverMentionsAndroidOrGoogle() {
        AppLanguage.entries.forEach { language ->
            val copy = trustCopyIos(language)
            copy.privacyPoints.forEach { point ->
                assertFalse(point.text.contains("Android", ignoreCase = true), "$language: ${point.title} names Android")
                assertFalse(point.text.contains("Google", ignoreCase = true), "$language: ${point.title} names Google")
            }
        }
    }

    @Test
    fun iosCopyKeepsTheShapeOfTheSharedCopy() {
        AppLanguage.entries.forEach { language ->
            val shared = trustCopy(language)
            val ios = trustCopyIos(language)
            assertEquals(shared.privacyPoints.map { it.title }, ios.privacyPoints.map { it.title })
            assertTrue(ios.privacyPoints.zip(shared.privacyPoints).count { it.first != it.second } == 3)
        }
    }
}

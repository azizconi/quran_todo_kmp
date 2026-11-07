package tj.app.quran_todo.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The owner forbids stars of any kind in the interface. Material icons that draw stars or sparkles (Insights, AutoAwesome and the
 * like) are as bad as a star shape: no source of the Android UI or of the shared screens may use them.
 */
class NoStarIconsTest {
    private val forbiddenIcon = Regex(
        """[iI]cons\.(?:\w+\.)*(?:Insights|AutoAwesome\w*|Stars?|Star(?:Rate|Half|Border|Outline|Purple500)\w*|Sparkle\w*|Flare|NewReleases|TipsAndUpdates)\b""",
    )
    private val forbiddenDrawing = Regex("""drawStar|StarShape|starPath""")

    @Test
    fun noStarOrSparkleIconAnywhereInTheScreens() {
        val roots = listOf(
            "src/androidMain/kotlin/tj/app/quran_todo/ui",
            "src/androidMain/kotlin/tj/app/quran_todo/presentation",
            "src/commonMain/kotlin/tj/app/quran_todo/presentation",
            "src/commonMain/kotlin/tj/app/quran_todo/ui",
        )
        var scanned = 0
        for (root in roots) {
            val dir = File(root)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                scanned++
                val text = file.readText()
                val icon = forbiddenIcon.find(text)
                assertTrue(icon == null, "${file.path} uses a star or sparkle icon: ${icon?.value}")
                val drawing = forbiddenDrawing.find(text)
                assertTrue(drawing == null, "${file.path} draws a star: ${drawing?.value}")
            }
        }
        assertTrue(scanned > 50 || !File("src/androidMain").exists(), "the sources were not found (scanned $scanned)")
    }

    @Test
    fun theRegexCatchesWhatItShould() {
        listOf(
            "Icons.Rounded.Insights", "Icons.Filled.AutoAwesome", "Icons.Rounded.AutoAwesomeMotion", "Icons.Outlined.Stars",
            "Icons.Rounded.StarRate", "Icons.Rounded.StarHalf", "Icons.AutoMirrored.Rounded.Star", "Icons.Rounded.Sparkle",
            "Icons.Rounded.Flare", "Icons.Rounded.NewReleases",
        ).forEach { assertTrue(forbiddenIcon.containsMatchIn(it), it) }
        listOf("Icons.Rounded.BarChart", "Icons.Rounded.Start", "Icons.Rounded.Stop").forEach {
            assertTrue(!forbiddenIcon.containsMatchIn(it), it)
        }
    }
}

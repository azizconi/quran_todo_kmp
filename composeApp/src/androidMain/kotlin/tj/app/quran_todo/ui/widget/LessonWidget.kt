package tj.app.quran_todo.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import tj.app.quran_todo.MainActivity
import tj.app.quran_todo.common.reminder.ReminderChannel
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.ui.i18n.widgetCopy
import tj.app.quran_todo.ui.reminder.ReminderLink
import tj.app.quran_todo.ui.reminder.ReminderTarget
import tj.app.quran_todo.ui.theme.QhColors
import tj.app.quran_todo.ui.theme.qhColors

/**
 * The home screen widget "Lesson of the day" (task F5b), built with Glance and Material 3.
 *
 * Two sizes: the one row (4x1), "Lesson of the day · ~12 min" over "New: An-Naba 1–5 · 2 repetitions", and the two rows (4x2) that add the
 * streak and the state of the lesson. A tap opens the lesson straight away (the link of the reminder, see [ReminderLink]); with no plan, a
 * finished plan or a pause it opens the main screen. What it says is [LessonWidgetModel], built from the same lesson card as the home
 * screen; when it is redrawn is [LessonWidgetUpdater]'s business. The colors are the palette of the app, light and dark with the system.
 * The font is the system's: Glance cannot carry its own.
 */
class LessonWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(OneRow, TwoRows))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = LessonWidgetLoader.load(context)
        val colors = AppGlanceColors.current()
        provideContent {
            GlanceTheme(colors = colors) {
                val tall = LocalSize.current.height >= TwoRowsMinHeight
                if (model == null) Placeholder(context) else Content(context, model, tall)
            }
        }
    }

    @Composable
    private fun Content(context: Context, model: LessonWidgetModel, tall: Boolean) {
        val colors = GlanceTheme.colors
        val open = openAction(context, model)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(colors.surface)
                .cornerRadius(22.dp)
                .clickable(open),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.title,
                    maxLines = 1,
                    style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = model.detail,
                    maxLines = if (tall) 3 else 1,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                )
                if (tall) {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = GlanceModifier.defaultWeight()) {
                            model.streak?.let { streak ->
                                Text(
                                    text = streak,
                                    maxLines = 1,
                                    style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                        model.action?.let { action ->
                            Box(
                                modifier = GlanceModifier
                                    .background(colors.primary)
                                    .cornerRadius(20.dp)
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = action,
                                    maxLines = 1,
                                    style = TextStyle(color = colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Shown when the lesson could not be read: only the name, and a tap opens the app. */
    @Composable
    private fun Placeholder(context: Context) {
        val colors = GlanceTheme.colors
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(colors.surface)
                .cornerRadius(22.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = widgetCopy(ReminderChannel.currentLanguage()).contentDescription,
                modifier = GlanceModifier.padding(horizontal = 16.dp),
                style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    private fun openAction(context: Context, model: LessonWidgetModel): Action =
        if (model.opensLesson) {
            actionStartActivity(ReminderLink.intentFor(context, ReminderTarget.Lesson, source = ReminderLink.SOURCE_WIDGET))
        } else {
            actionStartActivity(Intent(context, MainActivity::class.java))
        }

    private companion object {
        val OneRow = DpSize(250.dp, 56.dp)
        val TwoRows = DpSize(250.dp, 120.dp)
        val TwoRowsMinHeight = 110.dp
    }
}

/** The receiver that the system talks to for [LessonWidget]. It also plans the midnight redraw. */
class LessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LessonWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LessonWidgetUpdater.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        LessonWidgetUpdater.cancelMidnight(context)
    }
}

/** The palette of the app (the chosen one, light and dark with the system) as Glance color providers. */
internal object AppGlanceColors {
    fun current(): ColorProviders {
        val palette = ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST
        return forPalette(palette)
    }

    fun forPalette(palette: ThemePalette): ColorProviders = ColorProviders(
        light = scheme(qhColors(ThemeMode.LIGHT, palette), dark = false),
        dark = scheme(qhColors(ThemeMode.DARK, palette), dark = true),
    )

    private fun scheme(c: QhColors, dark: Boolean): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            background = c.bg,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.surfaceMuted,
            onSurfaceVariant = c.inkSecondary,
            outline = c.hairline,
            error = c.danger,
            onError = c.onDanger,
        )
    }
}

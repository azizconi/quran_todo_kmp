package tj.app.quran_todo.presentation.surah

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.utils.toUIColor
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.darwin.NSObject
import tj.app.quran_todo.common.theme.extendedColors
import androidx.compose.material.MaterialTheme

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
internal actual fun PlatformSurahReaderTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val target = remember { SurahReaderBackTarget(onBack) }
    val titleColor = MaterialTheme.colors.onBackground.toUIColor()
    val subtitleColor = MaterialTheme.extendedColors.textSecondary.toUIColor()
    val interfaceStyle = if (MaterialTheme.colors.isLight) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight
    } else {
        UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }
    SideEffect { target.onBack = onBack }

    UIKitView(
        factory = {
            SurahReaderTopBarContainer(
                title = title,
                subtitle = subtitle,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                interfaceStyle = interfaceStyle,
                target = target,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        background = MaterialTheme.colors.background,
        accessibilityEnabled = true,
        update = { container ->
            container.update(
                title = title,
                subtitle = subtitle,
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                interfaceStyle = interfaceStyle,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class SurahReaderBackTarget(
    var onBack: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun backTapped() {
        onBack()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class SurahReaderTopBarContainer(
    title: String,
    subtitle: String,
    titleColor: UIColor,
    subtitleColor: UIColor,
    interfaceStyle: UIUserInterfaceStyle,
    target: SurahReaderBackTarget,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val backButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setTitle("‹", forState = UIControlStateNormal)
        titleLabel?.font = UIFont.systemFontOfSize(38.0)
        addTarget(
            target = target,
            action = NSSelectorFromString("backTapped"),
            forControlEvents = UIControlEventTouchUpInside,
        )
    }
    private val titleLabel = UILabel().apply {
        font = UIFont.boldSystemFontOfSize(17.0)
        textAlignment = NSTextAlignmentCenter
        numberOfLines = 1
    }
    private val subtitleLabel = UILabel().apply {
        font = UIFont.systemFontOfSize(12.0)
        textAlignment = NSTextAlignmentCenter
        numberOfLines = 1
    }

    init {
        addSubview(backButton)
        addSubview(titleLabel)
        addSubview(subtitleLabel)
        update(title, subtitle, titleColor, subtitleColor, interfaceStyle)
    }

    fun update(
        title: String,
        subtitle: String,
        titleColor: UIColor,
        subtitleColor: UIColor,
        interfaceStyle: UIUserInterfaceStyle,
    ) {
        titleLabel.text = title
        subtitleLabel.text = subtitle
        titleLabel.textColor = titleColor
        subtitleLabel.textColor = subtitleColor
        backButton.tintColor = titleColor
        backButton.setTitleColor(titleColor, forState = UIControlStateNormal)
        overrideUserInterfaceStyle = interfaceStyle
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        backButton.setFrame(CGRectMake(8.0, (height - 44.0) / 2.0, 44.0, 44.0))
        titleLabel.setFrame(CGRectMake(56.0, 10.0, (width - 112.0).coerceAtLeast(0.0), 23.0))
        subtitleLabel.setFrame(CGRectMake(56.0, 33.0, (width - 112.0).coerceAtLeast(0.0), 18.0))
    }
}

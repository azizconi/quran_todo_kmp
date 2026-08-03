package tj.app.quran_todo.presentation.hifz

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.utils.toUIColor
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIColor
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIAccessibilityTraitButton
import platform.UIKit.UIAccessibilityTraitSelected
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewDistributionFillEqually
import platform.UIKit.UIImage
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UISearchBar
import platform.UIKit.UISearchBarDelegateProtocol
import platform.UIKit.UISearchBarStyle
import platform.UIKit.UISearchTextField
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.accessibilityTraits
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import tj.app.quran_todo.common.theme.extendedColors
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformQuranTopBar(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val titleColor = MaterialTheme.colors.onBackground.toUIColor()
    val interfaceStyle = platformInterfaceStyle()
    val target = remember { QuranSettingsButtonTarget(onOpenSettings) }
    SideEffect { target.onOpenSettings = onOpenSettings }
    UIKitView(
        factory = {
            UIView().apply {
                val label = UILabel().apply {
                    text = title
                    font = UIFont.boldSystemFontOfSize(34.0)
                    adjustsFontForContentSizeCategory = true
                    textColor = titleColor
                    overrideUserInterfaceStyle = interfaceStyle
                    translatesAutoresizingMaskIntoConstraints = false
                }
                val button = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                    setImage(UIImage.systemImageNamed("gearshape"), forState = UIControlStateNormal)
                    tintColor = titleColor
                    translatesAutoresizingMaskIntoConstraints = false
                    addTarget(
                        target = target,
                        action = NSSelectorFromString("openSettings:"),
                        forControlEvents = UIControlEventTouchUpInside,
                    )
                }
                addSubview(label)
                addSubview(button)
                label.leadingAnchor.constraintEqualToAnchor(leadingAnchor).active = true
                label.centerYAnchor.constraintEqualToAnchor(centerYAnchor).active = true
                button.trailingAnchor.constraintEqualToAnchor(trailingAnchor).active = true
                button.centerYAnchor.constraintEqualToAnchor(centerYAnchor).active = true
                button.widthAnchor.constraintEqualToConstant(44.0).active = true
                button.heightAnchor.constraintEqualToConstant(44.0).active = true
                label.trailingAnchor.constraintLessThanOrEqualToAnchor(button.leadingAnchor, constant = -8.0).active = true
            }
        },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(58.dp),
        background = MaterialTheme.colors.background,
        accessibilityEnabled = true,
        update = { container ->
            val label = container.subviews.firstOrNull() as? UILabel
            val button = container.subviews.getOrNull(1) as? UIButton
            label?.text = title
            label?.textColor = titleColor
            label?.overrideUserInterfaceStyle = interfaceStyle
            button?.tintColor = titleColor
            button?.overrideUserInterfaceStyle = interfaceStyle
        },
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class QuranSettingsButtonTarget(
    var onOpenSettings: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun openSettings(@Suppress("UNUSED_PARAMETER") sender: UIButton) {
        onOpenSettings()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformQuranSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    val target = remember { QuranSearchBarTarget(onValueChange) }
    val interfaceStyle = platformInterfaceStyle()
    val libraryColors = MaterialTheme.extendedColors
    val fieldBackgroundColor = libraryColors.libraryControlSurface.toUIColor()
    val fieldBorderColor = libraryColors.libraryControlBorder.toUIColor()
    val fieldTextColor = MaterialTheme.colors.onSurface.toUIColor()
    val secondaryColor = MaterialTheme.extendedColors.textSecondary.toUIColor()
    val searchTintColor = MaterialTheme.colors.primary.toUIColor()
    SideEffect {
        target.onValueChange = onValueChange
    }
    UIKitView(
        factory = {
            UISearchBar().apply {
                this.placeholder = placeholder
                text = value
                delegate = target
                searchBarStyle = UISearchBarStyle.UISearchBarStyleMinimal
                backgroundColor = UIColor.clearColor
                barTintColor = UIColor.clearColor
                tintColor = searchTintColor
                searchTextField.applyQuranSearchFieldStyle(
                    backgroundColor = fieldBackgroundColor,
                    borderColor = fieldBorderColor,
                    textColor = fieldTextColor,
                    secondaryColor = secondaryColor,
                    tintColor = searchTintColor,
                )
                overrideUserInterfaceStyle = interfaceStyle
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        // Keep the interop host opaque to prevent its default black backing
        // appearing at the rounded native field's corners during compositing.
        background = MaterialTheme.colors.background,
        accessibilityEnabled = true,
        update = { searchBar ->
            if (searchBar.text != value) searchBar.text = value
            searchBar.placeholder = placeholder
            searchBar.searchBarStyle = UISearchBarStyle.UISearchBarStyleMinimal
            searchBar.backgroundColor = UIColor.clearColor
            searchBar.barTintColor = UIColor.clearColor
            searchBar.tintColor = searchTintColor
            searchBar.searchTextField.applyQuranSearchFieldStyle(
                backgroundColor = fieldBackgroundColor,
                borderColor = fieldBorderColor,
                textColor = fieldTextColor,
                secondaryColor = secondaryColor,
                tintColor = searchTintColor,
            )
            searchBar.overrideUserInterfaceStyle = interfaceStyle
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun UISearchTextField.applyQuranSearchFieldStyle(
    backgroundColor: UIColor,
    borderColor: UIColor,
    textColor: UIColor,
    secondaryColor: UIColor,
    tintColor: UIColor,
) {
    this.backgroundColor = backgroundColor
    layer.backgroundColor = backgroundColor.CGColor
    this.textColor = textColor
    this.tintColor = tintColor
    leftView?.tintColor = secondaryColor
    layer.shadowOpacity = 0f
    layer.shadowRadius = 0.0
    layer.borderColor = borderColor.CGColor
    layer.borderWidth = 1.0
    layer.cornerRadius = 12.0
    layer.masksToBounds = true
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformQuranSegmentedControl(
    selectedIndex: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val selectedBackgroundColor = if (MaterialTheme.colors.isLight) {
        Color(0xFFBDE7D5)
    } else {
        Color(0xFF2D5C49)
    }.toUIColor()
    val containerColor = if (MaterialTheme.colors.isLight) {
        Color.White
    } else {
        Color(0xFF1C211F)
    }.toUIColor()
    val containerBorderColor = if (MaterialTheme.colors.isLight) {
        Color(0xFFE1E7E3)
    } else {
        Color(0xFF3A4540)
    }.toUIColor()
    val shelfColor = MaterialTheme.colors.background.toUIColor()
    val selectedContentColor = MaterialTheme.colors.primary.toUIColor()
    val inactiveContentColor = MaterialTheme.extendedColors.textSecondary.toUIColor()
    val interfaceStyle = platformInterfaceStyle()
    val target = remember { QuranTabsTarget(onSelected) }
    SideEffect {
        target.onSelected = onSelected
    }
    UIKitView(
        factory = {
            QuranTabsContainer(
                labels = labels,
                selectedIndex = selectedIndex,
                shelfColor = shelfColor,
                containerColor = containerColor,
                containerBorderColor = containerBorderColor,
                selectedBackgroundColor = selectedBackgroundColor,
                selectedContentColor = selectedContentColor,
                inactiveContentColor = inactiveContentColor,
                interfaceStyle = interfaceStyle,
                target = target,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        background = MaterialTheme.colors.background,
        accessibilityEnabled = true,
        update = { container ->
            container.update(
                labels = labels,
                selectedIndex = selectedIndex,
                shelfColor = shelfColor,
                containerColor = containerColor,
                containerBorderColor = containerBorderColor,
                selectedBackgroundColor = selectedBackgroundColor,
                selectedContentColor = selectedContentColor,
                inactiveContentColor = inactiveContentColor,
                interfaceStyle = interfaceStyle,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformQuranSurahStatusButton(
    status: SurahTodoStatus?,
    statusTitle: String,
    notStartedLabel: String,
    learningLabel: String,
    learnedLabel: String,
    cancelLabel: String,
    onStatusSelected: (SurahTodoStatus?) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.extendedColors
    val (label, backgroundColor, contentColor) = when (status) {
        SurahTodoStatus.LEARNING -> Triple(
            learningLabel,
            colors.surahStatusLearningSurface.toUIColor(),
            colors.info.toUIColor(),
        )

        SurahTodoStatus.LEARNED -> Triple(
            learnedLabel,
            colors.surahStatusLearnedSurface.toUIColor(),
            colors.success.toUIColor(),
        )

        null -> Triple(
            notStartedLabel,
            colors.surahStatusNotStartedSurface.toUIColor(),
            colors.textSecondary.toUIColor(),
        )
    }
    val interfaceStyle = platformInterfaceStyle()
    val target = remember { QuranSurahStatusButtonTarget() }
    SideEffect {
        target.statusTitle = statusTitle
        target.notStartedLabel = notStartedLabel
        target.learningLabel = learningLabel
        target.learnedLabel = learnedLabel
        target.cancelLabel = cancelLabel
        target.onStatusSelected = onStatusSelected
    }
    UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                titleLabel?.font = UIFont.systemFontOfSize(12.0)
                layer.cornerRadius = 12.0
                layer.masksToBounds = true
                addTarget(
                    target = target,
                    action = NSSelectorFromString("statusButtonTapped:"),
                    forControlEvents = UIControlEventTouchUpInside,
                )
            }
        },
        modifier = modifier
            .width(108.dp)
            .height(36.dp),
        // This view sits inside a Surface row. Giving its native host the
        // same opaque surface removes the black rectangular corners that can
        // appear around a rounded UIButton in Compose/UIKit interop.
        background = MaterialTheme.colors.surface,
        accessibilityEnabled = true,
        update = { button ->
            button.setTitle("$label  ▾", forState = UIControlStateNormal)
            button.setTitleColor(contentColor, forState = UIControlStateNormal)
            button.backgroundColor = backgroundColor
            button.layer.backgroundColor = backgroundColor.CGColor
            button.titleLabel?.font = UIFont.systemFontOfSize(12.0)
            button.layer.cornerRadius = 12.0
            button.layer.masksToBounds = true
            button.overrideUserInterfaceStyle = interfaceStyle
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class QuranTabsContainer(
    labels: List<String>,
    selectedIndex: Int,
    shelfColor: UIColor,
    containerColor: UIColor,
    containerBorderColor: UIColor,
    selectedBackgroundColor: UIColor,
    selectedContentColor: UIColor,
    inactiveContentColor: UIColor,
    interfaceStyle: UIUserInterfaceStyle,
    target: QuranTabsTarget,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private var selectedTabIndex = selectedIndex

    private val tabContainer = UIView().apply {
        backgroundColor = containerColor
        layer.cornerRadius = 14.0
        layer.masksToBounds = true
        layer.borderColor = containerBorderColor.CGColor
        layer.borderWidth = 1.0
    }

    private val selectionIndicator = UIView().apply {
        backgroundColor = selectedBackgroundColor
        layer.cornerRadius = 12.0
        layer.masksToBounds = true
    }

    private val buttons = labels.mapIndexed { index, label ->
        UIButton.buttonWithType(UIButtonTypeSystem).apply {
            tag = index.toLong()
            setTitle(label, forState = UIControlStateNormal)
            titleLabel?.font = UIFont.systemFontOfSize(13.0)
            backgroundColor = UIColor.clearColor
            addTarget(
                target = target,
                action = NSSelectorFromString("tabSelected:"),
                forControlEvents = UIControlEventTouchUpInside,
            )
        }
    }

    private val stackView = UIStackView(arrangedSubviews = buttons).apply {
        axis = UILayoutConstraintAxisHorizontal
        distribution = UIStackViewDistributionFillEqually
        spacing = 4.0
        backgroundColor = UIColor.clearColor
        overrideUserInterfaceStyle = interfaceStyle
    }

    init {
        backgroundColor = shelfColor
        addSubview(tabContainer)
        tabContainer.addSubview(selectionIndicator)
        tabContainer.addSubview(stackView)
        update(
            labels = labels,
            selectedIndex = selectedIndex,
            shelfColor = shelfColor,
            containerColor = containerColor,
            containerBorderColor = containerBorderColor,
            selectedBackgroundColor = selectedBackgroundColor,
            selectedContentColor = selectedContentColor,
            inactiveContentColor = inactiveContentColor,
            interfaceStyle = interfaceStyle,
        )
    }

    fun update(
        labels: List<String>,
        selectedIndex: Int,
        shelfColor: UIColor,
        containerColor: UIColor,
        containerBorderColor: UIColor,
        selectedBackgroundColor: UIColor,
        selectedContentColor: UIColor,
        inactiveContentColor: UIColor,
        interfaceStyle: UIUserInterfaceStyle,
    ) {
        val selectionChanged = selectedTabIndex != selectedIndex
        selectedTabIndex = selectedIndex.coerceIn(0, (buttons.lastIndex).coerceAtLeast(0))
        backgroundColor = shelfColor
        tabContainer.backgroundColor = containerColor
        tabContainer.layer.cornerRadius = 14.0
        tabContainer.layer.masksToBounds = true
        tabContainer.layer.borderColor = containerBorderColor.CGColor
        tabContainer.layer.borderWidth = 1.0
        overrideUserInterfaceStyle = interfaceStyle
        stackView.backgroundColor = UIColor.clearColor
        stackView.overrideUserInterfaceStyle = interfaceStyle
        selectionIndicator.backgroundColor = selectedBackgroundColor
        selectionIndicator.layer.cornerRadius = 12.0
        buttons.forEachIndexed { index, button ->
            val selected = index == selectedIndex
            button.setTitle(labels.getOrNull(index).orEmpty(), forState = UIControlStateNormal)
            button.setTitleColor(
                if (selected) selectedContentColor else inactiveContentColor,
                forState = UIControlStateNormal,
            )
            button.backgroundColor = UIColor.clearColor
            button.titleLabel?.font = if (selected) {
                UIFont.boldSystemFontOfSize(13.0)
            } else {
                UIFont.systemFontOfSize(13.0)
            }
            button.accessibilityTraits = if (selected) {
                UIAccessibilityTraitButton or UIAccessibilityTraitSelected
            } else {
                UIAccessibilityTraitButton
            }
            button.overrideUserInterfaceStyle = interfaceStyle
        }
        if (selectionChanged && bounds.useContents { size.width } > 0.0) {
            UIView.animateWithDuration(
                duration = 0.22,
                animations = { selectionIndicator.setFrame(selectionIndicatorFrame()) },
            )
        } else {
            selectionIndicator.setFrame(selectionIndicatorFrame())
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val width = bounds.useContents { size.width }
        val tabWidth = (width - 32.0).coerceAtLeast(0.0)
        val tabHeight = 36.0
        tabContainer.setFrame(CGRectMake(
            x = 16.0,
            y = 8.0,
            width = tabWidth,
            height = tabHeight,
        ))
        stackView.setFrame(CGRectMake(
            x = 0.0,
            y = 0.0,
            width = tabWidth,
            height = tabHeight,
        ))
        selectionIndicator.setFrame(selectionIndicatorFrame())
    }

    private fun selectionIndicatorFrame() = run {
        val width = tabContainer.bounds.useContents { size.width }
        val height = tabContainer.bounds.useContents { size.height }
        val gap = 4.0
        val inset = 2.0
        val itemWidth = ((width - inset * 2 - gap * (buttons.size - 1)) / buttons.size).coerceAtLeast(0.0)
        CGRectMake(
            x = inset + selectedTabIndex * (itemWidth + gap),
            y = inset,
            width = itemWidth,
            height = (height - inset * 2).coerceAtLeast(0.0),
        )
    }
}

@Composable
private fun platformInterfaceStyle(): UIUserInterfaceStyle =
    if (MaterialTheme.colors.isLight) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight
    } else {
        UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class QuranSearchBarTarget(
    var onValueChange: (String) -> Unit,
) : NSObject(), UISearchBarDelegateProtocol {
    override fun searchBar(searchBar: UISearchBar, textDidChange: String) {
        onValueChange(textDidChange)
    }

    override fun searchBarSearchButtonClicked(searchBar: UISearchBar) {
        searchBar.resignFirstResponder()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class QuranTabsTarget(
    var onSelected: (Int) -> Unit,
) : NSObject() {
    @Suppress("unused")
    @ObjCAction
    fun tabSelected(sender: UIButton) {
        onSelected(sender.tag.toInt())
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class QuranSurahStatusButtonTarget : NSObject() {
    var statusTitle: String = ""
    var notStartedLabel: String = ""
    var learningLabel: String = ""
    var learnedLabel: String = ""
    var cancelLabel: String = ""
    var onStatusSelected: (SurahTodoStatus?) -> Unit = {}

    @Suppress("unused")
    @ObjCAction
    fun statusButtonTapped(sender: UIButton) {
        val presenter = sender.window?.rootViewController?.topPresentedController() ?: return
        val alert = UIAlertController.alertControllerWithTitle(
            title = statusTitle,
            message = null,
            preferredStyle = UIAlertControllerStyleActionSheet,
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = notStartedLabel,
                style = UIAlertActionStyleDefault,
                handler = { onStatusSelected(null) },
            ),
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = learningLabel,
                style = UIAlertActionStyleDefault,
                handler = { onStatusSelected(SurahTodoStatus.LEARNING) },
            ),
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = learnedLabel,
                style = UIAlertActionStyleDefault,
                handler = { onStatusSelected(SurahTodoStatus.LEARNED) },
            ),
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = cancelLabel,
                style = UIAlertActionStyleCancel,
                handler = null,
            ),
        )
        // Action sheets belong at the bottom on iPhone. Anchoring the popover on
        // every device made the three status options overlap the sticky filters.
        // iPad requires an anchor for this presentation style, so retain it there.
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            alert.popoverPresentationController()?.apply {
                sourceView = sender
                sourceRect = sender.bounds
            }
        }
        presenter.presentViewController(alert, animated = true, completion = null)
    }
}

private fun UIViewController.topPresentedController(): UIViewController {
    var current = this
    while (current.presentedViewController != null) {
        current = current.presentedViewController ?: break
    }
    return current
}

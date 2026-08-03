package tj.app.quran_todo.presentation.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

/** Root navigation for the authenticated Hifz experience. */
interface HifzRootComponent {
    val childStack: Value<ChildStack<Config, Child>>

    fun openSettings()

    fun openSurah(surahNumber: Int)

    fun goBack()

    @Serializable
    sealed interface Config {
        @Serializable
        data object Quran : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data class Surah(val number: Int) : Config
    }

    sealed interface Child {
        data object Quran : Child
        data object Settings : Child

        data class Surah(val number: Int) : Child
    }
}

class DefaultHifzRootComponent(
    componentContext: ComponentContext,
    initialSurahNumber: Int? = null,
) : HifzRootComponent, ComponentContext by componentContext {
    private val navigation = StackNavigation<HifzRootComponent.Config>()

    override val childStack: Value<ChildStack<HifzRootComponent.Config, HifzRootComponent.Child>> = childStack(
        source = navigation,
        serializer = HifzRootComponent.Config.serializer(),
        initialConfiguration = initialSurahNumber?.let(HifzRootComponent.Config::Surah)
            ?: HifzRootComponent.Config.Quran,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override fun openSettings() {
        navigation.push(HifzRootComponent.Config.Settings)
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun openSurah(surahNumber: Int) {
        navigation.push(HifzRootComponent.Config.Surah(surahNumber))
    }

    override fun goBack() {
        navigation.pop()
    }

    private fun createChild(config: HifzRootComponent.Config, @Suppress("UNUSED_PARAMETER") context: ComponentContext): HifzRootComponent.Child =
        when (config) {
            HifzRootComponent.Config.Quran -> HifzRootComponent.Child.Quran
            HifzRootComponent.Config.Settings -> HifzRootComponent.Child.Settings
            is HifzRootComponent.Config.Surah -> HifzRootComponent.Child.Surah(config.number)
        }
}

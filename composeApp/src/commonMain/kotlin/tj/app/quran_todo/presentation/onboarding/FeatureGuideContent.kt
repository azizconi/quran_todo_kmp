package tj.app.quran_todo.presentation.onboarding

import tj.app.quran_todo.common.i18n.AppStrings

enum class FeatureGuidePreviewKind {
    RECITATION,
    EXAM_MODE,
    WEAK_BANK,
    VOICE_CHECK,
    AB_COMPARE,
    OFFLINE_PACKAGE,
    PROJECTION,
    RECITATION_REPORT,
}

data class FeatureGuideItem(
    val title: String,
    val description: String,
    val previewKind: FeatureGuidePreviewKind,
)

fun featureGuideItems(strings: AppStrings): List<FeatureGuideItem> {
    return listOf(
        FeatureGuideItem(
            title = strings.recitationCoachTitle,
            description = strings.featureGuideRecitationBody,
            previewKind = FeatureGuidePreviewKind.RECITATION
        ),
        FeatureGuideItem(
            title = strings.examModeDefaultLabel,
            description = strings.featureGuideExamBody,
            previewKind = FeatureGuidePreviewKind.EXAM_MODE
        ),
        FeatureGuideItem(
            title = strings.weakAyahBankTitle,
            description = strings.featureGuideWeakBody,
            previewKind = FeatureGuidePreviewKind.WEAK_BANK
        ),
        FeatureGuideItem(
            title = strings.voiceLabel,
            description = strings.featureGuideVoiceBody,
            previewKind = FeatureGuidePreviewKind.VOICE_CHECK
        ),
        FeatureGuideItem(
            title = strings.abLabel,
            description = strings.featureGuideAbBody,
            previewKind = FeatureGuidePreviewKind.AB_COMPARE
        ),
        FeatureGuideItem(
            title = strings.offlinePackageTitle,
            description = strings.featureGuideOfflineBody,
            previewKind = FeatureGuidePreviewKind.OFFLINE_PACKAGE
        ),
        FeatureGuideItem(
            title = strings.projectionTitle,
            description = strings.featureGuideProjectionBody,
            previewKind = FeatureGuidePreviewKind.PROJECTION
        ),
        FeatureGuideItem(
            title = strings.recitationReportTitle,
            description = strings.featureGuideRecitationReportBody,
            previewKind = FeatureGuidePreviewKind.RECITATION_REPORT
        ),
    )
}

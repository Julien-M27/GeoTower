package fr.geotower.ui.screens.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.geotower.R

internal data class ReleaseNotes(
    val sections: List<ReleaseNoteSection>
)

internal data class ReleaseNoteSection(
    val title: String,
    val entries: List<ReleaseNoteEntry>
)

internal sealed interface ReleaseNoteEntry

internal data class ReleaseNoteGroup(
    val title: String,
    val items: List<String>
) : ReleaseNoteEntry

internal data class ReleaseNoteItem(
    val text: String
) : ReleaseNoteEntry

@Composable
internal fun currentReleaseNotes(): ReleaseNotes = releaseNotes {
    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_active_filters_banner))
        item(stringResource(R.string.appstrings_release_map_share_qr_deeplink))
        item(stringResource(R.string.appstrings_release_map_open_position_link))
    }

    section(stringResource(R.string.appstrings_release_section_live_tracking)) {
        item(stringResource(R.string.appstrings_release_live_tracking_no_default_operator))
        item(stringResource(R.string.appstrings_release_live_tracking_nearest_active_site))
        item(stringResource(R.string.appstrings_release_live_tracking_context_texts))
        item(stringResource(R.string.appstrings_release_live_tracking_home_stop))
        item(stringResource(R.string.appstrings_release_live_tracking_onboarding_earlier))
    }

    section(stringResource(R.string.appstrings_release_section_navigation_loading)) {
        item(stringResource(R.string.appstrings_release_navigation_breadcrumbs))
        item(stringResource(R.string.appstrings_release_loading_messages_enriched))
    }

    section(stringResource(R.string.appstrings_release_section_nearby)) {
        item(stringResource(R.string.appstrings_release_nearby_map_filters_aligned))
        item(stringResource(R.string.appstrings_release_nearby_status_zb_underground_filters))
        item(stringResource(R.string.appstrings_release_nearby_grouping_improved))
    }

    section(stringResource(R.string.appstrings_release_section_statistics_data)) {
        item(stringResource(R.string.appstrings_release_statistics_online_data))
        item(stringResource(R.string.appstrings_release_statistics_frequency_active_sites_filters))
        item(stringResource(R.string.appstrings_release_statistics_5g_experimental_order))
    }
}

@Composable
private fun releaseNotes(content: @Composable ReleaseNotesBuilder.() -> Unit): ReleaseNotes {
    val builder = ReleaseNotesBuilder()
    builder.content()
    return ReleaseNotes(builder.sections)
}

private class ReleaseNotesBuilder {
    val sections = mutableListOf<ReleaseNoteSection>()

    @Composable
    fun section(title: String, content: @Composable ReleaseNoteSectionBuilder.() -> Unit) {
        val builder = ReleaseNoteSectionBuilder()
        builder.content()
        sections += ReleaseNoteSection(
            title = title,
            entries = builder.entries
        )
    }
}

private class ReleaseNoteSectionBuilder {
    val entries = mutableListOf<ReleaseNoteEntry>()

    @Composable
    fun group(title: String, content: @Composable ReleaseNoteGroupBuilder.() -> Unit) {
        val builder = ReleaseNoteGroupBuilder()
        builder.content()
        entries += ReleaseNoteGroup(
            title = title,
            items = builder.items
        )
    }

    @Composable
    fun item(text: String) {
        entries += ReleaseNoteItem(text)
    }
}

private class ReleaseNoteGroupBuilder {
    val items = mutableListOf<String>()

    @Composable
    fun item(text: String) {
        items += text
    }
}

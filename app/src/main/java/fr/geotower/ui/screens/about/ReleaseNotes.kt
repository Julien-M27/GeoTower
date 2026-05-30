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
        item(stringResource(R.string.appstrings_release_map_frequency_filters_consistent))
        item(stringResource(R.string.appstrings_release_map_frequency_change_refresh))
        item(stringResource(R.string.appstrings_release_map_clusters_frequency_filter_adapted))
        item(stringResource(R.string.appstrings_release_map_support_count_more_reliable))
    }

    section(stringResource(R.string.appstrings_release_section_site_detail)) {
        item(stringResource(R.string.appstrings_release_site_detail_filtered_bands_distinguished))
    }

    section(stringResource(R.string.appstrings_release_section_support_detail)) {
        item(stringResource(R.string.appstrings_release_support_detail_operators_frequency_highlight))
    }

    section(stringResource(R.string.appstrings_release_section_map_settings)) {
        item(stringResource(R.string.appstrings_release_map_settings_frequency_order))
    }

    section(stringResource(R.string.appstrings_release_section_about)) {
        item(stringResource(R.string.appstrings_release_about_quarterly_data_version))
        item(stringResource(R.string.appstrings_release_about_quarterly_data_translations))
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

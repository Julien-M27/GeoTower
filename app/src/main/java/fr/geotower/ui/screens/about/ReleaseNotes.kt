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
    section(stringResource(R.string.appstrings_release_section_search)) {
        item(stringResource(R.string.appstrings_release_search_coverage_better))
        item(stringResource(R.string.appstrings_release_search_unavailable_message_clearer))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_measurement_enriched))
        item(stringResource(R.string.appstrings_release_map_measurement_multiple_points))
        item(stringResource(R.string.appstrings_release_map_measurement_unit_choice))
        item(stringResource(R.string.appstrings_release_map_measurement_remove_easier))
    }

    section(stringResource(R.string.appstrings_release_section_antenna_details)) {
        item(stringResource(R.string.appstrings_release_antenna_details_identical_panels))
        item(stringResource(R.string.appstrings_release_antenna_details_panel_id_display))
        item(stringResource(R.string.appstrings_release_antenna_details_panel_id_copy))
    }

    section(stringResource(R.string.appstrings_release_section_fh_details)) {
        item(stringResource(R.string.appstrings_release_fh_details_missing_info_cleaner))
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

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
    section(stringResource(R.string.appstrings_release_section_image_sharing)) {
        item(stringResource(R.string.appstrings_release_image_sharing_copy_action))
        item(stringResource(R.string.appstrings_release_image_sharing_supported_pages))
        item(stringResource(R.string.appstrings_release_elevation_profile_operator_banner))
    }

    section(stringResource(R.string.appstrings_release_section_refresh)) {
        item(stringResource(R.string.appstrings_release_refresh_pull_to_refresh))
        item(stringResource(R.string.appstrings_release_refresh_keeps_visible_data))
    }

    section(stringResource(R.string.appstrings_release_section_nearby)) {
        item(stringResource(R.string.appstrings_release_nearby_radius_load_more))
        item(stringResource(R.string.appstrings_release_nearby_large_filtered_results))
    }

    section(stringResource(R.string.appstrings_release_section_live_tracking)) {
        item(stringResource(R.string.appstrings_release_live_tracking_gps_frequency))
        item(stringResource(R.string.appstrings_release_live_tracking_frequency_live_apply))
    }

    section(stringResource(R.string.appstrings_release_section_throughput_calculator)) {
        item(stringResource(R.string.appstrings_release_throughput_simplified_profile))
        item(stringResource(R.string.appstrings_release_throughput_modes_removed))
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

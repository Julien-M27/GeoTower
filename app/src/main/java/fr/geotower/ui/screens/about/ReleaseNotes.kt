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
    section(stringResource(R.string.appstrings_release_section_speedtests)) {
        item(stringResource(R.string.appstrings_release_speedtests_all_page))
        item(stringResource(R.string.appstrings_release_speedtests_sort_filters_details))
        item(stringResource(R.string.appstrings_release_speedtests_best_card_clickable))
        item(stringResource(R.string.appstrings_release_speedtests_best_metric_setting))
    }

    section(stringResource(R.string.appstrings_release_section_widgets)) {
        item(stringResource(R.string.appstrings_release_widgets_mini_map))
        item(stringResource(R.string.appstrings_release_widgets_nearby_antennas))
        item(stringResource(R.string.appstrings_release_widgets_open_map))
        item(stringResource(R.string.appstrings_release_widgets_previews_improved))
    }

    section(stringResource(R.string.appstrings_release_section_live_tracking)) {
        item(stringResource(R.string.appstrings_release_live_tracking_compact_display))
        item(stringResource(R.string.appstrings_release_live_tracking_tracker_icon))
        item(stringResource(R.string.appstrings_release_live_tracking_site_photo_focus))
    }

    section(stringResource(R.string.appstrings_release_section_global)) {
        item(stringResource(R.string.appstrings_release_global_feature_availability))
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

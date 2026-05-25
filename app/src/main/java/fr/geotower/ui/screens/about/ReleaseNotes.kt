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
    section(stringResource(R.string.appstrings_release_section_global)) {
        item(stringResource(R.string.appstrings_release_global_internal_stability_update))
        item(stringResource(R.string.appstrings_release_global_reliable_network_background_services))
        item(stringResource(R.string.appstrings_release_global_permissions_gps_compass_adjusted))
    }

    section(stringResource(R.string.appstrings_release_section_signalquest)) {
        item(stringResource(R.string.appstrings_release_signalquest_private_photo_data_removed))
        item(stringResource(R.string.appstrings_release_signalquest_speedtest_filtering_improved))
        item(stringResource(R.string.appstrings_release_signalquest_pagination_fixed))
        item(stringResource(R.string.appstrings_release_signalquest_network_display_clearer))
    }

    section(stringResource(R.string.appstrings_release_section_widgets)) {
        item(stringResource(R.string.appstrings_release_widgets_refresh_more_reliable))
    }

    section(stringResource(R.string.appstrings_release_section_interface)) {
        item(stringResource(R.string.appstrings_release_interface_speedtest_translations_completed))
        item(stringResource(R.string.appstrings_release_interface_plurals_fixed))
        item(stringResource(R.string.appstrings_release_interface_map_share_improvements))
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

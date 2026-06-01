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
    section(stringResource(R.string.appstrings_release_section_interface)) {
        item(stringResource(R.string.appstrings_release_interface_orientation_adapted))
        item(stringResource(R.string.appstrings_release_interface_large_screens))
        item(stringResource(R.string.appstrings_release_interface_two_pane_large_screens))
    }

    section(stringResource(R.string.appstrings_release_section_compass)) {
        item(stringResource(R.string.appstrings_release_compass_rotation_reliable))
        item(stringResource(R.string.appstrings_release_compass_landscape_compact))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_landscape_reorganized))
        item(stringResource(R.string.appstrings_release_map_search_tools_easier))
        item(stringResource(R.string.appstrings_release_map_buttons_consistent))
    }

    section(stringResource(R.string.appstrings_release_section_notifications)) {
        item(stringResource(R.string.appstrings_release_notifications_live_visual))
        item(stringResource(R.string.appstrings_release_notifications_live_text_clearer))
        item(stringResource(R.string.appstrings_release_notifications_live_compatibility))
    }

    section(stringResource(R.string.appstrings_release_section_global)) {
        item(stringResource(R.string.appstrings_release_global_outage_label_down))
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

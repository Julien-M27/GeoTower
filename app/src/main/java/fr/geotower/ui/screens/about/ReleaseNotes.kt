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
        item(stringResource(R.string.appstrings_release_global_flexible_features))
        item(stringResource(R.string.appstrings_release_global_home_announcement))
        item(stringResource(R.string.appstrings_release_global_unavailable_pages))
        item(stringResource(R.string.appstrings_release_global_localized_update_notes))
        item(stringResource(R.string.appstrings_release_global_database_version_check))
    }

    section(stringResource(R.string.appstrings_release_section_statistics)) {
        item(stringResource(R.string.appstrings_release_statistics_declared_active_sites))
        item(stringResource(R.string.appstrings_release_statistics_weekly_trends))
        item(stringResource(R.string.appstrings_release_statistics_custom_frequency_display))
    }

    section(stringResource(R.string.appstrings_release_section_settings)) {
        item(stringResource(R.string.appstrings_release_settings_shortcuts_across_screens))
    }

    section(stringResource(R.string.appstrings_release_section_community_services)) {
        item(stringResource(R.string.appstrings_release_community_cellularfr_availability))
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

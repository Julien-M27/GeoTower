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
    section(stringResource(R.string.appstrings_release_section_languages)) {
        item(stringResource(R.string.appstrings_release_languages_complete_management))
        item(stringResource(R.string.appstrings_release_languages_expanded_translations))
    }

    section(stringResource(R.string.appstrings_release_section_interface)) {
        item(stringResource(R.string.appstrings_release_interface_better_translation))
        item(stringResource(R.string.appstrings_release_interface_consistent_texts))
    }

    section(stringResource(R.string.appstrings_release_section_signalquest)) {
        item(stringResource(R.string.appstrings_release_signalquest_reliable_uploads))
        item(stringResource(R.string.appstrings_release_signalquest_failure_recovery))
        item(stringResource(R.string.appstrings_release_signalquest_photo_status))
    }

    section(stringResource(R.string.appstrings_release_section_notifications)) {
        item(stringResource(R.string.appstrings_release_notifications_live_improved))
        item(stringResource(R.string.appstrings_release_notifications_site_photo))
    }

    section(stringResource(R.string.appstrings_release_section_database)) {
        item(stringResource(R.string.appstrings_release_database_older_support))
        item(stringResource(R.string.appstrings_release_database_careful_cleanup))
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

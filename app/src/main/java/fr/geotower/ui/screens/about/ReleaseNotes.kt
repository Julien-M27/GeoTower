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
    section(stringResource(R.string.appstrings_release_section_security)) {
        item(stringResource(R.string.appstrings_release_security_downloads_verified))
        item(stringResource(R.string.appstrings_release_security_official_sources))
        item(stringResource(R.string.appstrings_release_security_downloaded_files_checked))
    }

    section(stringResource(R.string.appstrings_release_section_offline_maps)) {
        item(stringResource(R.string.appstrings_release_offline_maps_invalid_rejected))
        item(stringResource(R.string.appstrings_release_offline_maps_cancel_cleanup))
        item(stringResource(R.string.appstrings_release_offline_maps_download_checks))
    }

    section(stringResource(R.string.appstrings_release_section_signalquest)) {
        item(stringResource(R.string.appstrings_release_signalquest_upload_more_secure))
    }

    section(stringResource(R.string.appstrings_release_section_global)) {
        item(stringResource(R.string.appstrings_release_global_release_optimized_protected))
        item(stringResource(R.string.appstrings_release_global_internal_checks_reinforced))
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

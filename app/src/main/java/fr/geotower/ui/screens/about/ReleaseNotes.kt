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
    section(stringResource(R.string.appstrings_release_section_radio_data)) {
        item(stringResource(R.string.appstrings_release_radio_data_annex_database))
        item(stringResource(R.string.appstrings_release_radio_data_download_checks))
        item(stringResource(R.string.appstrings_release_radio_data_about_version))
        item(stringResource(R.string.appstrings_release_radio_data_settings))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_radio_markers))
        item(stringResource(R.string.appstrings_release_map_radio_filters))
        item(stringResource(R.string.appstrings_release_map_radio_readability))
        item(stringResource(R.string.appstrings_release_map_radio_detail))
    }

    section(stringResource(R.string.appstrings_release_section_5g)) {
        item(stringResource(R.string.appstrings_release_5g_new_frequencies))
        item(stringResource(R.string.appstrings_release_5g_consistent_data))
    }

    section(stringResource(R.string.appstrings_release_section_photos)) {
        item(stringResource(R.string.appstrings_release_photos_android_share))
        item(stringResource(R.string.appstrings_release_photos_map_draft))
        item(stringResource(R.string.appstrings_release_photos_support_operator_choice))
        item(stringResource(R.string.appstrings_release_photos_multi_operator_upload))
    }

    section(stringResource(R.string.appstrings_release_section_community_photos)) {
        item(stringResource(R.string.appstrings_release_community_photos_copy_download))
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

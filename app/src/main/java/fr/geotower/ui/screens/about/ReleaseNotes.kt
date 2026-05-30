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
    section(stringResource(R.string.appstrings_release_section_sites_supports)) {
        item(stringResource(R.string.appstrings_release_sites_supports_operator_added))
        item(stringResource(R.string.appstrings_release_sites_supports_arcep_label_operator))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_zb_only_filter))
        item(stringResource(R.string.appstrings_release_map_zb_filter_everywhere))
        item(stringResource(R.string.appstrings_release_map_zb_quick_search))
    }

    section(stringResource(R.string.appstrings_release_section_spectrums)) {
        item(stringResource(R.string.appstrings_release_spectrums_precise_readable))
        item(stringResource(R.string.appstrings_release_spectrums_totals_bands_better))
    }

    section(stringResource(R.string.appstrings_release_section_notifications)) {
        item(stringResource(R.string.appstrings_release_notifications_large_icons_flexible))
    }

    section(stringResource(R.string.appstrings_release_section_global)) {
        item(stringResource(R.string.appstrings_release_global_local_data_new_info))
        item(stringResource(R.string.appstrings_release_global_translations_updated))
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

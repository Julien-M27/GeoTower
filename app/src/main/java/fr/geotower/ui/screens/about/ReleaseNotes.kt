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
    section(stringResource(R.string.appstrings_release_section_hs_sites)) {
        item(stringResource(R.string.appstrings_release_hs_sites_data_enriched))
        item(stringResource(R.string.appstrings_release_hs_sites_arcep_source_date))
        item(stringResource(R.string.appstrings_release_hs_sites_service_details))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_site_status_clearer))
        item(stringResource(R.string.appstrings_release_map_status_legend_improved))
        item(stringResource(R.string.appstrings_release_map_arcep_details_easier))
        item(stringResource(R.string.appstrings_release_map_single_support_groups))
    }

    section(stringResource(R.string.appstrings_release_section_offline_maps)) {
        item(stringResource(R.string.appstrings_release_offline_maps_section_collapsible))
        item(stringResource(R.string.appstrings_release_offline_maps_auto_open))
    }

    section(stringResource(R.string.appstrings_release_section_about)) {
        item(stringResource(R.string.appstrings_release_about_anfr_arcep_sources))
        item(stringResource(R.string.appstrings_release_about_dates_quarters_localized))
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

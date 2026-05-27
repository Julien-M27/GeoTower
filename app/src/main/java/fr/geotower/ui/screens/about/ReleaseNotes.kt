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
        item(stringResource(R.string.appstrings_release_global_anfr_underground_supports))
        item(stringResource(R.string.appstrings_release_global_settings_sharing_consistent))
        item(stringResource(R.string.appstrings_release_global_translations_updated))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_map_hide_underground_sites_filter))
        item(stringResource(R.string.appstrings_release_map_nearby_search_more_precise))
    }

    section(stringResource(R.string.appstrings_release_section_sites_sharing)) {
        item(stringResource(R.string.appstrings_release_sites_arcep_nidt_display_copy))
        item(stringResource(R.string.appstrings_release_sites_zb_badge))
        item(stringResource(R.string.appstrings_release_sites_outage_dates))
    }

    section(stringResource(R.string.appstrings_release_section_live_tracking)) {
        item(stringResource(R.string.appstrings_release_live_tracking_hs_notification))
        item(stringResource(R.string.appstrings_release_live_tracking_operator_change_refresh))
        item(stringResource(R.string.appstrings_release_live_tracking_search_photo_reliability))
    }

    section(stringResource(R.string.appstrings_release_section_signalquest)) {
        item(stringResource(R.string.appstrings_release_signalquest_private_data_upload_setting))
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

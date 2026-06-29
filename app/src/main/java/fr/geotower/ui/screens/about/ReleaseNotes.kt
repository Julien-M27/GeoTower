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
    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_v1599_map_coverage_points))
        item(stringResource(R.string.appstrings_release_v1599_map_theoretical_coverage))
        item(stringResource(R.string.appstrings_release_v1599_map_history_slider))
    }

    section(stringResource(R.string.appstrings_release_section_settings)) {
        item(stringResource(R.string.appstrings_release_v1599_settings_search))
        item(stringResource(R.string.appstrings_release_v1599_settings_theme_button))
        item(stringResource(R.string.appstrings_release_v1599_settings_diagnostic))
        item(stringResource(R.string.appstrings_release_v1599_settings_battery_opt))
    }

    section(stringResource(R.string.appstrings_release_section_live_tracking)) {
        item(stringResource(R.string.appstrings_release_v1599_live_gps_accuracy))
    }

    section(stringResource(R.string.appstrings_release_section_preference_profiles)) {
        item(stringResource(R.string.appstrings_release_v1599_profiles_rename))
        item(stringResource(R.string.appstrings_release_v1599_profiles_import_preview))
        item(stringResource(R.string.appstrings_release_v1599_profiles_export_selective))
        item(stringResource(R.string.appstrings_release_v1599_profiles_changes_count))
    }

    section(stringResource(R.string.appstrings_release_section_elevation_profile)) {
        item(stringResource(R.string.appstrings_release_v1599_elevation_obstacles))
    }

    section(stringResource(R.string.appstrings_release_section_share_export)) {
        item(stringResource(R.string.appstrings_release_v1599_share_radio_report))
        item(stringResource(R.string.appstrings_release_v1599_share_site_pdf))
        item(stringResource(R.string.appstrings_release_v1599_share_community_photos))
    }

    section(stringResource(R.string.appstrings_release_section_site_detail)) {
        item(stringResource(R.string.appstrings_release_v1599_site_potential_outage))
        item(stringResource(R.string.appstrings_release_v1599_site_cellmapper))
        item(stringResource(R.string.appstrings_release_v1599_site_aer_id))
        item(stringResource(R.string.appstrings_release_v1599_site_throughput_height))
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

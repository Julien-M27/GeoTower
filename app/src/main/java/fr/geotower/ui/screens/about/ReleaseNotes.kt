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
    section(stringResource(R.string.appstrings_release_section_online_data)) {
        item(stringResource(R.string.appstrings_release_online_data_fallback_mode))
        item(stringResource(R.string.appstrings_release_online_data_map_nearby_compass))
        item(stringResource(R.string.appstrings_release_online_data_warning))
    }

    section(stringResource(R.string.appstrings_release_section_home_onboarding)) {
        item(stringResource(R.string.appstrings_release_home_onboarding_local_db_access))
        item(stringResource(R.string.appstrings_release_home_onboarding_radio_db_download))
    }

    section(stringResource(R.string.appstrings_release_section_map_details)) {
        item(stringResource(R.string.appstrings_release_map_details_special_support_ids))
        item(stringResource(R.string.appstrings_release_map_details_online_results))
    }

    section(stringResource(R.string.appstrings_release_section_interface_translations)) {
        item(stringResource(R.string.appstrings_release_interface_encoding_accents))
        item(stringResource(R.string.appstrings_release_interface_arcep_status_translations))
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

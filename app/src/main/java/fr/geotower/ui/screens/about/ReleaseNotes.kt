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
        item(stringResource(R.string.appstrings_release_v1966_simple_mode))
        item(stringResource(R.string.appstrings_release_v1966_simple_mode_banner))
    }

    section(stringResource(R.string.appstrings_release_section_notifications)) {
        item(stringResource(R.string.appstrings_release_v1966_notifications_master))
        item(stringResource(R.string.appstrings_release_v1966_live_without_notif))
        item(stringResource(R.string.appstrings_release_v1966_onboarding_notifications))
    }

    section(stringResource(R.string.appstrings_release_section_settings)) {
        item(stringResource(R.string.appstrings_release_v1966_page_customization_discovery))
        item(stringResource(R.string.appstrings_release_v1966_outage_source_merged))
    }

    section(stringResource(R.string.appstrings_release_section_support_detail)) {
        item(stringResource(R.string.appstrings_release_v1966_operators_actions))
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

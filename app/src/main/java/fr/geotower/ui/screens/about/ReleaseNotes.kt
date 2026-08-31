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

/**
 * Nouveautes de la version EN COURS uniquement : la carte s'intitule « Nouveautes de la version X »
 * (`about_new_for_version`), donc tout ce qui reste ici est presente comme appartenant a X. A chaque
 * publication, on remplace le contenu plutot que d'y ajouter une couche.
 */
@Composable
internal fun currentReleaseNotes(): ReleaseNotes = releaseNotes {
    section(stringResource(R.string.appstrings_release_section_android_auto)) {
        item(stringResource(R.string.appstrings_release_v2039_android_auto))
    }

    section(stringResource(R.string.appstrings_release_section_database)) {
        item(stringResource(R.string.appstrings_release_v2039_enb_operator_counts))
        item(stringResource(R.string.appstrings_release_v2039_onboarding_database_download))
    }

    section(stringResource(R.string.appstrings_release_section_spectrums)) {
        item(stringResource(R.string.appstrings_release_v2038_frequency_reference))
    }

    section(stringResource(R.string.appstrings_release_section_share_export)) {
        item(stringResource(R.string.appstrings_release_v2038_trip_import))
    }

    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_v2038_map_readability))
        item(stringResource(R.string.appstrings_release_v2038_page_customization))
    }

    section(stringResource(R.string.appstrings_release_section_about)) {
        item(stringResource(R.string.appstrings_release_v2038_logo_theme))
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

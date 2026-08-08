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
    section(stringResource(R.string.appstrings_release_section_map)) {
        item(stringResource(R.string.appstrings_release_v2014_smooth_location))
    }

    section(stringResource(R.string.appstrings_release_section_site_detail)) {
        item(stringResource(R.string.appstrings_release_v2014_announced_bands))
        item(stringResource(R.string.appstrings_release_v2014_azimuth_placeholder))
    }

    section(stringResource(R.string.appstrings_release_section_settings)) {
        item(stringResource(R.string.appstrings_release_v2014_pages_section))
        item(stringResource(R.string.appstrings_release_v2014_pages_search))
    }

    section(stringResource(R.string.appstrings_release_section_database)) {
        item(stringResource(R.string.appstrings_release_v2014_local_build_budgets))
        item(stringResource(R.string.appstrings_release_v2014_local_build_cost))
        item(stringResource(R.string.appstrings_release_v2014_local_build_announced))
    }

    section(stringResource(R.string.appstrings_release_section_performance)) {
        item(stringResource(R.string.appstrings_release_v2014_local_build_speed))
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

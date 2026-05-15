package fr.geotower.ui.screens.about

import androidx.compose.runtime.Composable
import fr.geotower.utils.AppStrings

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
    section("Général", "General", "Geral") {
        item(
            "Modification des sélecteurs en mode normal",
            "Updated selectors in normal mode",
            "Alteração dos seletores no modo normal"
        )
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
    fun section(
        fr: String,
        en: String,
        pt: String,
        content: @Composable ReleaseNoteSectionBuilder.() -> Unit
    ) {
        val builder = ReleaseNoteSectionBuilder()
        builder.content()
        sections += ReleaseNoteSection(
            title = AppStrings.get(fr, en, pt),
            entries = builder.entries
        )
    }
}

private class ReleaseNoteSectionBuilder {
    val entries = mutableListOf<ReleaseNoteEntry>()

    @Composable
    fun group(
        fr: String,
        en: String,
        pt: String,
        content: @Composable ReleaseNoteGroupBuilder.() -> Unit
    ) {
        val builder = ReleaseNoteGroupBuilder()
        builder.content()
        entries += ReleaseNoteGroup(
            title = AppStrings.get(fr, en, pt),
            items = builder.items
        )
    }

    @Composable
    fun item(fr: String, en: String, pt: String) {
        entries += ReleaseNoteItem(AppStrings.get(fr, en, pt))
    }
}

private class ReleaseNoteGroupBuilder {
    val items = mutableListOf<String>()

    @Composable
    fun item(fr: String, en: String, pt: String) {
        items += AppStrings.get(fr, en, pt)
    }
}

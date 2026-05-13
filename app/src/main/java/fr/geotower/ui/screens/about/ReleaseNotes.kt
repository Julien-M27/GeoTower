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
            "Suppression du code inutilisé",
            "Removed unused code",
            "Remoção de código não utilizado"
        )
        item(
            "Suppression du code dupliqué",
            "Removed duplicated code",
            "Remoção de código duplicado"
        )
        item(
            "Ajout de la vérification des mises à jour de l'application",
            "Added app update checks",
            "Adição da verificação de atualizações da aplicação"
        )
    }

    section("Paramètres", "Settings", "Definições") {
        item(
            "Suppression des notifications de fin de téléchargement si l'élément est affiché à l'écran",
            "Removed download-complete notifications when the item is displayed on screen",
            "Remoção das notificações de fim de transferência quando o elemento está visível no ecrã"
        )
        item(
            "Ajout d'une notification de fin de téléchargement d'une carte",
            "Added a download-complete notification for maps",
            "Adição de uma notificação de fim de transferência de mapa"
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

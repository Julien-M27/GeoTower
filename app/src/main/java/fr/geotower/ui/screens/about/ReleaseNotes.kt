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
    section("SignalQuest", "SignalQuest", "SignalQuest") {
        item(
            "Ajout d'un historique local des envois photo",
            "Added a local history of photo uploads",
            "Adição de um histórico local dos envios de fotos"
        )
        item(
            "Ajout du suivi de validation des photos envoyées",
            "Added validation tracking for uploaded photos",
            "Adição do acompanhamento de validação das fotos enviadas"
        )
        item(
            "Amélioration de l'envoi des photos et des métadonnées",
            "Improved photo and metadata uploads",
            "Melhoria do envio de fotos e metadados"
        )
    }

    section("Galerie communautaire", "Community gallery", "Galeria comunitária") {
        item(
            "Affichage des informations EXIF des photos",
            "Displayed photo EXIF information",
            "Apresentação das informações EXIF das fotos"
        )
        item(
            "Ajout d'une mini-carte lorsqu'une position GPS est disponible",
            "Added a mini map when a GPS position is available",
            "Adição de um minimapa quando há uma posição GPS disponível"
        )
    }

    section("Photos", "Photos", "Fotos") {
        item(
            "Meilleure gestion des photos prises avec l'appareil photo",
            "Improved handling of photos taken with the camera",
            "Melhor gestão das fotos tiradas com a câmara"
        )
        item(
            "Ajout d'options pour gérer les données EXIF avant l'envoi",
            "Added options to manage EXIF data before upload",
            "Adição de opções para gerir os dados EXIF antes do envio"
        )
    }

    section("Interface", "Interface", "Interface") {
        item(
            "Harmonisation de la barre de retour sur plusieurs écrans",
            "Harmonized the back bar across several screens",
            "Uniformização da barra de retorno em vários ecrãs"
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

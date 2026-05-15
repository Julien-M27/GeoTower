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
            "Ajout d'un panneau Données communautaires",
            "Added a Community Data panel",
            "Adição de um painel de Dados comunitários"
        )
        item(
            "Amélioration des tests internes",
            "Improved internal tests",
            "Melhoria dos testes internos"
        )
    }

    section("Données communautaires", "Community data", "Dados comunitários") {
        item(
            "Meilleure gestion des photos et speedtests communautaires",
            "Improved handling of community photos and speedtests",
            "Melhor gestão de fotos e speedtests comunitários"
        )
        item(
            "Choix des sources communautaires par opérateur",
            "Community source selection by operator",
            "Escolha das fontes comunitárias por operadora"
        )
        item(
            "Amélioration de la prise en charge de SignalQuest",
            "Improved SignalQuest support",
            "Melhoria do suporte ao SignalQuest"
        )
    }

    section("Carte", "Map", "Mapa") {
        item(
            "Ajout de la recherche par opérateur",
            "Added operator search",
            "Adição da pesquisa por operadora"
        )
        item(
            "Amélioration de la recherche de lieux",
            "Improved place search",
            "Melhoria da pesquisa de locais"
        )
        item(
            "Meilleure mise en avant de l'opérateur recherché",
            "Better highlighting of the searched operator",
            "Melhor destaque da operadora pesquisada"
        )
    }

    section("Statistiques", "Statistics", "Estatísticas") {
        item(
            "Amélioration des statistiques ville",
            "Improved city statistics",
            "Melhoria das estatísticas da cidade"
        )
        item(
            "Ajout de compteurs plus détaillés par opérateur et fréquence",
            "Added more detailed counters by operator and frequency",
            "Adição de contadores mais detalhados por operadora e frequência"
        )
    }

    section("Onboarding", "Onboarding", "Onboarding") {
        item(
            "Messages plus clairs lorsque les autorisations sont refusées",
            "Clearer messages when permissions are denied",
            "Mensagens mais claras quando as autorizações são recusadas"
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

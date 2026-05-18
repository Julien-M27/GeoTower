package fr.geotower.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.geotower.R

object AnfrDisplayText {
    @Composable
    fun nature(nature: String?): String = translatedValue(
        rawValue = nature,
        emptyValueRes = R.string.anfr_not_specified_feminine,
        resourceByRawValue = natureResources
    )

    @Composable
    fun antennaType(type: String?): String = translatedValue(
        rawValue = type,
        emptyValueRes = R.string.anfr_not_specified_masculine,
        resourceByRawValue = antennaTypeResources
    )

    @Composable
    fun owner(owner: String?): String = translatedValue(
        rawValue = owner,
        emptyValueRes = R.string.anfr_not_specified_masculine,
        resourceByRawValue = ownerResources
    )

    @Composable
    private fun translatedValue(
        rawValue: String?,
        @StringRes emptyValueRes: Int,
        resourceByRawValue: Map<String, Int>
    ): String {
        val cleanValue = rawValue?.trim().orEmpty()
        if (cleanValue.isBlank()) return stringResource(emptyValueRes)
        val resourceId = resourceByRawValue[cleanValue] ?: return cleanValue
        return stringResource(resourceId)
    }

    private val natureResources = mapOf(
        "Sans nature" to R.string.anfr_nature_sans_nature,
        "Sémaphore" to R.string.anfr_nature_semaphore,
        "Phare" to R.string.anfr_nature_phare,
        "Château d'eau - réservoir" to R.string.anfr_nature_chateau_d_eau_reservoir,
        "Immeuble" to R.string.anfr_nature_immeuble,
        "Local technique" to R.string.anfr_nature_local_technique,
        "Mât" to R.string.anfr_nature_mat,
        "Intérieur galerie" to R.string.anfr_nature_interieur_galerie,
        "Intérieur sous-terrain" to R.string.anfr_nature_interieur_sous_terrain,
        "Tunnel" to R.string.anfr_nature_tunnel,
        "Mât béton" to R.string.anfr_nature_mat_beton,
        "Mât métallique" to R.string.anfr_nature_mat_metallique,
        "Pylône" to R.string.anfr_nature_pylone,
        "Bâtiment" to R.string.anfr_nature_batiment,
        "Monument historique" to R.string.anfr_nature_monument_historique,
        "Monument religieux" to R.string.anfr_nature_monument_religieux,
        "Pylône autoportant" to R.string.anfr_nature_pylone_autoportant,
        "Pylône autostable" to R.string.anfr_nature_pylone_autostable,
        "Pylône haubané" to R.string.anfr_nature_pylone_haubane,
        "Pylône treillis" to R.string.anfr_nature_pylone_treillis,
        "Pylône tubulaire" to R.string.anfr_nature_pylone_tubulaire,
        "Silo" to R.string.anfr_nature_silo,
        "Ouvrage d'art (pont, viaduc)" to R.string.anfr_nature_ouvrage_d_art_pont_viaduc,
        "Tour hertzienne" to R.string.anfr_nature_tour_hertzienne,
        "Dalle en béton" to R.string.anfr_nature_dalle_en_beton,
        "Support non décrit" to R.string.anfr_nature_support_non_decrit,
        "Fût" to R.string.anfr_nature_fut,
        "Tour de contrôle" to R.string.anfr_nature_tour_de_controle,
        "Contre-poids au sol" to R.string.anfr_nature_contre_poids_au_sol,
        "Contre-poids sur shelter" to R.string.anfr_nature_contre_poids_sur_shelter,
        "Support DEFENSE" to R.string.anfr_nature_support_defense,
        "pylône arbre" to R.string.anfr_nature_pylone_arbre,
        "Ouvrage de signalisation (portique routier, panneau routier)" to R.string.anfr_nature_ouvrage_de_signalisation_portique_routier_panneau_routier,
        "Balise ou bouée" to R.string.anfr_nature_balise_ou_bouee,
        "XXX" to R.string.anfr_nature_xxx,
        "Eolienne" to R.string.anfr_nature_eolienne,
        "Mobilier urbain" to R.string.anfr_nature_mobilier_urbain
    )

    private val antennaTypeResources = mapOf(
        "Antenne Ran-Sharing" to R.string.anfr_antenna_type_antenne_ran_sharing,
        "Tube" to R.string.anfr_antenna_type_tube,
        "Sans type" to R.string.anfr_antenna_type_sans_type,
        "Accordable" to R.string.anfr_antenna_type_accordable,
        "Active (directionnelle ou omnidirectionnelle)" to R.string.anfr_antenna_type_active_directionnelle_ou_omnidirectionnelle,
        "Cigare" to R.string.anfr_antenna_type_cigare,
        "Corolle" to R.string.anfr_antenna_type_corolle,
        "Dipôle large bande" to R.string.anfr_antenna_type_dipole_large_bande,
        "Dipôle réglable" to R.string.anfr_antenna_type_dipole_reglable,
        "Antenne directive" to R.string.anfr_antenna_type_antenne_directive,
        "Filaire" to R.string.anfr_antenna_type_filaire,
        "Fouet" to R.string.anfr_antenna_type_fouet,
        "Fuseau" to R.string.anfr_antenna_type_fuseau,
        "Réseau linéaire 25 antennes" to R.string.anfr_antenna_type_reseau_lineaire_25_antennes,
        "Groundplane" to R.string.anfr_antenna_type_groundplane,
        "HLO" to R.string.anfr_antenna_type_hlo,
        "Logarithmique/Log périodique" to R.string.anfr_antenna_type_logarithmique_log_periodique,
        "Losange" to R.string.anfr_antenna_type_losange,
        "Panneau" to R.string.anfr_antenna_type_panneau,
        "Antenne parabolique" to R.string.anfr_antenna_type_antenne_parabolique,
        "Cierge/Perche" to R.string.anfr_antenna_type_cierge_perche,
        "Réseaux d'antennes" to R.string.anfr_antenna_type_reseaux_d_antennes,
        "Système antennaire" to R.string.anfr_antenna_type_systeme_antennaire,
        "Yagi" to R.string.anfr_antenna_type_yagi,
        "Réseau linéaire 13 antennes" to R.string.anfr_antenna_type_reseau_lineaire_13_antennes,
        "Antenne à fentes" to R.string.anfr_antenna_type_antenne_a_fentes,
        "Réseau circulaire 49 antennes" to R.string.anfr_antenna_type_reseau_circulaire_49_antennes,
        "Réseau vertical" to R.string.anfr_antenna_type_reseau_vertical,
        "Réseau vertical 2 antennes type P" to R.string.anfr_antenna_type_reseau_vertical_2_antennes_type_p,
        "Réseau vertical 3 antennes type M" to R.string.anfr_antenna_type_reseau_vertical_3_antennes_type_m,
        "Antenne Marguerite" to R.string.anfr_antenna_type_antenne_marguerite,
        "Antenne Parapluie" to R.string.anfr_antenna_type_antenne_parapluie,
        "Antenne Gonio" to R.string.anfr_antenna_type_antenne_gonio,
        "Dipôle/Doublet" to R.string.anfr_antenna_type_dipole_doublet,
        "Trombone" to R.string.anfr_antenna_type_trombone,
        "Colinéaire" to R.string.anfr_antenna_type_colineaire,
        "Antenne Plane LVA" to R.string.anfr_antenna_type_antenne_plane_lva,
        "Dipôle VHF" to R.string.anfr_antenna_type_dipole_vhf,
        "Antenne HF" to R.string.anfr_antenna_type_antenne_hf,
        "Antenne Plane" to R.string.anfr_antenna_type_antenne_plane,
        "Perche DAB" to R.string.anfr_antenna_type_perche_dab,
        "Aérien issu de reprise des données électroniques" to R.string.anfr_antenna_type_aerien_issu_de_reprise_des_donnees_electroniques,
        "Panneau DAB" to R.string.anfr_antenna_type_panneau_dab,
        "Antenne DAB" to R.string.anfr_antenna_type_antenne_dab,
        "Plan passif ou miroir" to R.string.anfr_antenna_type_plan_passif_ou_miroir,
        "Antenne Grille" to R.string.anfr_antenna_type_antenne_grille,
        "Cornet" to R.string.anfr_antenna_type_cornet,
        "Panneau bi-bandes" to R.string.anfr_antenna_type_panneau_bi_bandes,
        "Panneau tri-bandes" to R.string.anfr_antenna_type_panneau_tri_bandes,
        "Cylindre" to R.string.anfr_antenna_type_cylindre,
        "Dièdre" to R.string.anfr_antenna_type_diedre,
        "Globe-Plafonnier" to R.string.anfr_antenna_type_globe_plafonnier,
        "Discone" to R.string.anfr_antenna_type_discone,
        "Antenne dalle" to R.string.anfr_antenna_type_antenne_dalle,
        "Antenne radar" to R.string.anfr_antenna_type_antenne_radar,
        "Obus" to R.string.anfr_antenna_type_obus,
        "Helicoidal" to R.string.anfr_antenna_type_helicoidal,
        "Aérien DEFENSE" to R.string.anfr_antenna_type_aerien_defense,
        "Antenne trisectorielle" to R.string.anfr_antenna_type_antenne_trisectorielle,
        "Antenne indoor pour téléphonie mobile" to R.string.anfr_antenna_type_antenne_indoor_pour_telephonie_mobile,
        "Cable rayonnant (antenne coaxiale)" to R.string.anfr_antenna_type_cable_rayonnant_antenne_coaxiale,
        "Antenne équidirective dans un plan" to R.string.anfr_antenna_type_antenne_equidirective_dans_un_plan,
        "Antenne à rayonnement longitudinal" to R.string.anfr_antenna_type_antenne_a_rayonnement_longitudinal,
        "Antenne à rayonnement zenithal" to R.string.anfr_antenna_type_antenne_a_rayonnement_zenithal,
        "Multi Doublets/Multi dipoles" to R.string.anfr_antenna_type_multi_doublets_multi_dipoles,
        "Antenne à faisceau" to R.string.anfr_antenna_type_antenne_a_faisceau,
        "Antenne à jupe" to R.string.anfr_antenna_type_antenne_a_jupe,
        "Antenne biconique" to R.string.anfr_antenna_type_antenne_biconique,
        "REC-465" to R.string.anfr_antenna_type_rec_465,
        "REC-580" to R.string.anfr_antenna_type_rec_580,
        "AP27" to R.string.anfr_antenna_type_ap27,
        "29-25LOG(FI)" to R.string.anfr_antenna_type_v_29_25log_fi,
        "Pylone Rayonnant" to R.string.anfr_antenna_type_pylone_rayonnant,
        "XXX" to R.string.anfr_antenna_type_xxx,
        "Panneau bi-mode" to R.string.anfr_antenna_type_panneau_bi_mode,
        "Antenne pour la diffusion radio à ondes courtes ALLOUIS ISSOUDUN" to R.string.anfr_antenna_type_antenne_pour_la_diffusion_radio_a_ondes_courtes_allouis_issoudun,
        "Tout en 1(panneau-faisceau orientable)" to R.string.anfr_antenna_type_tout_en_1_panneau_faisceau_orientable,
        "Antenne à faisceaux orientables" to R.string.anfr_antenna_type_antenne_a_faisceaux_orientables,
        "Antenne cadre" to R.string.anfr_antenna_type_antenne_cadre,
        "Antenne BYT" to R.string.anfr_antenna_type_antenne_byt,
        "Antenne SFR" to R.string.anfr_antenna_type_antenne_sfr
    )

    private val ownerResources = mapOf(
        "Particulier" to R.string.anfr_owner_particulier,
        "Copropriété, Syndic, SCI" to R.string.anfr_owner_copropriete_syndic_sci,
        "Commune, communauté de commune" to R.string.anfr_owner_commune_communaute_de_commune,
        "Conseil Départemental" to R.string.anfr_owner_conseil_departemental,
        "Conseil Régional" to R.string.anfr_owner_conseil_regional,
        "Société Privée" to R.string.anfr_owner_societe_privee,
        "Etablissement de soins" to R.string.anfr_owner_etablissement_de_soins,
        "Etat Ministère" to R.string.anfr_owner_etat_ministere,
        "Aviation Civile" to R.string.anfr_owner_aviation_civile
    )
}

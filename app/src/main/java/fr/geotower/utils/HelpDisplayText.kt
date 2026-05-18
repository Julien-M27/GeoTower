package fr.geotower.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.geotower.R

object HelpDisplayText {
    @Composable fun visualTitle(id: String): String = stringResourceOrRaw(id, visualTitleResources)
    @Composable fun visualLabel(id: String, index: Int): String = stringResourceOrRaw("$id:$index", visualLabelResources)
    @Composable fun topicTitle(id: String): String = stringResourceOrRaw(id, topicTitleResources)
    @Composable fun topicSubtitle(id: String): String = stringResourceOrRaw(id, topicSubtitleResources)
    @Composable fun sectionTitle(id: String): String = stringResourceOrRaw(id, sectionTitleResources)
    @Composable fun sectionBody(id: String): String = stringResourceOrRaw(id, sectionBodyResources)

    @Composable
    fun actionTitle(id: String): String = when (id) {
        "home_nearby" -> stringResource(R.string.appstrings_near_antennas)
        "home_map" -> stringResource(R.string.appstrings_map_title)
        "home_compass" -> stringResource(R.string.appstrings_compass_title)
        "home_settings" -> stringResource(R.string.appstrings_settings_title)
        "home_about" -> stringResource(R.string.appstrings_about)
        "home_help" -> stringResource(R.string.appstrings_help_title)
        else -> stringResourceOrRaw(id, actionTitleResources)
    }

    @Composable fun actionDescription(id: String): String = stringResourceOrRaw(id, actionDescResources)

    @Composable
    private fun stringResourceOrRaw(rawValue: String, resources: Map<String, Int>): String {
        val resId = resources[rawValue] ?: return rawValue
        return stringResource(resId)
    }

    private val visualTitleResources = mapOf(
        "home" to R.string.help_visual_title_home,
        "nearby" to R.string.help_visual_title_nearby,
        "map" to R.string.help_visual_title_map,
        "support" to R.string.help_visual_title_support,
        "site" to R.string.help_visual_title_site,
        "elevation" to R.string.help_visual_title_elevation,
        "throughput" to R.string.help_visual_title_throughput,
        "photos" to R.string.help_visual_title_photos,
        "share" to R.string.help_visual_title_share,
        "settings" to R.string.help_visual_title_settings,
        "data" to R.string.help_visual_title_data,
        "about" to R.string.help_visual_title_about
    )

    private val visualLabelResources = mapOf(
        "home:1" to R.string.help_visual_label_home_1,
        "home:2" to R.string.help_visual_label_home_2,
        "home:3" to R.string.help_visual_label_home_3,
        "nearby:1" to R.string.help_visual_label_nearby_1,
        "nearby:2" to R.string.help_visual_label_nearby_2,
        "nearby:3" to R.string.help_visual_label_nearby_3,
        "map:1" to R.string.help_visual_label_map_1,
        "map:2" to R.string.help_visual_label_map_2,
        "map:3" to R.string.help_visual_label_map_3,
        "support:1" to R.string.help_visual_label_support_1,
        "support:2" to R.string.help_visual_label_support_2,
        "support:3" to R.string.help_visual_label_support_3,
        "site:1" to R.string.help_visual_label_site_1,
        "site:2" to R.string.help_visual_label_site_2,
        "site:3" to R.string.help_visual_label_site_3,
        "elevation:1" to R.string.help_visual_label_elevation_1,
        "elevation:2" to R.string.help_visual_label_elevation_2,
        "elevation:3" to R.string.help_visual_label_elevation_3,
        "throughput:1" to R.string.help_visual_label_throughput_1,
        "throughput:2" to R.string.help_visual_label_throughput_2,
        "throughput:3" to R.string.help_visual_label_throughput_3,
        "photos:1" to R.string.help_visual_label_photos_1,
        "photos:2" to R.string.help_visual_label_photos_2,
        "photos:3" to R.string.help_visual_label_photos_3,
        "share:1" to R.string.help_visual_label_share_1,
        "share:2" to R.string.help_visual_label_share_2,
        "share:3" to R.string.help_visual_label_share_3,
        "settings:1" to R.string.help_visual_label_settings_1,
        "settings:2" to R.string.help_visual_label_settings_2,
        "settings:3" to R.string.help_visual_label_settings_3,
        "data:1" to R.string.help_visual_label_data_1,
        "data:2" to R.string.help_visual_label_data_2,
        "data:3" to R.string.help_visual_label_data_3,
        "about:1" to R.string.help_visual_label_about_1,
        "about:2" to R.string.help_visual_label_about_2,
        "about:3" to R.string.help_visual_label_about_3
    )

    private val topicTitleResources = mapOf(
        "start" to R.string.help_topic_title_start,
        "home" to R.string.help_topic_title_home,
        "nearby" to R.string.help_topic_title_nearby,
        "map" to R.string.help_topic_title_map,
        "compass" to R.string.help_topic_title_compass,
        "support" to R.string.help_topic_title_support,
        "site" to R.string.help_topic_title_site,
        "elevation" to R.string.help_topic_title_elevation,
        "throughput" to R.string.help_topic_title_throughput,
        "photos" to R.string.help_topic_title_photos,
        "share" to R.string.help_topic_title_share,
        "settings" to R.string.help_topic_title_settings,
        "data" to R.string.help_topic_title_data,
        "about" to R.string.help_topic_title_about,
        "glossary" to R.string.help_topic_title_glossary
    )

    private val topicSubtitleResources = mapOf(
        "start" to R.string.help_topic_subtitle_start,
        "home" to R.string.help_topic_subtitle_home,
        "nearby" to R.string.help_topic_subtitle_nearby,
        "map" to R.string.help_topic_subtitle_map,
        "compass" to R.string.help_topic_subtitle_compass,
        "support" to R.string.help_topic_subtitle_support,
        "site" to R.string.help_topic_subtitle_site,
        "elevation" to R.string.help_topic_subtitle_elevation,
        "throughput" to R.string.help_topic_subtitle_throughput,
        "photos" to R.string.help_topic_subtitle_photos,
        "share" to R.string.help_topic_subtitle_share,
        "settings" to R.string.help_topic_subtitle_settings,
        "data" to R.string.help_topic_subtitle_data,
        "about" to R.string.help_topic_subtitle_about,
        "glossary" to R.string.help_topic_subtitle_glossary
    )

    private val sectionTitleResources = mapOf(
        "start_prepare" to R.string.help_section_title_start_prepare,
        "start_search" to R.string.help_section_title_start_search,
        "start_cards" to R.string.help_section_title_start_cards,
        "home_buttons" to R.string.help_section_title_home_buttons,
        "home_banners" to R.string.help_section_title_home_banners,
        "nearby_search" to R.string.help_section_title_nearby_search,
        "nearby_codes" to R.string.help_section_title_nearby_codes,
        "nearby_list" to R.string.help_section_title_nearby_list,
        "map_controls" to R.string.help_section_title_map_controls,
        "map_filters" to R.string.help_section_title_map_filters,
        "map_offline" to R.string.help_section_title_map_offline,
        "compass_use" to R.string.help_section_title_compass_use,
        "buttons" to R.string.help_section_title_buttons,
        "support_understand" to R.string.help_section_title_support_understand,
        "actions" to R.string.help_section_title_actions,
        "site_info" to R.string.help_section_title_site_info,
        "site_tools" to R.string.help_section_title_site_tools,
        "elevation_use" to R.string.help_section_title_elevation_use,
        "throughput_assumptions" to R.string.help_section_title_throughput_assumptions,
        "throughput_controls" to R.string.help_section_title_throughput_controls,
        "photos_view" to R.string.help_section_title_photos_view,
        "photos_upload" to R.string.help_section_title_photos_upload,
        "share_create" to R.string.help_section_title_share_create,
        "share_options" to R.string.help_section_title_share_options,
        "settings_general" to R.string.help_section_title_settings_general,
        "settings_split" to R.string.help_section_title_settings_split,
        "settings_pages" to R.string.help_section_title_settings_pages,
        "data_database" to R.string.help_section_title_data_database,
        "data_maps" to R.string.help_section_title_data_maps,
        "about_sections" to R.string.help_section_title_about_sections,
        "about_loops" to R.string.help_section_title_about_loops,
        "glossary_icons" to R.string.help_section_title_glossary_icons,
        "glossary_issues" to R.string.help_section_title_glossary_issues
    )

    private val sectionBodyResources = mapOf(
        "start_prepare" to R.string.help_section_body_start_prepare,
        "start_search" to R.string.help_section_body_start_search,
        "start_cards" to R.string.help_section_body_start_cards,
        "home_buttons" to R.string.help_section_body_home_buttons,
        "home_banners" to R.string.help_section_body_home_banners,
        "nearby_search" to R.string.help_section_body_nearby_search,
        "nearby_codes" to R.string.help_section_body_nearby_codes,
        "nearby_list" to R.string.help_section_body_nearby_list,
        "map_controls" to R.string.help_section_body_map_controls,
        "map_filters" to R.string.help_section_body_map_filters,
        "map_offline" to R.string.help_section_body_map_offline,
        "compass_use" to R.string.help_section_body_compass_use,
        "buttons" to R.string.help_section_body_buttons,
        "support_understand" to R.string.help_section_body_support_understand,
        "actions" to R.string.help_section_body_actions,
        "site_info" to R.string.help_section_body_site_info,
        "site_tools" to R.string.help_section_body_site_tools,
        "elevation_use" to R.string.help_section_body_elevation_use,
        "throughput_assumptions" to R.string.help_section_body_throughput_assumptions,
        "throughput_controls" to R.string.help_section_body_throughput_controls,
        "photos_view" to R.string.help_section_body_photos_view,
        "photos_upload" to R.string.help_section_body_photos_upload,
        "share_create" to R.string.help_section_body_share_create,
        "share_options" to R.string.help_section_body_share_options,
        "settings_general" to R.string.help_section_body_settings_general,
        "settings_split" to R.string.help_section_body_settings_split,
        "settings_pages" to R.string.help_section_body_settings_pages,
        "data_database" to R.string.help_section_body_data_database,
        "data_maps" to R.string.help_section_body_data_maps,
        "about_sections" to R.string.help_section_body_about_sections,
        "about_loops" to R.string.help_section_body_about_loops,
        "glossary_icons" to R.string.help_section_body_glossary_icons,
        "glossary_issues" to R.string.help_section_body_glossary_issues
    )

    private val actionTitleResources = mapOf(
        "search_field" to R.string.help_action_title_search_field,
        "clear" to R.string.help_action_title_clear,
        "quick_suggestions" to R.string.help_action_title_quick_suggestions,
        "info" to R.string.help_action_title_info,
        "load_more" to R.string.help_action_title_load_more,
        "expand_area" to R.string.help_action_title_expand_area,
        "location" to R.string.help_action_title_location,
        "zoom" to R.string.help_action_title_zoom,
        "map_compass" to R.string.help_action_title_map_compass,
        "scale" to R.string.help_action_title_scale,
        "live_search" to R.string.help_action_title_live_search,
        "quit" to R.string.help_action_title_quit,
        "open_map" to R.string.help_action_title_open_map,
        "navigate" to R.string.help_action_title_navigate,
        "share" to R.string.help_action_title_share,
        "photos" to R.string.help_action_title_photos,
        "operator_site" to R.string.help_action_title_operator_site,
        "elevation_profile" to R.string.help_action_title_elevation_profile,
        "throughput" to R.string.help_action_title_throughput,
        "settings_gear" to R.string.help_action_title_settings_gear,
        "recalculate" to R.string.help_action_title_recalculate,
        "calculate_later" to R.string.help_action_title_calculate_later,
        "custom" to R.string.help_action_title_custom,
        "optimal_distance" to R.string.help_action_title_optimal_distance,
        "mini_map" to R.string.help_action_title_mini_map,
        "back_arrow" to R.string.help_action_title_back_arrow,
        "magnifier" to R.string.help_action_title_magnifier,
        "x_icon" to R.string.help_action_title_x_icon,
        "gear" to R.string.help_action_title_gear,
        "share_icon" to R.string.help_action_title_share_icon,
        "download" to R.string.help_action_title_download,
        "refresh" to R.string.help_action_title_refresh
    )

    private val actionDescResources = mapOf(
        "home_nearby" to R.string.help_action_desc_home_nearby,
        "home_map" to R.string.help_action_desc_home_map,
        "home_compass" to R.string.help_action_desc_home_compass,
        "home_settings" to R.string.help_action_desc_home_settings,
        "home_about" to R.string.help_action_desc_home_about,
        "home_help" to R.string.help_action_desc_home_help,
        "search_field" to R.string.help_action_desc_search_field,
        "clear" to R.string.help_action_desc_clear,
        "quick_suggestions" to R.string.help_action_desc_quick_suggestions,
        "info" to R.string.help_action_desc_info,
        "load_more" to R.string.help_action_desc_load_more,
        "expand_area" to R.string.help_action_desc_expand_area,
        "location" to R.string.help_action_desc_location,
        "zoom" to R.string.help_action_desc_zoom,
        "map_compass" to R.string.help_action_desc_map_compass,
        "scale" to R.string.help_action_desc_scale,
        "live_search" to R.string.help_action_desc_live_search,
        "quit" to R.string.help_action_desc_quit,
        "open_map" to R.string.help_action_desc_open_map,
        "navigate" to R.string.help_action_desc_navigate,
        "share" to R.string.help_action_desc_share,
        "photos" to R.string.help_action_desc_photos,
        "operator_site" to R.string.help_action_desc_operator_site,
        "elevation_profile" to R.string.help_action_desc_elevation_profile,
        "throughput" to R.string.help_action_desc_throughput,
        "settings_gear" to R.string.help_action_desc_settings_gear,
        "recalculate" to R.string.help_action_desc_recalculate,
        "calculate_later" to R.string.help_action_desc_calculate_later,
        "custom" to R.string.help_action_desc_custom,
        "optimal_distance" to R.string.help_action_desc_optimal_distance,
        "mini_map" to R.string.help_action_desc_mini_map,
        "back_arrow" to R.string.help_action_desc_back_arrow,
        "magnifier" to R.string.help_action_desc_magnifier,
        "x_icon" to R.string.help_action_desc_x_icon,
        "gear" to R.string.help_action_desc_gear,
        "share_icon" to R.string.help_action_desc_share_icon,
        "download" to R.string.help_action_desc_download,
        "refresh" to R.string.help_action_desc_refresh
    )

}

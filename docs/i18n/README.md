# GeoTower i18n

Android uses `app/src/main/res/values-*` as the runtime source of truth.

This folder keeps a language-by-language working view for translators:

- `en/strings.xml`
- `en/plurals.xml`
- `fr/strings.xml`
- `fr/plurals.xml`
- `pt/strings.xml`
- `pt/plurals.xml`
- `it/strings.xml`
- `it/plurals.xml`
- `de/strings.xml`
- `de/plurals.xml`
- `es/strings.xml`
- `es/plurals.xml`

When a translation is validated here, mirror it into the matching Android resource directory.

## Migration order

1. Keep common labels and language selection in Android resources first.
2. Move settings and onboarding strings screen by screen.
3. Move navigation, home, map, nearby sites, compass and statistics.
4. Move support/site detail screens and sharing flows.
5. Move background strings for workers, notifications, widgets and Android Auto.
6. Replace repeated count strings with `plurals.xml`.
7. Keep only formatting helpers and non-user-facing constants outside resources.

`AndroidI18nResourcesTest` checks that every localized Android `strings.xml` has the same translatable keys and placeholders as the default resource file.

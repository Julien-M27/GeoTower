# Correction du test DSS `RadioThroughputEngineTest`

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique pourquoi le test unitaire DSS echoue et comment le corriger proprement.

## Objectif

Faire repasser les tests unitaires, en particulier :

```text
RadioThroughputEngineTest > dssPolicyDoesNotDoubleCountSameFddBand
```

Le test doit verifier la vraie regle metier : une meme bande FDD partagee entre 4G et 5G ne doit pas etre additionnee deux fois.

## Probleme actuel

Etat observe :

- `:app:compileDebugKotlin` passe.
- `:app:testDebugUnitTest` echoue avec 1 test en erreur.
- L'echec est dans `app/src/test/java/fr/geotower/radio/RadioThroughputEngineTest.kt`, ligne 90.
- Le test cherche :

```kotlin
assertTrue(result.warnings.any { it.contains("partagee", ignoreCase = true) })
```

- Le code produit un warning utilisateur avec accent :

```kotlin
"Bande potentiellement partagée entre la 4G et la 5G : ..."
```

Donc le test cherche `partagee` sans accent, alors que le texte contient `partagée` avec accent.

## Diagnostic

Ce n'est probablement pas un bug de calcul radio. Les assertions principales passent avant :

- 2 carriers sont produits ;
- 1 seul carrier est inclus ;
- au moins 1 carrier est exclu.

Le test echoue seulement sur une assertion de texte utilisateur. Il est trop fragile car il depend de l'accentuation exacte d'une phrase francaise.

## Correction minimale acceptable

Dans `RadioThroughputEngineTest.kt`, normaliser les accents avant l'assertion.

Exemple :

```kotlin
private fun String.withoutAccents(): String {
    return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
}
```

Puis :

```kotlin
assertTrue(
    result.warnings.any {
        it.withoutAccents().contains("partagee", ignoreCase = true)
    }
)
```

Avantage :

- changement tres limite ;
- le test accepte `partagée` et `partagee` ;
- pas de changement de comportement app.

## Correction recommandee

Ne pas tester une phrase utilisateur complete pour verifier une regle metier.

Preferer tester les donnees structurees :

- `included.size == 1` ;
- `excludedCarriers` n'est pas vide ;
- le carrier exclu correspond a la bande attendue ;
- le carrier exclu correspond a la technologie attendue ;
- `excludedReason` ou `warnings` est non vide, sans imposer une orthographe exacte.

Si le moteur doit exposer des raisons stables, ajouter une representation structuree, par exemple :

```kotlin
enum class ThroughputWarningCode {
    DSS_SHARED_FDD_BAND
}
```

Puis le test peut verifier le code `DSS_SHARED_FDD_BAND`, tandis que l'UI garde une phrase traduite/accentuee librement.

Cette approche est plus robuste a long terme, surtout si les textes passent par `AppStrings` ou sont traduits.

## Fichiers a lire

Lire au minimum :

- `app/src/test/java/fr/geotower/radio/RadioThroughputEngineTest.kt`
- `app/src/main/java/fr/geotower/radio/RadioThroughputEngine.kt`
- `app/src/main/java/fr/geotower/radio/RadioModels.kt`
- `app/src/main/java/fr/geotower/utils/AppStrings.kt`, si les warnings sont affiches/traduits cote UI

## Criteres d'acceptation

La correction est terminee seulement si :

- `:app:testDebugUnitTest` passe ;
- le test DSS continue de verifier qu'une bande FDD 4G/5G partagee n'est pas double-comptee ;
- le test ne depend plus d'une orthographe accentuee fragile ;
- aucune phrase utilisateur n'est degradee pour satisfaire le test ;
- `RadioThroughputEngine.kt` garde un texte lisible en francais si ce texte reste dans le moteur.

## Commandes de verification

Depuis la racine du projet :

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugKotlin
```

Si le sandbox bloque le verrou Gradle dans `C:\Users\Julien\.gradle`, relancer les tests hors sandbox avec l'autorisation utilisateur.

## A ne pas faire

- Ne pas remplacer `partagée` par un texte degrade uniquement pour faire passer le test.
- Ne pas supprimer l'assertion DSS importante.
- Ne pas ignorer le test.
- Ne pas rendre le test trop large, par exemple en acceptant n'importe quel warning.
- Ne pas melanger cette correction avec les chantiers securite.

## Prompt court pour l'IA qui implementera

Tu dois corriger l'echec du test `RadioThroughputEngineTest.dssPolicyDoesNotDoubleCountSameFddBand`. Lis `RadioThroughputEngineTest.kt`, `RadioThroughputEngine.kt` et `RadioModels.kt`. Le test echoue car il cherche `partagee` sans accent alors que le warning produit contient `partagée`. Ne degrade pas le texte utilisateur pour satisfaire le test. Corrige le test en normalisant les accents ou, mieux, en assertant la regle metier via les donnees structurees : un seul carrier inclus, un carrier exclu, bande attendue exclue, et raison/warning present. Termine en lancant `:app:testDebugUnitTest`.

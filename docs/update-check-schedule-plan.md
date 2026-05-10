# Planification des verifications de mise a jour a 20h

Ce document est un cahier des charges pour une IA/code agent. Il ne modifie pas l'application : il explique le probleme actuel et decrit comment changer la verification des mises a jour de base pour qu'elle se fasse une fois par jour vers 20h.

## Objectif

La verification automatique de nouvelle base de donnees doit etre faite une fois par jour, autour de 20h, heure locale de l'appareil.

L'utilisateur doit continuer a pouvoir activer/desactiver les notifications de mise a jour depuis les parametres. Si l'option est desactivee, aucun appel reseau utile ne doit etre fait pour cette fonctionnalite.

## Probleme actuel

Etat observe :

- `MainActivity.kt` programme `UpdateCheckWorker`.
- Le commentaire dit une verification tous les 3 jours.
- Le code programme en realite une periodicite de 30 minutes.
- La policy actuelle est `ExistingPeriodicWorkPolicy.KEEP`.

Effets :

- l'app peut interroger le serveur beaucoup trop souvent ;
- la batterie et le reseau sont utilises inutilement ;
- le serveur recoit plus de trafic que necessaire ;
- si une ancienne version de l'app a deja programme le work periodique, `KEEP` peut conserver l'ancienne planification au lieu d'appliquer la nouvelle.

## Comportement cible

La verification doit etre faite :

- une fois par jour ;
- vers 20h ;
- seulement si les notifications de mise a jour sont activees ;
- sans notifier deux fois la meme version ;
- avec une contrainte reseau si possible ;
- sans lancer automatiquement le telechargement tant que l'utilisateur n'a pas clique ou confirme l'action prevue par l'app.

Note importante : WorkManager n'est pas une alarme exacte. Android peut retarder l'execution a cause de Doze, de l'economie de batterie ou de contraintes systeme. L'objectif realiste est donc "autour de 20h", pas "20:00:00 garanti".

## Strategie recommandee

Pour viser une heure murale comme 20h, preferer un `OneTimeWorkRequest` reprogramme chaque jour plutot qu'un `PeriodicWorkRequest` de 24h.

Pourquoi :

- un periodique de 24h peut deriver si Android retarde une execution ;
- un one-shot peut recalculer le prochain 20h apres chaque execution ;
- cela garde mieux l'intention produit : "chaque jour a 20h".

Architecture conseillee :

1. Creer un petit scheduler dedie, par exemple `UpdateCheckScheduler`.
2. Ce scheduler calcule le delai jusqu'au prochain 20h local.
3. Il programme un `OneTimeWorkRequest<UpdateCheckWorker>`.
4. `UpdateCheckWorker`, quand il termine correctement, demande au scheduler de programmer le prochain passage.
5. Au demarrage de l'app et quand le parametre change, le scheduler remet la planification dans l'etat attendu.

## Fichiers a lire avant modification

Lire au minimum :

- `app/src/main/java/fr/geotower/MainActivity.kt`
- `app/src/main/java/fr/geotower/data/workers/UpdateCheckWorker.kt`
- `app/src/main/java/fr/geotower/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/fr/geotower/data/api/DatabaseDownloader.kt`
- `app/src/main/java/fr/geotower/utils/AppConfig.kt`

## Changements a effectuer

### 1. Retirer la planification 30 minutes

Dans `MainActivity.kt` :

- retirer le `PeriodicWorkRequestBuilder<UpdateCheckWorker>(30, TimeUnit.MINUTES)`;
- supprimer ou corriger le commentaire qui parle de 3 jours ;
- ne pas conserver `ExistingPeriodicWorkPolicy.KEEP` pour cette ancienne planification.

Pour les utilisateurs qui ont deja une ancienne planification :

- annuler explicitement le work unique `"PeriodicUpdateCheck"` une fois la nouvelle strategie en place ;
- eviter qu'un ancien worker 30 minutes continue a tourner apres mise a jour de l'app.

### 2. Creer un scheduler dedie

Ajouter un objet ou une classe, par exemple :

- `fr.geotower.data.workers.UpdateCheckScheduler`

Responsabilites :

- calculer le prochain 20h en heure locale ;
- programmer un `OneTimeWorkRequest`;
- annuler la verification si l'option utilisateur est desactivee ;
- eviter les doublons avec un nom unique stable, par exemple `"DailyUpdateCheckAt20"`;
- ajouter une contrainte `NetworkType.CONNECTED`.

Pseudo-logique :

```kotlin
val now = ZonedDateTime.now(ZoneId.systemDefault())
var next = now.toLocalDate().atTime(20, 0).atZone(ZoneId.systemDefault())
if (!next.isAfter(now)) {
    next = next.plusDays(1)
}
val delayMillis = Duration.between(now, next).toMillis()
```

Puis creer un `OneTimeWorkRequest` avec ce delai.

### 3. Mettre a jour `UpdateCheckWorker`

Dans `UpdateCheckWorker.kt` :

- garder la verification `enable_update_notifications`;
- garder la logique `last_notified_db_version` pour ne pas spammer la meme version ;
- en cas de succes normal, programmer le prochain passage quotidien ;
- en cas d'erreur temporaire, laisser WorkManager appliquer `Result.retry()` plutot que programmer un deuxieme work en parallele ;
- ne pas logger de donnees sensibles.

Attention : si l'utilisateur a desactive l'option, le worker peut retourner `Result.success()`, mais le scheduler ne doit pas reprogrammer inutilement une verification active. Le plus simple est de faire porter cette decision par `UpdateCheckScheduler`.

### 4. Mettre a jour les parametres

Dans `SettingsScreen.kt`, quand l'utilisateur active/desactive les notifications :

- si active : appeler le scheduler pour programmer la prochaine verification a 20h ;
- si desactive : annuler le work unique `"DailyUpdateCheckAt20"` ;
- garder la preference `enable_update_notifications`.

Cela evite d'attendre le prochain lancement de l'app pour appliquer le choix utilisateur.

### 5. Au demarrage de l'app

Dans `MainActivity.kt` :

- appeler le scheduler au demarrage pour garantir que la planification existe si l'option est activee ;
- annuler l'ancien work unique `"PeriodicUpdateCheck"` pour migrer les installations existantes ;
- ne pas programmer un nouveau worker periodique.

## Alternative acceptable mais moins precise

Si l'IA veut faire plus simple, elle peut utiliser :

- `PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)`;
- `setInitialDelay(delayUntilNext20h)`;
- `ExistingPeriodicWorkPolicy.UPDATE`.

Mais cette solution peut deriver avec le temps, car le prochain passage depend de l'execution precedente. Elle est acceptable si "vers 20h" suffit, mais la strategie one-shot reprogrammee est plus fidele.

## Criteres d'acceptation

La correction est terminee seulement si :

- il n'existe plus de planification toutes les 30 minutes pour `UpdateCheckWorker`;
- `rg "PeriodicUpdateCheck|30, java.util.concurrent.TimeUnit.MINUTES|UpdateCheckWorker" app/src/main/java` confirme que l'ancienne logique est retiree ou annulee explicitement ;
- la prochaine verification est calculee pour 20h heure locale ;
- le work est unique et ne se duplique pas a chaque lancement ;
- l'option utilisateur active/desactive bien la planification ;
- la meme version de base ne genere pas plusieurs notifications ;
- une absence reseau ne provoque pas une boucle agressive ;
- `:app:compileDebugKotlin` passe ;
- les tests existants passent, ou les echecs restants sont documentes comme non lies.

## Commandes de verification suggerees

Depuis la racine du projet Android :

```powershell
rg "UpdateCheckWorker|PeriodicUpdateCheck|DailyUpdateCheckAt20|PeriodicWorkRequestBuilder" app/src/main/java
rg "30, java.util.concurrent.TimeUnit.MINUTES|30, TimeUnit.MINUTES" app/src/main/java
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

## A ne pas faire

- Ne pas laisser l'ancien worker 30 minutes actif.
- Ne pas utiliser `KEEP` si cela empeche les installations existantes de recevoir la nouvelle planification.
- Ne pas promettre une execution exacte a 20h avec WorkManager.
- Ne pas faire un appel reseau quotidien si l'utilisateur a desactive les notifications.
- Ne pas notifier plusieurs fois pour la meme version de base.
- Ne pas lancer automatiquement le telechargement complet sans action utilisateur claire.

## Prompt court pour l'IA qui implementera

Tu dois remplacer la verification periodique des mises a jour de base GeoTower actuellement programmee depuis `MainActivity.kt` toutes les 30 minutes. Le nouveau comportement voulu est une verification une fois par jour autour de 20h, heure locale de l'appareil. Lis `MainActivity.kt`, `UpdateCheckWorker.kt`, `SettingsScreen.kt`, `DatabaseDownloader.kt` et `AppConfig.kt`. Supprime la planification 30 minutes, migre les installations existantes en annulant l'ancien work unique `"PeriodicUpdateCheck"`, puis ajoute une planification quotidienne robuste. Strategie preferee : un `OneTimeWorkRequest` unique, contraint au reseau, programme pour le prochain 20h et reprogramme apres succes via un scheduler dedie. Respecte la preference `enable_update_notifications`, conserve la logique anti-spam `last_notified_db_version`, et verifie avec `rg`, compilation Kotlin et tests unitaires.

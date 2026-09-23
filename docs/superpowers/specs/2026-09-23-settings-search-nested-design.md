# Recherche approfondie des paramètres — conception

**Date :** 2026-09-23

**État :** conception approuvée dans la conversation

## Objectif

Permettre à la recherche des paramètres de trouver les réglages placés dans les feuilles et sous-feuilles ouvertes depuis l’écran Paramètres, puis d’ouvrir directement l’écran contenant le réglage recherché.

La recherche actuelle indexe principalement les réglages de premier niveau dans `SettingsScreen.kt`. Les sous-écrans, notamment les options de photos communautaires atteintes depuis la personnalisation des pages, restent difficiles à trouver sans parcourir leur hiérarchie.

## Expérience attendue

- Les résultats comprennent les réglages de premier niveau déjà indexés et les réglages disponibles dans les feuilles et sous-feuilles des paramètres.
- Chaque résultat profond affiche son libellé et son chemin, par exemple « Personnalisation des pages › Fiche site › Photos communautaires ».
- Sélectionner un résultat ferme la recherche et ouvre directement la feuille ou sous-feuille qui contient le réglage. Les feuilles longues se positionnent sur l’option correspondante.
- Sélectionner un résultat ne modifie aucune préférence.
- La recherche garde sa normalisation sans accents et accepte des synonymes utiles, dont les termes français et anglais déjà employés par le catalogue.

## Portée

Le catalogue couvre les réglages configurables dans les feuilles ouvertes depuis Paramètres, y compris leurs sous-feuilles : apparence, préférences, partage, filtres cartographiques, personnalisation des pages, réglages de pages, données communautaires et autres feuilles actuellement accessibles depuis cet écran.

Les résultats suivent les mêmes conditions d’accès que les écrans : drapeaux de fonctionnalités, disponibilité d’écran et mode simplifié. Les choix d’un sélecteur ne deviennent pas des résultats autonomes ; leurs noms peuvent servir de mots-clés pour retrouver le réglage qui les contient.

Les opérateurs et sources de données communautaires sont tirés des modèles locaux existants. Les sources désactivées par les drapeaux de fonctionnalités ne sont pas proposées. Les entrées photo couvrent les réglages de visibilité par opérateur, les sources, leur ordre et leur mode de repli, ainsi que le masquage des doublons visuels.

## Architecture

Le catalogue reste centralisé dans le flux de recherche de `SettingsScreen.kt`, où l’index actuel peut déjà accéder aux états et callbacks qui ouvrent les feuilles. Chaque entrée fournit :

- un libellé de réglage ;
- des mots-clés ;
- un chemin hiérarchique pour le résultat ;
- une destination explicite vers la feuille, sous-feuille et option ciblées.

Les routes sont déclarées de manière stable et exécutées par le routeur central de recherche. Les composants de feuille reçoivent, au besoin, un identifiant d’option initiale afin de défiler jusqu’à cette option. Le rendu des résultats continue d’appliquer la normalisation et la correspondance de tous les tokens de la requête.

Les libellés visibles réutilisent les ressources et noms de sources déjà affichés par les feuilles. Les chemins réutilisent les libellés des sections et sous-écrans concernés. Les listes variables sont construites depuis les données locales et les drapeaux disponibles au moment où l’index est construit.

## Navigation et état

Le clic sur un résultat efface la requête avant d’activer sa destination. Une destination ouvre la feuille exacte plutôt que de laisser l’utilisateur rechercher à nouveau dans la feuille parente. Pour les feuilles longues, un ancrage ou mécanisme de mise en évidence déjà présent est réutilisé lorsque c’est possible ; les composants sans ciblage devront recevoir le minimum d’état nécessaire pour défiler vers l’option.

Les retours de sous-feuilles continuent à utiliser les callbacks existants. Si une destination ne peut plus être affichée à cause d’un drapeau ou d’une condition d’accès, elle est omise du catalogue plutôt que de produire un résultat inopérant.

## Critères d’acceptation

1. Une recherche sur le nom d’un réglage disponible dans une feuille imbriquée renvoie ce réglage avec son chemin.
2. Choisir un résultat ouvre la feuille correspondante et amène à l’option, sans changer sa valeur.
3. La recherche de réglages de photos communautaires trouve les options visibles par opérateur et par source, l’ordre et le repli des sources, ainsi que le masquage des doublons.
4. Les entrées relatives aux options indisponibles ou masquées par les drapeaux de fonctionnalités ne sont pas proposées.
5. Les résultats principaux existants conservent leurs destinations et leur comportement.
6. La recherche reste insensible aux accents et accepte les synonymes déclarés.

## Vérification

La validation comprend une compilation de l’application et un parcours manuel des résultats représentatifs : un réglage direct, un réglage dans une sous-feuille, une option de photo communautaire dynamique, un écran masqué par un drapeau et un accès en mode simplifié. Aucun changement de préférence ne doit se produire lors de l’ouverture d’un résultat.

## Hors périmètre

La recherche concerne les réglages accessibles depuis l’écran Paramètres. Elle ne devient pas une recherche dans les enregistrements, photos de sites ou valeurs de données affichées par d’autres écrans.

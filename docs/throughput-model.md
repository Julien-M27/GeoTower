# Modele de debit radio theorique

Version de calcul actuelle : `FR_RADIO_THROUGHPUT_V1`.

Le calculateur utilise un moteur pur dans `fr.geotower.radio`. L'ecran Compose convertit les frequences ANFR du site en `SiteRadioSystem`, puis le moteur applique les allocations operateur FR metropole et un profil radio explicite.

## Entrees

- Operateur : Orange, SFR, Bouygues Telecom ou Free Mobile.
- Systeme radio : technologie 4G LTE ou 5G NR, bande ANFR, statut, azimut et hauteur quand disponibles.
- Profil : `Prudent`, `Standard`, `Ideal` ou `Personnalise`.
- Politique DSS : `DO_NOT_DOUBLE_COUNT`, pour eviter d'additionner deux fois une meme bande FDD partagee 4G/5G.

## 5G NR

La 5G utilise une approximation issue de la formule de debit maximal 3GPP TS 38.306 :

```text
rate = 1e-6 * layers * Qm * f * Rmax * ((N_PRB * 12) / T_s_mu) * (1 - OH) * ratio_TDD
```

Pour `FR_RADIO_THROUGHPUT_V1`, le moteur embarque une table PRB FR1 SCS 30 kHz pour les canaux 5 a 100 MHz. Les largeurs non entieres issues des blocs de spectre francais, par exemple 14,8 MHz en 2100 MHz, sont rapprochees de la largeur PRB standard la plus proche.

## 4G LTE

La 4G utilise `LTE_APPROX_V1`, un modele conservateur base sur une reference 20 MHz :

- DL 20 MHz 2x2 64-QAM : 150 Mbit/s.
- UL 20 MHz 1 couche 64-QAM : 75 Mbit/s.
- Le resultat est module par largeur de bande, ordre de modulation et couches MIMO.

Ce choix garde le calcul stable tant que le projet n'a pas de table TBS complete TS 36.213.

## Limites

Le resultat est un debit radio theorique estime. Il ne connait pas la charge reseau, le backhaul, le SINR, les capacites exactes du telephone, les activations reelles de carrier aggregation, ni la configuration TDD exacte de chaque site.

L'upload est volontairement calcule avec des hypotheses de terminal : moins de couches, modulation plus basse et ratio TDD UL limite sur n78. C'est pour eviter de presenter un montant comme si le telephone avait la puissance d'emission et la chaine RF d'une antenne.

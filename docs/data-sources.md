# Sources de donnees radio

## Spectre mobile FR metropole

Source principale : Arcep, patrimoine de frequences des operateurs mobiles.

URL : https://www.arcep.fr/la-regulation/grands-dossiers-reseaux-mobiles/la-couverture-mobile-en-metropole/le-patrimoine-de-frequences-des-operateurs-mobiles.html

Le fichier `SpectrumAllocationsFrMetro.kt` contient les blocs FR metropole utilises par le moteur :

- 700 MHz : B28 / n28.
- 800 MHz : B20.
- 900 MHz : B8.
- 1800 MHz : B3.
- 2100 MHz : B1 / n1.
- 2600 MHz : B7.
- 3500 MHz : n78.

La largeur de bande par operateur vient de ces allocations. Si une bande n'existe pas pour l'operateur, le moteur l'exclut au lieu d'inventer une largeur.

## Donnees site

Les frequences, statuts, azimuts et hauteurs viennent des donnees locales ANFR deja importees par GeoTower. Le calculateur ne suppose pas qu'une frequence declaree est automatiquement active commercialement : les bandes en projet restent exclues par defaut dans l'interface.

## References modele

- 5G NR : 3GPP TS 38.306 pour la structure de formule de debit maximal et les hypotheses de couches/modulation : https://portal.3gpp.org/desktopmodules/Specifications/SpecificationDetails.aspx?specificationId=3193
- 4G LTE : TS 36.213 comme reference de procedures physiques LTE, avec fallback `LTE_APPROX_V1` tant qu'une table TBS complete n'est pas embarquee : https://portal.3gpp.org/desktopmodules/Specifications/SpecificationDetails.aspx?specificationId=2427

Les documents 3GPP/ETSI evoluent par releases. La version `FR_RADIO_THROUGHPUT_V1` documente donc les hypotheses dans les resultats pour rester auditables.

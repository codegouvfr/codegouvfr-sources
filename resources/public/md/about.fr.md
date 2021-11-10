## Pourquoi ce site ?

Tout code source obtenu ou développé par un organisme remplissant une
mission de service public est considéré comme un document
administratif, relevant des obligations de publication en open data.

De nombreux organismes publient déjà des codes sources : nous les
listons sur ce site de façon à faciliter leur découverte et à
encourager les administrations à y trouver des occasions de
réutilisation ou de contribution.

## Comment est-il construit ?

1. [codegouvfr-sources](https://git.sr.ht/~etalab/codegouvfr-sources) référence les comptes d'organisation ouverts par des organismes publics sur github.com, gitlab.com ou des forges GitLab locales ;
2. [codegouvfr-fetch-data](https://git.sr.ht/~etalab/codegouvfr-fetch-data) récupère les données des organisations et des dépôts à partir de cette liste ;
3. [codegouvfr-consolidate-data](https://git.sr.ht/~etalab/codegouvfr-consolidate-data) consolide les données et y ajoute la liste des dépendances ;
4. [code.gouv.fr](https://git.sr.ht/~etalab/code.gouv.fr) expose les données.

## Où télécharger les données ?

Toutes les données sont publiées sous [licence Ouverte 2.0](https://spdx.org/licenses/etalab-2.0.html).

* La liste des organisations en [csv](/data/organizations/csv/all.csv) et [json](/data/organizations/json/all.json)
* La liste des dépôts en [csv](/data/repositories/csv/all.csv) et [json](/data/repositories/json/all.json)
* La liste des dépendances en [json](/data/deps.json)

## Comment contribuer ?

Vous êtes une mission de service public et publiez des codes
sources que vous souhaitez référencer ?
[Écrivez-nous](mailto:logiciels-libres@data.gouv.fr) !

<div class="fr-highlight">
  <p>Vous êtes une mission de service public et souhaitez référencer vos dépôts de code source ?  <a href="mailto:logiciels-libres@data.gouv.fr">Écrivez-nous !</a>
  </p>
</div>

## Pourquoi ce site ?

Tout code source obtenu ou développé par un organisme remplissant une
mission de service public est considéré comme un document
administratif, relevant des obligations de publication en open data.

De nombreux organismes publient déjà des codes sources : nous les
présentons sur ce site de façon à faciliter leur découverte et à
encourager les administrations à les réutiliser ou à y contribuer.

## Qu'est-ce qu'un « code source » ?

Le code source est la version lisible d'un programme informatique.

Pour d'autres précisions sur des termes techniques, [consultez ce glossaire](https://man.sr.ht/~etalab/logiciels-libres/glossary.fr.md).

## Où télécharger les données ?

Toutes les données sont publiées sous [licence Ouverte 2.0](https://spdx.org/licenses/etalab-2.0.html).

* La liste des organisations en [csv](/data/organizations/csv/all.csv) et [json](/data/organizations/json/all.json)
* La liste des dépôts en [csv](/data/repositories/csv/all.csv) et [json](/data/repositories/json/all.json)
* La liste des dépendances en [csv](/data/dependencies/csv/all.csv) et [json](/data/dependencies/json/all.json)

## Comment ces données sont-elles construites ?

1. [codegouvfr-sources](https://git.sr.ht/~etalab/codegouvfr-sources) référence les comptes d'organisation ouverts par des organismes publics sur github.com, gitlab.com ou des forges GitLab locales ;
2. [codegouvfr-fetch-data](https://git.sr.ht/~etalab/codegouvfr-fetch-data) récupère les données des organisations et des dépôts à partir de cette liste ;
3. [codegouvfr-consolidate-data](https://git.sr.ht/~etalab/codegouvfr-consolidate-data) consolide les données et y ajoute la liste des dépendances ;
4. [code.gouv.fr](https://git.sr.ht/~etalab/code.gouv.fr) expose les données.

**Attention**: Nous ne référençons pas les comptes personnels.

Nous ne référençons pour l'instant que les comptes de github.com, gitlab.com et des instances GitLab locales : si vous pouvez aider à référencer des comptes qui publient sur d'autres forges ([SourceHut](https://sourcehut.org/), [BitBucket](https://bitbucket.org), instances [Gogs](https://gogs.io) ou [Gitea](https://gitea.io), etc.), n'hésitez pas à [contribuer](https://git.sr.ht/~etalab/codegouvfr-fetch-data).


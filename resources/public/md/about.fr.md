<div class="fr-highlight">
  <p>Vous êtes une mission de service public et souhaitez référencer vos dépôts de code source ?  <a href="mailto:contact@code.gouv.fr">Écrivez-nous !</a>
  </p>
</div>

## Pourquoi ce site ?

Tout code source obtenu ou développé par un organisme remplissant une mission de service public est considéré comme un document administratif, relevant des obligations de publication en open data.

De nombreux organismes publient déjà des codes sources : nous les présentons sur ce site de façon à faciliter leur découverte et à encourager les administrations à les réutiliser ou à y contribuer.

### Qu'est-ce qu'un « dépôt de code source » ?

Le *code source* est la version lisible par un humain d'un programme informatique.  Un *dépôt de code source* est l'ensemble des fichiers d'un programme.  Tous les dépôts référencés sur ce site utilisent le logiciel de gestion de versions Git.

Ce site ne référence que les dépôts publiés via un *compte d'organisation* GitHub ou un groupe public sur gitlab.com ou une instance GitLab : les dépôts publiés via des comptes personnels ne sont pas pris en compte.

Pour d'autres précisions sur les termes techniques de ce site, [consultez ce glossaire](https://man.sr.ht/~codegouvfr/logiciels-libres/glossary.fr.md).

### Bibliothèques et dépendances

Les bibliothèques référencées sur ce site sont des bibliothèques logicielles distribuées via des plateformes dédiées (npmjs.com, pypi.org, etc.) et développées à partir des dépôts référencés.

Les dépendances sont les bibliothèques, venant du secteur public ou non, requises par les dépôts référencés.  Nous ne listons que les dépendances de premier niveau, pas les dépendances de dépendances.

### Logiciels libres du SILL et services en ligne

Le socle interministériel de logiciels libres est la liste des logiciels libres recommandés pour toutes les administrations.  Chaque logiciel de cette liste est aujourd'hui en usage dans au moins une administration, et dispose d'un « référent SILL » prêt à aider les autres administrations dans son utilisation.

Si vous souhaitez référencer de nouveaux logiciels libres utilisés dans vos administrations, rendez-vous sur [le site de gestion du SILL](https://sill.etalab.gouv.fr).

Les services en ligne référencés sur le site sont tous des instances de logiciels libres mises à la disposition des agents publics.

## Comment contribuer ?

Pour ajouter une forge, un compte d'organisation GitHub ou un groupe GitLab, écrivez à [contact@code.gouv.fr](mailto:contact@code.gouv.fr) ou envoyez un correctif sur [ce dépôt](https://git.sr.ht/~codegouvfr/codegouvfr-sources/).

**Attention**: Nous ne référençons pas les comptes personnels.

## Où télécharger les données ?

Ces données sont publiées sous [licence Ouverte 2.0](https://spdx.org/licenses/etalab-2.0.html) :

* La liste des organisations en [csv](/data/organizations/csv/all.csv) et [json](/data/organizations/json/all.json).
* La liste des dépôts en [csv](/data/repositories/csv/all.csv) et [json](/data/repositories/json/all.json).
* Les données du socle interministériel de logiciels libres  et du catalogue de services : [sill.json](https://sill.etalab.gouv.fr/api/sill.json), [sill.tsv](/data/sill.tsv), [sill.pdf](/data/sill.pdf), [sill.md](/data/sill.md) et [sill.org](/data/sill.org).

Ces données, collectées depuis [libraries.io](https://libraries.io/terms), sont publiées sous licence [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) :

* La liste des bibliothèques en [csv](/data/libraries/csv/all.csv) et [json](/data/libraries/json/all.json).

## Comment suivre les mises à jour ?

Voir notre [page avec tous les flux RSS](/#/feeds).

Vous pouvez aussi suivre les annonces du pôle logiciels libres en vous abonnant à la [gazette BlueHats](https://communs.numerique.gouv.fr/gazette/) ou en nous suivant sur [Twitter](https://twitter.com/codegouvfr) et [Mastodon](https://mastodon.social/@CodeGouvFr).

## Comment ces données sont-elles construites ?

1. [codegouvfr-sources](https://git.sr.ht/~codegouvfr/codegouvfr-sources) référence les comptes d'organisation ouverts par des organismes publics sur sr.ht (SourceHut), github.com, gitlab.com ou des forges GitLab locales.
2. [codegouvfr-fetch-data](https://git.sr.ht/~codegouvfr/codegouvfr-fetch-data) récupère les données des organisations et des dépôts à partir de cette liste.
3. [codegouvfr-consolidate-data](https://git.sr.ht/~codegouvfr/codegouvfr-consolidate-data) consolide les données en y ajoutant des informations.
4. [code.gouv.fr](https://git.sr.ht/~codegouvfr/code.gouv.fr) expose les données.

Nous ne référençons pour l'instant que les comptes de sr.ht (SourceHut), github.com, gitlab.com et des instances GitLab locales : si vous pouvez aider à référencer des comptes qui publient sur d'autres forges ([SourceHut](https://sourcehut.org/), [BitBucket](https://bitbucket.org), instances [Gogs](https://gogs.io) ou [Gitea](https://gitea.io), etc.), n'hésitez pas à [contribuer](https://git.sr.ht/~codegouvfr/codegouvfr-fetch-data).

[![img](https://img.shields.io/badge/Licence-EPL-orange.svg?style=flat-square)](https://git.sr.ht/~etalab/code.gouv.fr/blob/master/LICENSE)

# code.gouv.fr

Browse sector source code repositories from the french public sector.

![img](codegouvfr.png)

# Data

- The list of organizations: [code.gouv.fr/data/orgas.json](https://code.gouv.fr/data/orgas.json)
- The list of repositories: [code.gouv.fr/data/repos.json](https://code.gouv.fr/data/repos.json)
- The list of dependencies: [code.gouv.fr/data/deps.json](https://code.gouv.fr/data/deps.json)

# How it works

This frontend retrieves information source code [repositories](https://api-code.etalab.gouv.fr/api/repertoires/all) and [organizations](https://api-code.etalab.gouv.fr/api/organisations/all) from the french public sector.  The source code for creating these endpoints can be found [here](https://git.sr.ht/~etalab/codegouvfr-fetch-data).

# Develop

    ~$ git clone https://git.sr.ht/~etalab/code.gouv.fr
    ~$ cd code.gouv.fr/
    ~$ clj -M:fig

This will open <http://localhost:9500>.  You can then hack and see changes going live.

# Contribute

The development of this repository happens on [the SourceHut repository](https://git.sr.ht/~etalab/code.gouv.fr).  The code is also published on [GitHub](https://github.com/etalab/code.etalab.gouv.fr) to reach more developers.

Your help is welcome.  You can contribute with bug reports, patches, feature requests or general questions by sending an email to [~etalab/logiciels-libres@lists.sr.ht](mailto:~etalab/logiciels-libres@lists.sr.ht).

## Translation

If you want to help with the translation:

- add your language to `src/cljc/codegouvfr/i18n.cljc`;
- add relevant variables to `src/clj/codegouvfr/views.clj`.

Hack and send a *pull request*, I would be happy to integrate your contribution.

# Support the Clojure(script) ecosystem

If you like Clojure(script), please consider supporting maintainers by donating to [clojuriststogether.org](https://www.clojuriststogether.org).

# Licenses

2019-2020 DINUM, Bastien Guerry.

This application is published under the [EPL 2.0 license](LICENSES/LICENSE.EPL-2.0.txt).

The data referenced in this `README.md` and exposed on `code.gouv.fr` are published under the [Etalab 2.0 license](https://git.sr.ht/~etalab/code.gouv.fr/tree/master/item/LICENSES/LICENSE.Etalab-2.0.md).

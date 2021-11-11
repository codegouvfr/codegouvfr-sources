[![Software License](https://img.shields.io/badge/Licence-EPL%2C%20Licence%20Ouverte-orange.svg?style=flat-square)](https://git.sr.ht/~etalab/code.gouv.fr/tree/master/item/LICENSES)

# code.gouv.fr

Explore source code from the french public sector.

![img](codegouvfr.png)

# Get the data

The list of organizations/groups accounts is maintained on
[codegouvfr-sources](https://git.sr.ht/~etalab/codegouvfr-sources).

From these sources, data are then fetched by
[codegouvfr-fetch-data](https://git.sr.ht/~etalab/codegouvfr-fetch-data)
and consolidated with
[codegouvfr-consolidate-data](https://git.sr.ht/~etalab/codegouvfr-consolidate-data).

- Organizations: as [csv](https://code.gouv.fr/data/organizations/csv/all.csv) or [json](https://code.gouv.fr/data/organizations/json/all.json)
- Repositories: as [csv](https://code.gouv.fr/data/repositories/csv/all.csv) or [json](https://code.gouv.fr/data/repositories/json/all.json)
- Dependencies: [json](https://code.gouv.fr/data/deps.json)

# Develop

    ~$ git clone https://git.sr.ht/~etalab/code.gouv.fr
    ~$ cd code.gouv.fr/
    ~$ clj -M:fig

This will open you browser at `http://localhost:9500` where you can
see your changes as you hack.

**Note**: if you don't have the `clj` executable, try `apt install
clojure` or [follow the
instructions](https://clojure.org/guides/getting_started) on
clojure.org.

# Contribute

The development of this repository happens on [the SourceHut repository](https://git.sr.ht/~etalab/code.gouv.fr).

The code is also published on [GitHub](https://github.com/etalab/code.etalab.gouv.fr) to reach more developers.

Your help is welcome.  You can contribute with bug reports, patches, feature requests or general questions by sending an email to [~etalab/logiciels-libres@lists.sr.ht](mailto:~etalab/logiciels-libres@lists.sr.ht).

# Support the Clojure(script) ecosystem

If you like Clojure(script), please consider supporting maintainers by donating to [clojuriststogether.org](https://www.clojuriststogether.org).

# Licenses

2019-2021 DINUM, Bastien Guerry.

This application is published under the [EPL 2.0 license](LICENSES/LICENSE.EPL-2.0.txt).

The data referenced in this `README.md` and exposed on `code.gouv.fr` are published under the [Etalab 2.0 license](https://git.sr.ht/~etalab/code.gouv.fr/tree/master/item/LICENSES/LICENSE.Etalab-2.0.md).

[![Software License](https://img.shields.io/badge/Licence-EPL%2C%20Licence%20Ouverte-orange.svg?style=flat-square)](https://git.sr.ht/~etalab/code.gouv.fr/tree/master/item/LICENSES)

# code.gouv.fr

Web application to explore the source code from the french public sector.

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

# Contributing

The development of this repository happens on [the SourceHut repository](https://git.sr.ht/~etalab/code.gouv.fr).

The code is also published on [GitHub](https://github.com/etalab/code.etalab.gouv.fr) to reach more developers, but *do not propose pull requests* there.

For confidential feedback, use [logiciels-libres@data.gouv.fr](mailto:logiciels-libres@data.gouv.fr).

For *patches*, configure your local copy of the repository like this:

`git config format.subjectPrefix 'PATCH code.gouv.fr'`

For bug reports, feature requests and general questions, send an email
to the public mailing list [~etalab/codegouvfr-devel@lists.sr.ht](mailto:~etalab/codegouvfr-devel@lists.sr.ht).

# Support the Clojure(script) ecosystem

If you like Clojure(script), please consider supporting maintainers by donating to [clojuriststogether.org](https://www.clojuriststogether.org).

# Licenses

2019-2022 DINUM, Bastien Guerry.

This application is published under the [EPL 2.0 license](LICENSES/LICENSE.EPL-2.0.txt).

The data referenced in this `README.md` and exposed on `code.gouv.fr` are published under the [Etalab 2.0 license](https://git.sr.ht/~etalab/code.gouv.fr/tree/master/item/LICENSES/LICENSE.Etalab-2.0.md).

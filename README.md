[![img](https://img.shields.io/badge/code.gouv.fr-contributif-blue.svg)](https://code.gouv.fr/documentation/#/publier)
[![Software License](https://img.shields.io/badge/Licence-EPL%2C%20Licence%20Ouverte-orange.svg)](https://github.com/codegouvfr/codegouvfr-sources/tree/main/LICENSES)

# code.gouv.fr/sources

This repository contains a web static frontend for exploring source
code published by french public sector organizations. This frontend is
available on [code.gouv.fr/sources](https://code.gouv.fr/sources/).

*If you are looking for the source code of the main `code.gouv.fr`
website, check
[codegouvfr-website](https://github.com/codegouvfr/codegouvfr-website.)*

![img](codegouvfr.png)

# Get the data

<<<<<<< HEAD
Data used for [code.gouv.fr/sources](https://code.gouv.fr/sources) are maintained on [codegouvfr-data](https://git.sr.ht/~codegouvfr/codegouvfr-data) and consolidated by [this script](https://git.sr.ht/~codegouvfr/codegouvfr-cli/tree/main/item/src/codegouvfr-output-data.clj).
||||||| efd6183
The list of organizations accounts is maintained on [codegouvfr-sources](https://git.sr.ht/~codegouvfr/codegouvfr-sources).
=======
Data used for [code.gouv.fr/sources](https://code.gouv.fr/sources) are maintained on [codegouvfr-data](https://github.com/codegouvfr/codegouvfr-data) and consolidated by [this script](https://github.com/codegouvfr/codegouvfr-cli/tree/main/item/src/codegouvfr-output-data.clj).
>>>>>>> main

You can download them:

- Organizations: as [csv](https://code.gouv.fr/data/codegouvfr-organizations.csv) or [json](https://code.gouv.fr/data/codegouvfr-organizations.json)
- Repositories: as [csv](https://code.gouv.fr/data/codegouvfr-repositories.csv) or [json](https://code.gouv.fr/data/codegouvfr-repositories.json)

# Develop

<<<<<<< HEAD
    ~$ git clone https://git.sr.ht/~codegouvfr/codegouvfr-sources
    ~$ cd codegouvfr-sources/
||||||| efd6183
    ~$ git clone https://git.sr.ht/~codegouvfr/code.gouv.fr
    ~$ cd code.gouv.fr/
=======
    ~$ git clone https://github.com/codegouvfr/codegouvfr-sources
    ~$ cd codegouvfr-sources/
>>>>>>> main
    ~$ clj -M:fig

This will open you browser at `http://localhost:9500` where you can see your changes as you hack.

**Note**: if you don't have the `clj` executable, try `apt install clojure` or [follow the instructions](https://clojure.org/guides/getting_started) on clojure.org.

# [Contributing](CONTRIBUTING.md)

# Support the Clojure(script) ecosystem

If you like Clojure(script), please consider supporting maintainers by donating to [clojuriststogether.org](https://www.clojuriststogether.org).

# Licenses

2019-2024 DINUM, Bastien Guerry.

This application is published under the [EPL 2.0 license](LICENSES/LICENSE.EPL-2.0.txt).

The data referenced in this `README.md` and exposed on `code.gouv.fr` are published under the [Etalab 2.0 license](LICENSES/LICENSE.Etalab-2.0.md).

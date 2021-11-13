<div class="fr-highlight">
  <p>If you are a French public agency and want to add new source code repositories, please <a href="mailto:logiciels-libres@data.gouv.fr">send us an email</a>.
  </p>
</div>

## Why this website?

Source code bought or developed by public agencies are considered
administrative documents, due to be opened as open data.

Many agencies already publish source code repositories : we are
listing them on this website to make it easier to find them and to
encourage administrations to reuse them or to contribute to them.

## How do we build the data?

1. [codegouvfr-sources](https://git.sr.ht/~etalab/codegouvfr-sources) contains the list of organization/group accounts on github.com, gitlab.com or local GitLab forges;
2. [codegouvfr-fetch-data](https://git.sr.ht/~etalab/codegouvfr-fetch-data) fetches data from organizations and repositories from this list;
3. [codegouvfr-consolidate-data](https://git.sr.ht/~etalab/codegouvfr-consolidate-data) consolidate the data and add the list of dependencies;
4. [code.gouv.fr](https://git.sr.ht/~etalab/code.gouv.fr) expose the data.

## Where to download the data?

All data are published under the [Open License 2.0](https://spdx.org/licenses/etalab-2.0.html).

- The list of organizations as [csv](/data/organizations/csv/all.csv) and [json](/data/organizations/json/all.json)
- The list of repositories as [csv](/data/repositories/csv/all.csv) and [json](/data/repositories/json/all.json)
- The list of dependencies as [json](/data/deps.json)

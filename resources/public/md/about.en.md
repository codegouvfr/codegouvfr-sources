<div class="fr-highlight">
  <p>If you are a French public agency and want to add new source code repositories, please <a href="mailto:contact@code.gouv.fr">send us an email</a>.
  </p>
</div>

## Why this website?

Source code bought or developed by public agencies are considered administrative documents, due to be opened as open data.

Many agencies already publish source code repositories : we are listing them on this website to make it easier to find them and to encourage administrations to reuse them or to contribute to them.

### What is a "source code repository"?

The *source code* is the human-readable version of a computer program.  A *source code repository* is the set of files for a computer program.  All source code repositories listed on this website use Git as their versioning system.

This website only references repositories from organizational accounts (GitHub) or public groups (gitlab.com or GitLab instances) : repositories on personal accounts are not listed.

For more details on other technical terms, [please check this glossary](https://code.gouv.fr/documentation/#glossaire).

### Libraries and dependencies

Libraries referenced on this web are those distributed on dedicated platforms (npmjs.com, pypi.org, etc.) and developed from referenced repositories.

Dependencies are libraries, from the French public sector or not, required by the referenced repositories.  We only list first-level dependencies, not dependencies of dependencies.

### Recommended free software and services

The list of recommended free software is a list of software currently in use in the French administration.  Each software in this list has a public servant who can help other administrations use this software.

If you want to contribute new Free Software used in your administration, please contribute to [code.gouv.fr/sill](https://code.gouv.fr/sill).

Referenced services are services for public agents based on a free software.

## How to contribute?

To submit a new organization account, send us an email at [contact@code.gouv.fr](mailto:contact@code.gouv.fr) or send a patch against [this repository](https://git.sr.ht/~codegouvfr/codegouvfr-sources/).

**Warning**: We don't reference personal accounts.

To contribute to this website, you can submit a patch against [this repository](https://git.sr.ht/~codegouvfr/codegouvfr-sources)

More generally, you can find more information on how to contribute to free and open source software in this living [documentation](https://code.gouv.fr/documentation/#/publier).

## Where to download the data?

These data are published under the [Open License 2.0](https://spdx.org/licenses/etalab-2.0.html):

- The list of organizations as [csv](/data/organizations/csv/all.csv) and [json](/data/organizations/json/all.json).
- The list of repositories as [csv](/data/repositories/csv/all.csv) and [json](/data/repositories/json/all.json).
- Data from the [SILL](https://code.gouv.fr/sill) and the catalog of services: [sill.json](https://code.gouv.fr/sill/api/sill.json), [sill.tsv](/data/sill.tsv), [sill.pdf](/data/sill.pdf), [sill.md](/data/sill.md) and [sill.org](/data/sill.org).

These data, collected from [libraries.io](https://libraries.io/terms), are published under the [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) license:

- The list of libraries as [csv](/data/libraries/csv/all.csv) and [json](/data/libraries/json/all.json).

## How to get updates?

Check our [list of RSS feeds](#/feeds).

You can also follow us on [Twitter](https://twitter.com/codegouvfr) and [Mastodon](https://social.numerique.gouv.fr/@codegouvfr).

## How are the data collected?

1. [codegouvfr-sources](https://git.sr.ht/~codegouvfr/codegouvfr-sources) contains the list of organization accounts on sr.ht (SourceHut) github.com, gitlab.com or local GitLab forges.
2. [codegouvfr-fetch-data](https://git.sr.ht/~codegouvfr/codegouvfr-fetch-data) fetches data from organizations and repositories from this list.
3. [codegouvfr-consolidate-data](https://git.sr.ht/~codegouvfr/codegouvfr-consolidate-data) consolidates the data and enrich them.
4. [code.gouv.fr](https://git.sr.ht/~codegouvfr/code.gouv.fr) exposes the data.

So far, we only reference accounts on sr.ht (SourceHut), github.com, gitlab.com and local GitLab instances: if you can help referencing accounts that publish code on other forges ([SourceHut](https://sourcehut.org/), [BitBucket](https://bitbucket.org), [Gogs](https://gogs.io) or [Gitea](https://gitea.io) instances, etc.), please see how to [contribute](https://git.sr.ht/~codegouvfr/codegouvfr-fetch-data).


## Who are we ?

This site is managed and published by DINUM's Free Software Unit (an OSPO). You can find out more [here](https://code.gouv.fr/en/mission/).

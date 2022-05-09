<div class="fr-highlight">
  <p>If you are a French public agency and want to add new source code repositories, please <a href="mailto:logiciels-libres@data.gouv.fr">send us an email</a>.
  </p>
</div>

## Why this website?

Source code bought or developed by public agencies are considered administrative documents, due to be opened as open data.

Many agencies already publish source code repositories : we are listing them on this website to make it easier to find them and to encourage administrations to reuse them or to contribute to them.

## What is a "source code"?

The source code is the readable version of a computer program.

For more details on other technical terms, [please check this glossary](https://man.sr.ht/~etalab/logiciels-libres/glossary.en.md).

## Libraries and dependencies

Libraries referenced on this web are those distributed on dedicated platforms (npmjs.com, pypi.org, etc.) and developed from referenced repositories.

Dependencies are libraries, from the French public sector or not, required by the referenced repositories.  We only list first-level dependencies, not dependencies of dependencies.

# Recommended free software and services

The list of recommended free software is a list of software currently in use in the French administration.  Each software in this list has a public servant who can help other administrations use this software.

Referenced services are services for public agents based on a free software.

## Where to download the data?

All data are published under the [Open License 2.0](https://spdx.org/licenses/etalab-2.0.html).

- The list of organizations as [csv](/data/organizations/csv/all.csv) and [json](/data/organizations/json/all.json).
- The list of repositories as [csv](/data/repositories/csv/all.csv) and [json](/data/repositories/json/all.json).
- The list of libraries as [csv](/data/libraries/csv/all.csv) and [json](/data/libraries/json/all.json).

## How to get updates?

Check our [list of RSS feeds](/#/feeds).

You can also follow us on [Twitter](https://twitter.com/codegouvfr) and [Mastodon](https://mastodon.social/@codegouvfr).

## How are the data collected?

1. [codegouvfr-sources](https://git.sr.ht/~etalab/codegouvfr-sources) contains the list of organization/group accounts on sr.ht (SourceHut) github.com, gitlab.com or local GitLab forges;
2. [codegouvfr-fetch-data](https://git.sr.ht/~etalab/codegouvfr-fetch-data) fetches data from organizations and repositories from this list;
3. [codegouvfr-consolidate-data](https://git.sr.ht/~etalab/codegouvfr-consolidate-data) consolidate the data and add the list of dependencies;
4. [code.gouv.fr](https://git.sr.ht/~etalab/code.gouv.fr) expose the data.

**Warning**: We don't reference personal accounts.

So far, we only reference accounts on sr.ht (SourceHut), github.com, gitlab.com and local GitLab instances: if you can help referencing accounts that publish code on other forges ([SourceHut](https://sourcehut.org/), [BitBucket](https://bitbucket.org), [Gogs](https://gogs.io) or [Gitea](https://gitea.io) instances, etc.), please see how to [contribute](https://git.sr.ht/~etalab/codegouvfr-fetch-data).

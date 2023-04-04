---
id: Contributing
title: Contributor's guide
slug: /contributing
---


# Welcome to shiva contributor's guide

Thank you for investing your time in contributing to the project! 

Read our [Code of Conduct](CODE_OF_CONDUCT.md) to keep our community approachable and respectable.

In this guide you will get an overview of the contribution workflow from opening an issue, creating a PR, reviewing, and merging the PR.

## New contributor guide

To get an overview of the project, read the [README](README.md).

### Dev Setup

To start developing with shiva, the easiest way to set up dev env is via [sdkman](https://sdkman.io/). Just cd into the root of the project dir and :

```
sdk env
```

To setup required java version.

### Formatting and Linting

Before opening a PR, please format and lint by executing

```
sbt fmt
```

```
sbt fix
```

You can check if everything is alright by executing

```
sbt check
```

### Running tests

```
sbt clean test
```

### Submitting a PR

On each commit to Github, a Github Action script runs a full build process. This build validates that the build runs, all tests pass and all code meets code standards.
Anything that runs in the build script has a corresponding way to run locally and should be run before committing.

1. Please add unit tests for the changes and run all tests locally (see above). Note that there is a code coverage check on build that might fail if coverage is not enough.
2. Format and lint before committing the changes (see above)
3. Commit and push the changes to your branch in a fork.
4. Submit PR on Github via ```Pull Requests``` tab for the project.
5. PRs will be subsequently reviewed before merging. Please do not be discouraged if there are follow-up questions on the changes. We just strive to maintain uniform code quality and format.
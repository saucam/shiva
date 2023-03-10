# shiva [WIP]
shiva is a library for Simple High dimensional Indexed Vector search Algorithms.

![](https://img.shields.io/github/license/saucam/shiva)
![](https://img.shields.io/github/issues/saucam/shiva)
[![CI](https://github.com/saucam/shiva/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/saucam/shiva/actions/workflows/ci.yaml)
[![codecov](https://codecov.io/gh/saucam/shiva/branch/main/graph/badge.svg?token=7UDJE3NX5K)](https://codecov.io/gh/saucam/shiva)

## Overview

Basic guiding principle is to be:
- Simple (non-distributed, single threaded indexing, easy to use)
- Support high dimensional vectors, optimize memory for speed
- Support many different distance metrics
- Scale out to different indices and algorithms

## Dev guide

To start developing with shiva, easiest way to set up dev env is via [sdkman](https://sdkman.io/). Just cd into the root of the project dir and :

```
sdk env
```

To setup required java version.

### Running tests

```
sbt clean test
```

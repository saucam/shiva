---
id: readme
title: README
hide_title: false
hide_table_of_contents: false
sidebar_label: Introduction
sidebar_position: 1
---
# shiva [WIP]
shiva is a library for Simple High dimensional Indexed Vector search Algorithms.

![](https://img.shields.io/github/license/saucam/shiva)
![](https://img.shields.io/github/issues/saucam/shiva)
[![CI](https://github.com/saucam/shiva/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/saucam/shiva/actions/workflows/ci.yaml)
[![codecov](https://codecov.io/gh/saucam/shiva/branch/main/graph/badge.svg?token=7UDJE3NX5K)](https://codecov.io/gh/saucam/shiva)
[![Sonatype Snapshots](https://img.shields.io/nexus/s/io.github.saucam/shiva-core_2.13?server=https%3A%2F%2Fs01.oss.sonatype.org%2F)](https://s01.oss.sonatype.org/content/repositories/snapshots/io/github/saucam/shiva-core_2.13/)
[![Sonatype Releases](https://img.shields.io/nexus/r/io.github.saucam/shiva-core_2.13?nexusVersion=2&server=https%3A%2F%2Fs01.oss.sonatype.org)](https://s01.oss.sonatype.org/content/repositories/releases/io/github/saucam/shiva-core_2.13/)
## Overview

Basic guiding principle is to be:
- Simple (non-distributed, single threaded indexing, easy to use)
- Support high dimensional vectors, optimize memory for speed
- Support many distance metrics
- Scale out to different indices and algorithms

## Installation
To use Shiva, add the following to your ```build.sbt```

For releases versions:
```scala
resolvers +=
  "Sonatype OSS Releases" at "https://s01.oss.sonatype.org/content/repositories/releases"

libraryDependencies ++= Seq(
  "io.github.saucam" %% "shiva-core" % "<version>"
)
```

For snapshot versions:

```scala
resolvers +=
  "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "io.github.saucam" %% "shiva-core" % "<version>"
)
```

## Usage

The following gives a simple example on how to use the hnsw index in the library after adding the dependency:

```scala
val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
  dimensions = 3,
  maxItemCount = 1000000,
  m = 32,
  distanceCalculator = new EuclideanDistanceDouble
).build()

val item1 = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))
val item2 = IntDoubleIndexItem(2, Vector(8.01d, 2.06d, 1.8d))
val item3 = IntDoubleIndexItem(3, Vector(9.34d, 3.06d, 3.1d))

index.add(item1)
index.add(item2)
index.add(item3)

val results = index.findKSimilarItems(item1.id, 10)
results.foreach(println())
```

## Contributing
See the [contributor's guide](CONTRIBUTING.md)

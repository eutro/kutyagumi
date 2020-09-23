# Kutyagumi

A two-player game about cells and taking over the world!

## Where to play

The web version of the game is available to play [here](https://eutropius225.github.io/kutyagumi/index.html),
or you can [build it yourself](#building)!

## How to play

### Overview

There are two colonies of cells (![Cells](resources/public/assets/cells.png)), red and green, 
fighting for territory.

Your objective is to seize more of the board than your opponent.

### Placement

Cells can be placed adjacent (up, down, left or right) to other cells of their colour, 
provided there is no wall (![Wall](resources/public/assets/walls.png)) between them.

To place a cell, click a suitable place on the board when it is your turn.

![](resources/showcase/place_red.gif)

_Note that the background colour reflects whose turn it is._ 

## Building

To build this project, you'll need the Clojure CLI tool:

https://clojure.org/guides/deps_and_cli


To develop in a browser with live code reloading:

```
clj -A:dev
```


To build a release version for the web:

```
clj -A:prod:play-cljc
```


To develop the native version:

```
clj -A:dev native

# NOTE: On Mac OS, you need to add the macos alias:

clj -A:dev:macos native
```


To build the native version as a jar file:

```
clj -A:prod uberjar
```

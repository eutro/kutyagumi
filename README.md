# Kutyagumi Server

A simple server for Kutyagumi.

## Running

To run, you will need Java.

Then run with arguments:

```shell script
java -jar path/to/kutyagumi-server-standalone.jar
# or
lein run server.core
```

You can optionally supply a port for the server to run on (80 by default):

```shell script
java -jar path/to/kutyagumi-server-standalone.jar 8080
# or
lein run server.core 8080
```

## Building

To build, you will need [leiningen](https://leiningen.org/).

```shell script
lein uberjar # to build an executable jar usable with the above commands
```

## Plugin: "aeonics.jit"

This *Bloodstream Enterprise Suite* plugin provides dynamic runtime compilation
and execution of components from simple source files.

## Compile and package

You can use your favourite tool (Maven, Gradle,...) but to be honest, we prefer
the plain simple standard and out-of-the-box `javac`.

The binary distribution of the *aeonics.system* core `ae.jar` should be in the
current directory and the *aeonics.http* plugin `aeonics.http.jar` should be
in the `plugins` directory.

```shell
javac -source 11 -target 11 -nowarn -XDignore.symbol.file \
      -d aeonics.jit/bin \
      --module-path .;plugins \
      --module-source-path .\
      --module aeonics.jit

jar -c --file=aeonics.jit.jar \
    -C aeonics.jit/bin/aeonics.jit \
    .
```

## Deployment

Place the binary distribution in the `plugins` folder of your installation.

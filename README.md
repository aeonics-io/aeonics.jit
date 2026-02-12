## Plugin: "aeonics.jit"

This Aeonics software plugin provides dynamic runtime compilation
and execution of components from simple source files.

## Compile and package

You can use your favourite tool (Maven, Gradle,...) but to be honest, we prefer
the plain simple standard and out-of-the-box `javac`.

The binary distribution of the *aeonics.boot* jar should be in the
current directory, and the *aeonics.core* and *aeonics.http* jars should be 
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

## Security Notice

**This plugin enables arbitrary Java code execution at runtime.**

The JIT compilation endpoint (`/api/admin/jit/entity`) accepts Java source code, compiles it
in-process, and instantiates the result with the same privileges as the host JVM. There is no
sandboxing, no class whitelist, and no restriction on what the compiled code can do. This is
by design.

### Rationale

The Aeonics framework follows an "orchestration in runtime" philosophy. Hot-deploying code at
runtime is the core mechanism that enables the platform to provide developers
with maximum flexibility and ease of use. The ability to compile and load new components without
restarting the runtime is not a vulnerability -- it is the product's primary feature.

### Deployment Responsibility

Including this plugin in a deployment is an explicit opt-in to runtime code execution. If your
deployment does not require hot-deploy capabilities, **do not include the `aeonics.jit` plugin**.

When this plugin is included, the following controls must be in place:

- The `/api/admin/*` path must be restricted to trusted administrative users only (SUPERADMIN).
- Administrative accounts must enforce strong authentication (password + MFA).
- Access to the administration interface should be network-restricted where possible.
- All JIT compilation requests should be monitored through audit logs.

The security of this feature relies entirely on the authorization layer. Any weakness in access
control that permits non-admin access to JIT endpoints results in full system compromise.

## Deployment

Place the binary distribution in the `plugins` folder of your installation.

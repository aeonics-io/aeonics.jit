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
in-process, and instantiates the result with the same privileges as the host JVM. By default
there is no sandboxing and no restriction on what the compiled code can do. This is by design.

The endpoint accepts an optional `policy` parameter naming a registered `policy.Policy`
entity, whose inspector receives the `policy.References` of the compiled bytecode (collected by
`Compiler.scan()`, after `javac` succeeds and before the class is loaded) and may throw to
reject the deployment. Uniqorn uses it to keep entry-level plans inside the framework API.

`References` reports two axes, because a class name on its own cannot separate them:

- `invoked()` — the members the code calls or reads, as `package.Class.member`. This is where
  capability sits. The owner is the type the call was compiled against, not the type declaring
  the member, so an inherited member invoked from a subclass is reported against the subclass.
- `interfaces()` — the interfaces the code implements. These leave no trace in `invoked()`:
  an interface has no constructor to chain to, and a call to an inherited default method is
  compiled against the implementing class.

Superclasses are deliberately absent: every subclass constructor chains to a superclass
constructor, so `extends Foo` always surfaces as `Foo.<init>` in `invoked()`.

Types named only in descriptors and bare class constants are deliberately **not** reported. A
parameter or return type can only be acted upon through a call, which is already reported
against its own owner, and the `InnerClasses` attribute forces the enclosing class of every
nested type named anywhere into the constant pool — which is why a naive scan reports
`java.lang.invoke.MethodHandles` for every class containing a lambda or a string concatenation.

A policy constrains what a caller can compile against, but the code that does get compiled
still runs with full JVM privileges. It cannot see through a *confused deputy*: an allowed API
that resolves a type from a caller-supplied string reflects on the caller's behalf without any
of it appearing in `References`. Do not mistake an attached policy for isolation.

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

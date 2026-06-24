# Robust SBPP-NLIA-RMSA Multi-Domain Contribution

This project contains the Net2Plan implementation used to evaluate the robust
SBPP-NLIA-RMSA mechanism in a multi-domain elastic optical network.

## Installation

The source code is published without generated JAR files. To reproduce the
project, install Java 8 and Net2Plan 0.7.0.1, then import this folder as an
existing Eclipse project.

In Eclipse, define the `NET2PLAN_HOME` classpath variable with the path of the
Net2Plan installation directory: `Window > Preferences > Java > Build Path >
Classpath Variables > New`. Use `NET2PLAN_HOME` as the variable name. For
example:

```text
C:\\Net2Plan\\Net2Plan-0.7.0.1
```

The project classpath then resolves the required libraries from
`NET2PLAN_HOME/lib`. To compile from PowerShell, run:

```powershell
.\\scripts\\compile_with_net2plan.ps1 -Net2PlanHome 'C:\\Net2Plan\\Net2Plan-0.7.0.1'
```

To create the processor JAR in Eclipse, use `File > Export > Java > JAR file`
after the project compiles successfully. Load the generated JAR in Net2Plan to
run the simulation.

## QoT modes

The Net2Plan event processor exposes the `existingConnectionQoTGuard`
parameter. It controls the verification of the impact of a new allocation on
connections that are already established.

| Mode | Behavior |
| --- | --- |
| `STRICT` | Rejects a candidate when it degrades the QoT of an established connection in the evaluated scenarios. |
| `RELAXED` | Keeps the QoT/NLI validation of the new candidate, but does not reject it because of its impact on established connections. |

## Reproducing the experimental scenarios

Load the processor in Net2Plan, then set the parameters below while keeping
the same topology, traffic model, random seed, and remaining simulation
parameters for a fair comparison.

| Scenario | `existingConnectionQoTGuard` | Failure injection |
| --- | --- | --- |
| S1 | `STRICT` | Disabled |
| S2 | `RELAXED` | Disabled |
| S3 | `STRICT` | Enabled |
| S4 | `RELAXED` | Enabled |

## Scientific trace output

The `scientificTracePath` parameter is empty by default so that the project
does not depend on a local machine path. To generate a trace, enable
`scientificTraceEnabled` and provide a writable path chosen on the local
machine, for example `C:\\results\\trace_S1.txt`.

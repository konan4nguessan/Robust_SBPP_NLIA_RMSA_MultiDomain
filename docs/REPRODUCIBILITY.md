# Reproducibility guide

## Experimental inputs

The `data/networkTopologies` directory contains the 18-node, three-domain
Net2Plan designs used in this work. Each design contains 58 directed optical
links and 306 traffic demands. The files correspond to traffic matrices scaled
to 30%, 50%, and 70% load. The reference results in this repository use the
70% design.

## Installation

1. Install Java 8 and Net2Plan 0.7.0.1.
2. Import the repository as an existing Eclipse project.
3. Define the Eclipse `NET2PLAN_HOME` classpath variable as described in the
   root README.
4. Compile the project and export the processor JAR.
5. Load the processor JAR and the selected `.n2p` design in Net2Plan.

## Running S1 to S4

The property files in `config` are reference configurations. Net2Plan does not
load them automatically: copy their values into the Simulation input
parameters and the Event generator / Provisioning algorithm parameter panels.

Use `topology_18nodes_3domains_70pct.n2p` for the reference runs. In all four
scenarios, set the global Net2Plan simulation time to 3600 seconds and the
transitory time to 150 seconds.

For S3 and S4, the deterministic failure is injected at 2900 seconds on link
75 (Node13 to Node4). The reverse directed link, 74 (Node4 to Node13), is also
failed by the processor. Repair is disabled.

## Reference outputs

`results/reference-results` contains one final report for each scenario. The
reports can be compared with the final Net2Plan simulation report after a run.

## Model scope

The hierarchical SDN architecture is modeled in the simulation. The local
controllers and the inter-domain broker are logical software components; this
repository does not deploy real SDN controllers or implement NETCONF,
RESTCONF, OpenFlow, or inter-controller communication protocols.

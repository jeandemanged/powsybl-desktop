# Introduction

PowSyBl Desktop is a desktop application to explore, edit and simulate power networks with
[PowSyBl](https://www.powsybl.org/), the open-source power system blocks framework of
[LF Energy](https://lfenergy.org/).

## PowSyBl and PowSyBl Desktop

PowSyBl is a set of Java libraries for power system modeling, simulation and visualization: a network model (IIDM),
importers and exporters for the industry formats, load flow, security analysis, sensitivity analysis, dynamic
simulation, diagrams... It is also available in Python through [PyPowSyBl](https://github.com/powsybl/pypowsybl).
PowSyBl is used to build applications, or through scripts: using it means writing code, or first building one's own
tooling around it.

PowSyBl Desktop is a graphical application on top of these libraries. It removes three barriers to adopting PowSyBl:

1. **Technical**: no Java, Python or PowSyBl programming skills are needed.
2. **Usability**: a graphical desktop environment replaces APIs and scripts.
3. **Adoption**: organizations can use PowSyBl without first building their own desktop tooling.

It brings productivity, not access to the underlying Java or Python APIs: anything it does can also be done with
PowSyBl or PyPowSyBl in code, and anything that requires automation, large batches or integration in other systems
remains the job of these libraries.

### A thin layer over PowSyBl

PowSyBl Desktop has no business logic of its own. Networks are PowSyBl networks, imports and exports are PowSyBl's,
every value shown or edited is read from or written to the network through PowSyBl, which validates it, and
computations, diagrams and limit violations come from PowSyBl libraries. What you see is what PowSyBl does: a result
obtained in PowSyBl Desktop is the result PowSyBl gives in a script with the same network and parameters.

The few exceptions are:

- the **search**, a fuzzy search by identifier or name, built by the application;
- the **map**, drawn by the application from the network's geographical positions;
- the **load flow convergence verdict** (converged, partially converged, not converged), derived by the application
  from the status PowSyBl gives for each part of the network.

### Easy to try

PowSyBl Desktop is distributed as a self-contained application for Windows, Linux and macOS, with its own Java runtime:
download, extract and run. Nothing else needs to be installed.

## Use cases

| Who                                                                                               | Uses                                                                                                               |
|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Transmission and Distribution System Operators (TSOs, DSOs), Regional Coordination Centres (RCCs) | Network modeling and analysis, operational studies                                                                 |
| Energy consultants and engineering companies                                                      | Faster engineering studies                                                                                         |
| Universities, research laboratories, electrical engineering departments                           | An accessible power system simulation environment: network visualization and simulation without custom development |
| Developers building PowSyBl-based applications                                                    | An example of a desktop client over the PowSyBl APIs; a quick way to look at a network or check a result           |

In practice, PowSyBl Desktop lets you:

- **Open networks** in the formats PowSyBl supports: CGMES, UCTE-DEF, IIDM (XML, JSON, binary), MATPOWER, PSS®E,
  IEEE CDF and PowerFactory, or start from one of the many sample networks shipped with PowSyBl. Several networks can
  be open at once, merged, and split again into their subnetworks.
- **Explore networks** through equipment tables, interactive single-line and network-area diagrams, a map and a
  search by identifier or name.
- **Edit networks**: change equipment values in place, operate switches in the diagrams, edit limits and extensions,
  create and remove substations, voltage levels and equipment, move substations and lines on the map; then export the
  network.
- **Run load flows**, AC or DC, with PowSyBl OpenLoadFlow and its full set of parameters, and inspect flows,
  voltages and limit violations.
- **Run security analyses** on contingency lists defined in the application or imported from files, and inspect the
  violations each contingency causes.
- **Understand what happened**: every operation produces a report, and the application logs and notifications keep
  track of everything that ran.

The [quick start tutorials](../quickstart/index.md) go through a load flow and a security analysis in a few minutes.

## What PowSyBl Desktop does not do yet

PowSyBl Desktop covers a part of PowSyBl only. Among what PowSyBl offers, the following is not available in the
application yet, and could come in future versions:

- **Remedial action optimization** with [OpenRAO](https://powsybl.readthedocs.io/projects/openrao/en/stable/):
  finding the topological and setpoint actions that solve the constraints found by a security analysis.
- **Remedial actions in security analysis**: operator strategies applied after a contingency.
- **Dynamic simulation**, with [PowSyBl Dynawo](https://powsybl.readthedocs.io/projects/powsybl-dynawo/en/latest/):
  time-domain simulation of the network's behavior after an event.
- **Sensitivity analysis**: the sensitivity of flows and voltages to injections, phase shifters or voltage targets.
- **Short-circuit analysis** and **flow decomposition**.
- **Study persistence**: loaded networks, results and contingency lists are not kept between two runs of the
  application. Save networks by exporting them, and contingency lists by exporting them to files; parameters are saved
  automatically.

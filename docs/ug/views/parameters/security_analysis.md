# Security Analysis

The **Security Analysis** tab sets the parameters of the security analysis, run by **Run** > **Security Analysis**
(see [Security analysis results](../analysis/security_analysis_results.md)). The security analysis always runs with
the load flow parameters of the [Load Flow](load_flow.md) tab.

![The Security Analysis tab](../../_generated/images/parameters-security-analysis.png)

## General

| Field | Editor | Description |
|---|---|---|
| **Create Result Extension** | Check box | Attach Open Load Flow specific result extensions to the security analysis result. |
| **Contingency Propagation** | Check box | Propagate a contingency to further disconnect isolated equipment left without any energized path. |
| **Thread Count** | Number | Number of parallel threads used to run contingencies (at least 1). |
| **DC Fast Mode** | Check box | Use a faster, approximate variant of the DC security analysis. |
| **Contingency Active Power Loss Distribution** | Choice | How active power lost to a contingency (disconnected loads and generators) is redistributed before running its load flow. |
| **Start With Frozen AC Emulation** | Check box | Start each contingency's HVDC AC emulation state frozen at its pre-contingency value. |

## Increased Violations

| Field | Editor | Description |
|---|---|---|
| **Flow Proportional Threshold** | Number | Minimum proportional increase for a flow violation to be reported after a contingency; 0.1 reports only increases of more than 10 %. |
| **Low Voltage Proportional Threshold** | Number | Minimum proportional increase for a low voltage violation to be reported after a contingency. |
| **Low Voltage Absolute Threshold** | Number | Minimum absolute increase (kV) for a low voltage violation to be reported after a contingency. |
| **High Voltage Proportional Threshold** | Number | Minimum proportional increase for a high voltage violation to be reported after a contingency. |
| **High Voltage Absolute Threshold** | Number | Minimum absolute increase (kV) for a high voltage violation to be reported after a contingency. |

## Modified Monitored Elements

| Field | Editor | Description |
|---|---|---|
| **Power Modification Threshold** | Number | Minimum active or reactive power change (MW, MVAr) for a monitored branch or three-winding transformer result to be reported after a contingency. |
| **Voltage Modification Proportional Threshold** | Number | Minimum proportional voltage magnitude change for a monitored bus result to be reported after a contingency. |
| **Voltage Modification Absolute Threshold** | Number | Minimum absolute voltage magnitude change (kV) for a monitored bus result to be reported after a contingency. |

## Miscellaneous

| Field | Editor | Description |
|---|---|---|
| **Intermediate Results In Operator Strategy** | Check box | Report intermediate load flow results computed while evaluating operator strategies. |
| **Debug Directory** | Text field | Directory where debug files are written; empty for none. |

They are documented in the PowSyBl documentation:

- [generic security analysis parameters](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/security/configuration.html);
- [Open Load Flow specific security analysis parameters](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/security/parameters.html).

## Import and export

The **Import** and **Export** buttons of the tab read and write the security analysis parameters only, in the PowSyBl
JSON format of security analysis parameters. Load flow parameters held by an imported file are ignored: those of the
**Load Flow** tab are kept.

# Sensitivity Analysis

The **Sensitivity Analysis** tab sets the parameters of the sensitivity analysis, run by **Run** > **Sensitivity
Analysis** (see [Sensitivity analysis results](../results/sensitivity_analysis_results.md)). The sensitivity analysis
runs with the load flow parameters of the [Load Flow](load_flow.md) tab, AC or DC as its **Calculation Mode** says.

![The Sensitivity Analysis tab](../../_generated/images/parameters-sensitivity-analysis.png)

## General

| Field | Editor | Description |
|---|---|---|
| **Calculation Mode** | Choice: `Same as load flow`, `AC`, `DC` | AC or DC, for the sensitivity analysis only: **Same as load flow** follows the **DC** parameter of the Load Flow tab, and tells which it is. A DC sensitivity analysis is much faster, but only computes branch active power sensitivities to injections, phase shifts and HVDC lines. |
| **Thread Count** | Number | Number of parallel threads used to run contingencies (at least 1). |
| **Start With Frozen AC Emulation** | Check box | Start each contingency's HVDC AC emulation state frozen at its pre-contingency value. |

## Thresholds

Sensitivities smaller in absolute value than these thresholds are not reported (shown as **Below threshold** in the
results).

| Field | Editor | Description |
|---|---|---|
| **Flow/Flow Threshold** | Number | Flow sensitivities to injections, phase shifts and HVDC lines. |
| **Voltage/Voltage Threshold** | Number | Voltage sensitivities to target voltages. |
| **Flow/Voltage Threshold** | Number | Flow and reactive power sensitivities to target voltages. |
| **Angle/Flow Threshold** | Number | Angle sensitivities to phase shifts. |

## Miscellaneous

| Field | Editor | Description |
|---|---|---|
| **Debug Directory** | Text field | Directory where Open Load Flow writes debug files; empty for none. |

They are documented in the PowSyBl documentation:

- [generic sensitivity analysis parameters](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/sensitivity/configuration.html);
- [Open Load Flow specific sensitivity analysis parameters](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/sensitivity/parameters.html).

## Import and export

The **Import** and **Export** buttons of the tab read and write the sensitivity analysis parameters only, in the
PowSyBl JSON format of sensitivity analysis parameters. Load flow parameters held by an imported file are ignored: those
of the **Load Flow** tab are kept. The **Calculation Mode** is the application's own: it is neither imported nor
exported, but saved with all the parameters.

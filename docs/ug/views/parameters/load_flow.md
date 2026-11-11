# Load Flow

The **Load Flow** tab sets the parameters of the load flow, run by **Run** > **Load Flow** (see
[Load flow results](../results/loadflow_results.md)), and also used by the security analysis. The load flow is
PowSyBl [Open Load Flow](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/).

![The Load Flow tab](../../_generated/images/parameters-load-flow.png)

## Categories

**Model**, **DC**, **Slack Distribution**, **Reference Bus**, **Voltage Controls**, **Generator Voltage Control**,
**Transformer Voltage Control**, **Shunt Voltage Control**, **Phase Control**, **Reactive Power Control**,
**Voltage Init**, **Newton-Raphson**, **Newton-Krylov**, **Solver**, **Outer Loops**, **Fast Restart**,
**Automation**, **HVDC**, **Performance**, **Debug** and **Reporting**.

Each category starts with the generic load flow parameters it holds, then lists the Open Load Flow specific
parameters of that category. A specific parameter's field shows its description; its tooltip gives its name and
default value. Depending on the parameter, the field is a check box, a choice box, a number field, a text field (a
comma or colon separated list, for a list) or a list of check boxes (a searchable country list for countries). A
value Open Load Flow refuses reverts to the previous one.

## Generic parameters

| Category | Field | Editor | Description |
|---|---|---|---|
| Model | **Model** | Radio buttons: `AC`, `DC` | The AC (non-linear) or DC (linearized) load flow model. |
| Model | **Transformer Split Shunt Admittance** | Check box | Split half of the transformer shunt admittance on each side of the branch model. |
| DC | **DC Use Transformer Ratio** | Check box | Take the transformer ratio into account in the DC approximation. |
| DC | **DC Power Factor** | Number | Power factor used to convert currents in DC mode, in (0, 1]. |
| Slack Distribution | **Distributed Slack** | Check box | Distribute the slack power mismatch across participating buses instead of assigning it all to the slack bus. |
| Slack Distribution | **Balance Type** | Choice: `PROPORTIONAL_TO_GENERATION_P`, `PROPORTIONAL_TO_GENERATION_P_MAX`, `PROPORTIONAL_TO_GENERATION_REMAINING_MARGIN`, `PROPORTIONAL_TO_GENERATION_PARTICIPATION_FACTOR`, `PROPORTIONAL_TO_LOAD`, `PROPORTIONAL_TO_CONFORM_LOAD` | How the distributed slack is shared among generators or loads. |
| Slack Distribution | **Read Slack Bus** | Check box | Read the slack bus selection from the network, if present. |
| Slack Distribution | **Write Slack Bus** | Check box | Write the elected slack bus back to the network after computation. |
| Slack Distribution | **Countries To Balance** | Searchable country list | The countries whose injections balance the slack; none selected means all. |
| Voltage Controls | **Use Reactive Limits** | Check box | Enforce generator and static var compensator reactive power limits. |
| Transformer Voltage Control | **Transformers Voltage Control** | Check box | Enable ratio tap changer transformer voltage control. |
| Shunt Voltage Control | **Shunt Compensators Voltage Control** | Check box | Enable shunt compensator voltage control. |
| Phase Control | **Phase Shifters Regulation** | Check box | Enable phase shifter transformer active power/flow regulation. |
| Voltage Init | **Voltage Initialization Mode** | Choice: `UNIFORM_VALUES`, `DC_VALUES`, `PREVIOUS_VALUES`, `VOLTAGE_MAGNITUDE`, `FULL_VOLTAGE` | The initial voltages: the last two values compute them by a preprocessing step (Open Load Flow's voltage initialization override). |
| HVDC | **HVDC AC Emulation** | Check box | Simulate HVDC lines in AC emulation mode. |
| Performance | **Component Mode** | Choice: `MAIN_CONNECTED`, `ALL_CONNECTED`, `MAIN_SYNCHRONOUS` | Which connected or synchronous components of the network to compute. |
| Debug | **Debug Directory** | Text field | Directory where debug files are written; empty for none. |

They are documented in the PowSyBl documentation:

- [generic load flow parameters](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/loadflow/configuration.html);
- [Open Load Flow specific load flow parameters](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/loadflow/parameters.html).

## Import and export

The **Import** and **Export** buttons of the tab read and write the load flow parameters only, in the PowSyBl JSON
format of load flow parameters. An unreadable file is reported by an error notification and changes nothing.

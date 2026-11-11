# Network Import

The **Network Import** tab sets the parameters of each network import format, used by **Import** in the
[Networks](../network/networks.md) view.

![The Network Import tab](../../_generated/images/parameters-network-import.png)

The list on the left holds the formats of the import menu: **IIDM** first (XML, binary and JSON), then the other
formats in alphabetical order (CGMES, MATPOWER, PSS/E, UCTE...).

Each format's fields are the parameters PowSyBl declares for it: the field shows the parameter's description, and its
tooltip the parameter's name and default value. Depending on the parameter, the field is a check box, a choice box or a
text field (comma separated for a list). Clearing a field, or setting it back to the default, restores the default
value. A format without parameters shows "This format has no parameters."

The parameters of each format are documented in the
[PowSyBl grid exchange formats documentation](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/grid_exchange_formats/index.html).

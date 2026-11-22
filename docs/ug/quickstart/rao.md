# Remedial action optimization

A remedial action optimization (RAO), computed by
[PowSyBl OpenRAO](https://powsybl.readthedocs.io/projects/openrao/en/stable/), finds the remedial actions that best
relieve the constraints of a network. Its input is a CRAC: the contingencies, the critical network elements to monitor
(CNECs) and the remedial actions the RAO may use. This tutorial relieves the two overloads that the
[load flow tutorial](loadflow.md) found on SmallGrid, on lines 8-9 and 9-10, by redispatching two
generators: lowering one and raising the other.
It starts from the network loaded in the [first tutorial](load_network.md).

## Create a CRAC

1. In the main toolbar, click the small arrow of **Inputs**, then **CRAC**.
2. Click **New**, then **OK**.

![Creating an empty CRAC](../_generated/images/quickstart-rao-new-crac.png)

The new CRAC has no contingency, CNEC or remedial action yet: only its instants, the preventive one (before any
contingency), the outage one and a curative one. This tutorial only uses the preventive instant.

## Monitor the overloaded lines

Click **Add**, then **Flow CNECs…**.

![The Add menu](../_generated/images/quickstart-rao-add-menu.png)

A dialog lists the branches of the network. Type `8-9` in its search field and check line 8-9, then do the same for
`9-10`, and click **OK**.

![Picking the lines](../_generated/images/quickstart-rao-pick-lines.png)

A second dialog asks at which states to monitor them. The CRAC having no contingency, **Preventive** is the only one.
Click **OK**.

![The states to monitor the lines at](../_generated/images/quickstart-rao-cnec-states.png)

The **CNECs** tab now lists a CNEC for each line, with thresholds of ± its permanent current limit.

![The flow CNECs](../_generated/images/quickstart-rao-cnecs.png)

## Add redispatching actions

Generator Breed SM feeds lines 9-10 and 8-9; producing less there and more elsewhere relieves them.

1. Click **Add**, then **Injection Range Action…**. Type `Breed` in the search field, check **Breed SM**, then click
   **OK**. The range action is added, its form open on the right: the RAO may set the generator anywhere between its
   minimum and maximum active power, 300 and 600 MW.
2. In **Name**, type `Redispatching Breed` and press Enter.
3. At the bottom of the form, under **Usage Rules**, click **+**. A remedial action without usage rule is never used:
   the rule tells when the RAO may use it. Leave **At an instant** and **preventive**, and click **OK**.

![Adding a usage rule](../_generated/images/quickstart-rao-usage-rule.png)

![The range action](../_generated/images/quickstart-rao-range-action.png)

Do the same for **Sporn SM**, named `Redispatching Sporn`.

## Run the remedial action optimization

1. In the main toolbar, click **Run**.
2. Click **Remedial Action Optimization**.

The RAO runs with the parameters of the
[Remedial Action Optimization](../views/parameters/remedial_action_optimization.md) tab of the Parameters view. When it
ends, a notification offers **View results** and **View report**.

## Read the results

Click **View results**, or open **Results** > **Remedial Action Optimization Results**. The summary at the top says the
flows are secure after optimization. The **CNECs** tab shows the flows on both lines before and after the remedial
actions: the **Initial Margin**, negative, is the overload before optimization; scroll right to see the **Margin**
after it, now positive.

![The CNECs after optimization](../_generated/images/quickstart-rao-results.png)

The **Remedial Actions** tab lists what the RAO did: Breed SM down from 450 to 300 MW, Sporn SM up by about as much.

![The remedial actions](../_generated/images/quickstart-rao-results-actions.png)

The network itself is not changed. See [Remedial action optimization results](../views/results/rao_results.md), and
[CRAC](../views/inputs/crac.md) to import a CRAC from a file, generate one from the network, or add contingencies and
other remedial actions.

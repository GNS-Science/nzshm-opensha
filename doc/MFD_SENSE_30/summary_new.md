# Summary of MFD weighting sensitivity test

## 25 30 mins inversions performed Mar 15. 2021

Test permutations of mfd_equality and inequality weighting are used to define the Target MFD inversion
constraints. Inversions of 30 minutes were performed and MagRate curves generated.


## Observations/Questions

 * The other (non MFD) constraints applied in these inversions are:
     * **minimizationConstraintWt = 1000;** // "to minimize rates of rups below sectMinMag"
     * **slipRateConstraintWt_normalized = 1;**
     * **slipRateConstraintWt_unnormalized = 100;**
     * **slipRateWeighting = SlipRateConstraintWeightingType.BOTH**
 * with **eq:0; ineq:0** there are no MFD constraints applied so we're seeing the effect of above constraints
 * with **eq:>0; ineq: 0** we see target MFD fitting as expected
 * with **eq:0; ineq:>0** why does the solution look so GR ??

| | Inequality 0 | Inequality 1 | Inequality 10 | Inequality 100 | Inequality 1000 |
|-----|-----|-----|-----|-----|----|
| **Equality: 0** | <img src="eq0000_ineq0000/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0000_ineq0001/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0000_ineq0010/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0000_ineq0100/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0000_ineq1000/MAG_rates_log_fixed_yscale.png" width=300 > |
| **Equality: 1** | <img src="eq0001_ineq0000/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0001_ineq0001/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0001_ineq0010/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0001_ineq0100/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0001_ineq1000/MAG_rates_log_fixed_yscale.png" width=300 > |
| **Equality: 10** | <img src="eq0010_ineq0000/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0010_ineq0001/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0010_ineq0010/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0010_ineq0100/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0010_ineq1000/MAG_rates_log_fixed_yscale.png" width=300 > |
| **Equality: 100** | <img src="eq0100_ineq0000/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0100_ineq0001/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0100_ineq0010/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0100_ineq0100/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq0100_ineq1000/MAG_rates_log_fixed_yscale.png" width=300 > |
| **Equality: 1000** | <img src="eq1000_ineq0000/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq1000_ineq0001/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq1000_ineq0010/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq1000_ineq0100/MAG_rates_log_fixed_yscale.png" width=300 > | <img src="eq1000_ineq1000/MAG_rates_log_fixed_yscale.png" width=300 > |






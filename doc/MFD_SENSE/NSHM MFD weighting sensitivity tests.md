# Summary of MFD weighting sensitivity test

Test permuations of mfd_equality and inequality weighting as used to define the Tartet MFD inversion
constraints,


Short inversions of 10 minutes were performed and MagRate curves generated.

Note the default crustal weightings in UCERF3 were equality:10 vs in-equality:1000


```
    for eq_wt in [0, 10, 100, 1000]:
        for ineq_wt in [1000, 100, 10, 0]:

            new_folder = output_folder.joinpath("eq%04d_ineq%04d" % (eq_wt, ineq_wt))

            ##run an inversion
            solution_path = run_inversion(eq_wt, ineq_wt, new_folder, INVERSION_MINS)

            #run the report
            reporter.setName("eq%04d_ineq%04d" % (eq_wt, ineq_wt))\
                .setRuptureSetName(solution_path)\
                .setOutputDir(str(new_folder))\
                .generate()

    summary.close()
    print('Done!')
```

## Equality: 0

|  **Equality: 0** |
|-----|-----|-----|----|
| ![**Equality: 0. Inequality 1000**](eq0000_ineq1000/MAG_rates_log_fixed_yscale.png) | ![**Equality: 0. Inequality 100**](eq0000_ineq1000/MAG_rates_log_fixed_yscale.png) | ![**Equality: 0. Inequality 10**](eq0000_ineq1000/MAG_rates_log_fixed_yscale.png) | ![**Equality: 0. Inequality 0**](eq0000_ineq1000/MAG_rates_log_fixed_yscale.png) |


### Equality: 0. Inequality 1000

![Equality: 0. Inequality 1000](eq0000_ineq1000/MAG_rates_log_fixed_yscale.png)

### Equality: 0. Inequality 100

![Equality: 0. Inequality 1000](eq0000_ineq0100/MAG_rates_log_fixed_yscale.png)

### Equality: 0. Inequality 10

![Equality: 0. Inequality 1000](eq0000_ineq0010/MAG_rates_log_fixed_yscale.png)

### Equality: 0. Inequality 0

![Equality: 0. Inequality 1000](eq0000_ineq0000/MAG_rates_log_fixed_yscale.png)

## Equality: 10

### Equality: 10. Inequality 1000

!eq0000_ineq0000/MAG_rates_log_fixed_yscale.png

### Equality: 10. Inequality 100

### Equality: 10. Inequality 10

### Equality: 10. Inequality 0

## Equality: 100

### Equality: 100. Inequality 1000

### Equality: 100. Inequality 100

### Equality: 100. Inequality 10

### Equality: 100. Inequality 0

## Equality: 1000

### Equality: 1000. Inequality 1000

### Equality: 1000. Inequality 100

### Equality: 1000. Inequality 10

### Equality: 1000. Inequality 0


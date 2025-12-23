# rupture set modules

- inject custom fault model (no conflict)
- AveSlipModule
   - ave alip for rup
   - relies on scale
- SectSlipRates
   - slip rate and slip rate stdv for sections
   - relies on deformation model
   - relies on scale


# Config

- runzi has extra logic to map from its config to opensha config. Makes it hard
- still need to do deformationmodel

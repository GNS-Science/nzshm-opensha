# A Physically Consistent Approach for Calculating Magnitude of Joint Ruptures

For joint ruptures we need to know not just the magnitude of the entire rupture but also the magnitudes for the crustal and subduction (interface) portions of the rupture. This is because we need to calculate the slip for the rupture (which is found via the total rupture magnitude) and the partition magnitudes for use in MFD constraints. There are two separate MFD constraints for the crustal portion and the subduction portion of the rupture.

It's important that we calculate the magnitudes for each partition in a consistent manner -- the moment budget implied by the partition MFD assumes that the partition's magnitude is consistent with its seismic moment.

## Definitions
- $M_o$: seismic moment
- $M_w$: moment magnitude
- $X_p$: value of $X$ for partition p (e.g. $M_{os}$ is subduction moment)
- $A$: area of rupture
- $D$: mean slip of rupture
- $\mu$: shear modulus

## Equations

### Physical Laws
(1) $M_o = \mu D A$
(2) $M_w = (\log(M_o) - 9.05)/1.5$ (where $\log$ is taken to be $\log_{10}$)

### NZ-NSHM Model Assumptions
(3) $M_w = \log(A_c C_c + A_s C_s) = \log A^{*}$: $C_c$ and $C_s$ are the crustal and subduction scaling constants.

Here we introduce $A^*$ purely for compactness of notation.

(4) $D_c = D_s$: slip is the same for the crustal and interface portions of the rupture (NB: this could change in the future)

## Calculating Partition Magnitude from Seismic Moment
Combining equation 1 and 2 and using the seismic moment for only partition $p$:

(5) $M_w^p = (\log(\mu A_p D) - 9.05) / 1.5$

From equation 1:

(6) $D = M_o / (\mu A)$ where $M_o$ and $A$ are for the entire rupture.

Substituting equations 2 (rearranged to be $M_o$ as a function of $M_w$) and 3 into equation 6 

(7) $D = \exp_10{1.5 \log A^* + 9.05} / (\mu A) = 10^{9.05} A^{* 1.5} / (\mu A)$

And now we can find $M_w^p$ as a function of the known quantities $A$, $A_c$, and $A_s$ substituting equation 7 into equation 5

$M_w^p = \frac{1}{1.5} (\log(\frac{\mu A_p 10^{9.05} A^{*1.5}}{\mu A}) - 9.05)$

$M_w^p = \frac{1}{1.5}(9.05 + \log \frac{A_p}{A} + 1.5 \log(A_c C_c + A_s C_s) - 9.05)$

$M_w^p = \frac{1}{1.5}\log \frac{A_p}{A} + \log(A_c C_c + A_s C_s)$

which, in general, does not give us the same result as using $M_w^p = \log(A_p C_p)$.

## Conclusion
When calculating the magnitude of the entire rupture from the area (the only quantity we know for a rupture set) use the magnitude-area scaling relationship (equation 3). When calculating the magnitude for each partition, calculate the seismic moment using equation 1 and the moment magnitude using equation 2. This will always be consistent with whatever assumptions we make (scaling relationship, slip apportioning, etc.). To ensure consistency in the face of future changes to joint rupture assumptions, do not use the final equation as this is only provided as proof for the current case.
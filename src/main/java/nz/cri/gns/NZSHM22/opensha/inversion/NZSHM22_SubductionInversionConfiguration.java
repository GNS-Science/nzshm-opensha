package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.InversionModels;

/**
 * This represents all of the inversion configuration parameters specific to an individual model on
 * the NZSHM22 logic tree. Parameters can be fetched for a given logic tree branch with the <code>
 * forModel(...)</code> method.
 *
 * <p>based on scratch.UCERF3.inversion.UCERF3InversionConfiguration
 *
 * @author chrisbc
 */
public class NZSHM22_SubductionInversionConfiguration extends AbstractInversionConfiguration {

    /** */
    public NZSHM22_SubductionInversionConfiguration() {}

    /**
     * This generates an inversion configuration for the given inversion model and rupture set
     *
     * @param model
     * @param rupSet
     * @param mfdUncertaintyWeightedConstraintScalar TODO
     * @param totalRateM5
     * @param bValue
     * @param mfdTransitionMag magnitude to switch from MFD equality to MFD inequality
     * @return
     */
    public static NZSHM22_SubductionInversionConfiguration forModel(
            NZSHM22_SubductionInversionRunner runner,
            InversionModels model,
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            double[] initialSolution,
            double mfdUncertaintyWeightedConstraintWt,
            double mfdUncertaintyWeightedConstraintPower,
            double mfdUncertaintyWeightedConstraintScalar,
            double totalRateM5,
            double bValue,
            double mfdTransitionMag,
            double mfdMinMag) {

        // setup MFD constraints
        NZSHM22_SubductionInversionTargetMFDs inversionMFDs =
                new NZSHM22_SubductionInversionTargetMFDs(
                        rupSet,
                        totalRateM5,
                        bValue,
                        mfdTransitionMag,
                        mfdMinMag,
                        mfdUncertaintyWeightedConstraintWt,
                        mfdUncertaintyWeightedConstraintPower,
                        mfdUncertaintyWeightedConstraintScalar);
        rupSet.setInversionTargetMFDs(inversionMFDs);

        double[] minimumRuptureRateBasis = null;

        if (model == InversionModels.CHAR_CONSTRAINED) {

            List<IncrementalMagFreqDist> mfdConstraints = new ArrayList<>();
            mfdConstraints.addAll(inversionMFDs.getMfdEqIneqConstraints());
            mfdConstraints.addAll(inversionMFDs.getMfdUncertaintyConstraints());
            IncrementalMagFreqDist targetOnFaultMFD = inversionMFDs.getTotalOnFaultSupraSeisMFD();

            //            minimumRuptureRateBasis =
            //                    adjustStartingModel(
            //                            UCERF3InversionConfiguration.getSmoothStartingSolution(
            //                                    rupSet, targetOnFaultMFD),
            //                            mfdConstraints,
            //                            rupSet,
            //                            true);
        }

        return (NZSHM22_SubductionInversionConfiguration)
                new NZSHM22_SubductionInversionConfiguration()
                        .setInversionTargetMfds(inversionMFDs)
                        .initialiseFromRunner(
                                runner,
                                model,
                                inversionMFDs.getMfdEqIneqConstraints(),
                                inversionMFDs.getMfdUncertaintyConstraints(),
                                initialSolution,
                                minimumRuptureRateBasis);
    }

    /**
     * CBC: cloned this from UCERF3InversionConfiguration just for testing with some water levels.
     * It needs work to decided a) if its wanted and b) how it should work!! @MCG
     *
     * <p>This method adjusts the starting model to ensure that for each MFD inequality constraint
     * magnitude-bin, the starting model is below the MFD. If adjustOnlyIfOverMFD = false, it will
     * adjust the starting model so that it's MFD equals the MFD constraint. It will uniformly
     * reduce the rates of ruptures in any magnitude bins that need adjusting.
     */
    private static double[] adjustStartingModel(
            double[] initialRupModel,
            List<IncrementalMagFreqDist> mfdInequalityConstraints,
            FaultSystemRupSet rupSet,
            boolean adjustOnlyIfOverMFD) {

        double[] rupMeanMag = rupSet.getMagForAllRups();

        for (int i = 0; i < mfdInequalityConstraints.size(); i++) {
            double[] fractRupsInside =
                    rupSet.getFractRupsInsideRegion(
                            mfdInequalityConstraints.get(i).getRegion(), false);
            IncrementalMagFreqDist targetMagFreqDist = mfdInequalityConstraints.get(i);
            IncrementalMagFreqDist startingModelMagFreqDist =
                    new IncrementalMagFreqDist(
                            targetMagFreqDist.getMinX(),
                            targetMagFreqDist.size(),
                            targetMagFreqDist.getDelta());
            startingModelMagFreqDist.setTolerance(0.1);

            // Find the starting model MFD
            for (int rup = 0; rup < rupSet.getNumRuptures(); rup++) {
                double mag = rupMeanMag[rup];
                double fractRupInside = fractRupsInside[rup];
                if (fractRupInside > 0)
                    if (mag < 8.5) // b/c the mfdInequalityConstraints only go to M8.5!
                    startingModelMagFreqDist.add(mag, fractRupInside * initialRupModel[rup]);
            }

            // Find the amount to adjust starting model MFD to be below or equal to Target MFD
            IncrementalMagFreqDist adjustmentRatio =
                    new IncrementalMagFreqDist(
                            targetMagFreqDist.getMinX(),
                            targetMagFreqDist.size(),
                            targetMagFreqDist.getDelta());
            for (double m = targetMagFreqDist.getMinX();
                    m <= targetMagFreqDist.getMaxX();
                    m += targetMagFreqDist.getDelta()) {
                if (adjustOnlyIfOverMFD == false)
                    adjustmentRatio.set(
                            m,
                            targetMagFreqDist.getClosestYtoX(m)
                                    / startingModelMagFreqDist.getClosestYtoX(m));
                else {
                    if (startingModelMagFreqDist.getClosestYtoX(m)
                            > targetMagFreqDist.getClosestYtoX(m))
                        adjustmentRatio.set(
                                m,
                                targetMagFreqDist.getClosestYtoX(m)
                                        / startingModelMagFreqDist.getClosestYtoX(m));
                    else adjustmentRatio.set(m, 1.0);
                }
            }

            // Adjust initial model rates
            for (int rup = 0; rup < rupSet.getNumRuptures(); rup++) {
                double mag = rupMeanMag[rup];
                if (!Double.isNaN(adjustmentRatio.getClosestYtoX(mag))
                        && !Double.isInfinite(adjustmentRatio.getClosestYtoX(mag)))
                    initialRupModel[rup] =
                            initialRupModel[rup] * adjustmentRatio.getClosestYtoX(mag);
            }
        }

        /*		// OPTIONAL: Adjust rates of largest rups to equal global target MFD
        IncrementalMagFreqDist targetMagFreqDist = UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.ALL_CA_1850).getMagFreqDist();
        IncrementalMagFreqDist startingModelMagFreqDist = new IncrementalMagFreqDist(targetMagFreqDist.getMinX(), targetMagFreqDist.getNum(), targetMagFreqDist.getDelta());
        startingModelMagFreqDist.setTolerance(0.1);
        for(int rup=0; rup<rupSet.getNumRuptures(); rup++) {
        	double mag = rupMeanMag[rup];
        	if (mag<8.5)
        		startingModelMagFreqDist.add(mag, initialRupModel[rup]);
        }
        IncrementalMagFreqDist adjustmentRatio = new IncrementalMagFreqDist(targetMagFreqDist.getMinX(), targetMagFreqDist.getNum(), targetMagFreqDist.getDelta());
        for (double m=targetMagFreqDist.getMinX(); m<=targetMagFreqDist.getMaxX(); m+= targetMagFreqDist.getDelta()) {
        	if (m>8.0)	adjustmentRatio.set(m, targetMagFreqDist.getClosestY(m) / startingModelMagFreqDist.getClosestY(m));
        	else adjustmentRatio.set(m, 1.0);
        }
        for(int rup=0; rup<rupSet.getNumRuptures(); rup++) {
        	double mag = rupMeanMag[rup];
        	if (!Double.isNaN(adjustmentRatio.getClosestY(mag)) && !Double.isInfinite(adjustmentRatio.getClosestY(mag)))
        		initialRupModel[rup] = initialRupModel[rup] * adjustmentRatio.getClosestY(mag);
        }	*/

        return initialRupModel;
    }
}

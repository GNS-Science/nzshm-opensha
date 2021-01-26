package nz.cri.gns.NSHM.opensha.inversion;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.NSHMSlipEnabledRuptureSet;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.*;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;


/**
 * Runs the standard NSHM inversion on a rupture set.
 */
public class NSHMInversionRunner {

    protected long inversionMins = 1;
    protected long syncInterval = 10;
    protected int numThreads = Runtime.getRuntime().availableProcessors();
    protected NSHMSlipEnabledRuptureSet rupSet = null;
    protected List<InversionConstraint> constraints = new ArrayList<>();
    protected List<CompletionCriteria> completionCriterias = new ArrayList<>();
    private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;

    private CompletionCriteria completionCriteria;
    private ThreadedSimulatedAnnealing tsa;
    private double[] initialState;
    
    /*
     * MFD constraint default settings
     */
    protected double totalRateM5 = 5d;
    protected double bValue = 1d;
    protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
    protected int mfdNum = 40;
    protected double mfdMin = 5.05d;
    protected double mfdMax = 8.95;
    
    protected double mfdEqualityConstraintWt = 10;
    protected double mfdInequalityConstraintWt = 1000;

    
    /* 
     * Sliprate constraint default settings
     */  
    // If normalized, slip rate misfit is % difference for each section (recommended since it helps fit slow-moving faults).
    // If unnormalized, misfit is absolute difference.
    // BOTH includes both normalized and unnormalized constraints.
    protected UCERF3InversionConfiguration.SlipRateConstraintWeightingType slipRateWeighting = UCERF3InversionConfiguration.SlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)    
    // For SlipRateConstraintWeightingType.NORMALIZED (also used for SlipRateConstraintWeightingType.BOTH) -- NOT USED if UNNORMALIZED!
    protected double slipRateConstraintWt_normalized = 1;
    // For SlipRateConstraintWeightingType.UNNORMALIZED (also used for SlipRateConstraintWeightingType.BOTH) -- NOT USED if NORMALIZED!
    protected double slipRateConstraintWt_unnormalized = 100;
    
    
    /**
     * Creates a new NSHMInversionRunner with defaults.
     */
    public NSHMInversionRunner() {
    }

    public Region getRegionNZ() {
    	// NZ as used for scecVDO graticule
		//    			upper-latitude = -30
		//    			lower-latitude = -50
		//    			right-longitude = 185
		//    			left-longitude = 165
		Location nw = new Location(-30.0, 165.0);
		Location se = new Location(-50.0, 185.0);
		return new Region(nw, se);
    }
    
    
    public Region getRegionTVZ() {
    	//Taupo Volcanic Zone points from MattG
    	LocationList locs = new LocationList();
		locs.add(new Location(-36.17, 177.25));
		locs.add(new Location(-36.17, 178.14));
		locs.add(new Location(-37.53, 177.31));
		locs.add(new Location(-39.78, 175.38));
		locs.add(new Location(-39.78, 174.97));
		locs.add(new Location(-39.22, 175.29));
		locs.add(new Location(-36.17, 177.25));
		return new Region(locs, null);
    }
    
    /**
     * Sets how many minutes the inversion runs for.
     * Default is 1 minute.
     * @param inversionMinutes the duration of the inversion in minutes.
     * @return this runner.
     */
    public NSHMInversionRunner setInversionMinutes(long inversionMinutes) {
        this.inversionMins = inversionMinutes;
        return this;
    }

    /**
     * @param energyDelta
     * @param energyPercentDelta
     * @param lookBackMins
     * @return
     */
    public NSHMInversionRunner setEnergyChangeCompletionCriteria(double energyDelta, 
    		double energyPercentDelta, double lookBackMins) {
    	this.energyChangeCompletionCriteria = new EnergyChangeCompletionCriteria(energyDelta, 
    			energyPercentDelta, lookBackMins);
    	return this;
    }
    
    /**
     * Sets the length of time between syncs in seconds.
     * Default is 10 seconds.
     * @param syncInterval the interval in seconds.
     * @return this runner.
     */
    public NSHMInversionRunner setSyncInterval(long syncInterval) {
        this.syncInterval = syncInterval;
        return this;
    }

    /**
     * Sets how many threads the inversion will try to use.
     * Default is all available processors / cores.
     * @param numThreads the number of threads.
     * @return this runner.
     */
    public NSHMInversionRunner setNumThreads(int numThreads) {
        this.numThreads = numThreads;
        return this;
    }
  
    /**
     * Sets the FaultModel file f
     *
     * @param ruptureSetFileName the rupture file name
     * @return this builder
     * @throws DocumentException 
     * @throws IOException 
     */
    public NSHMInversionRunner setRuptureSetFile(String ruptureSetFileName) throws IOException, DocumentException {
    	File rupSetFile = new File(ruptureSetFileName);
    	this.setRuptureSetFile(rupSetFile);
        return this;
    }

    /**
     * Sets the FaultModel file
     *
     * @param ruptureSetFile the rupture file
     * @return this builder
     * @throws DocumentException 
     * @throws IOException 
     */
    public NSHMInversionRunner setRuptureSetFile(File ruptureSetFile) throws IOException, DocumentException {
        this.rupSet = loadRupSet(ruptureSetFile);
        return this;
    }    
    
    /**
     * Sets GutenbergRichterMFD arguments
     * @param totalRateM5 the number of  M>=5's per year. TODO: ref David Rhodes/Chris Roland? [KKS, CBC]
     * @param bValue
     * @param mfdTransitionMag magnitude to switch from MFD equality to MFD inequality TODO: how to validate this number for NZ? (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
     * @param mfdNum
     * @param mfdMin
     * @param mfdMax
     * @return
     */
    public NSHMInversionRunner setGutenbergRichterMFD(double totalRateM5, double bValue, 
    		double mfdTransitionMag, int mfdNum, double mfdMin, double mfdMax ) {
        this.totalRateM5 = totalRateM5; 
        this.bValue = bValue;
        this.mfdTransitionMag = mfdTransitionMag;      
        this.mfdNum = mfdNum;
        this.mfdMin = mfdMin;
        this.mfdMax = mfdMax;
        return this;
    }
    
    /**
     * @param mfdEqualityConstraintWt
     * @param mfdInequalityConstraintWt
     * @return
     */
    public NSHMInversionRunner setGutenbergRichterMFDWeights(double mfdEqualityConstraintWt, 
    		double mfdInequalityConstraintWt) {
    	this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
    	this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
    	return this;
    }
    
    /**
     * If normalized, slip rate misfit is % difference for each section (recommended since it helps fit slow-moving faults).
     * If unnormalized, misfit is absolute difference.
     * BOTH includes both normalized and unnormalized constraints.
     * 
     * @param weightingType a value fromUCERF3InversionConfiguration.SlipRateConstraintWeightingType 
     * @param normalizedWt
     * @param unnormalizedWt
     * @return
     */
    public NSHMInversionRunner setSlipRateConstraint(UCERF3InversionConfiguration.SlipRateConstraintWeightingType weightingType, 
    		double normalizedWt, double unnormalizedWt) {
    	this.slipRateWeighting = weightingType;
    	this.slipRateConstraintWt_normalized = normalizedWt;
    	this.slipRateConstraintWt_unnormalized = unnormalizedWt;
    	return this;
    }
    
    public String completionCriteriaMetrics() {
    	String info = "";
		ProgressTrackingCompletionCriteria pComp = (ProgressTrackingCompletionCriteria)completionCriteria;
		long numPerturbs = pComp.getPerturbs().get(pComp.getPerturbs().size()-1);
		int numRups = initialState.length;
		info += "\nAvg Perturbs Per Rup: "+numPerturbs+"/"+numRups+" = "
		+((double)numPerturbs/(double)numRups);
		int rupsPerturbed = 0;
		double[] solution_no_min_rates = tsa.getBestSolution();
		int numAboveWaterlevel =  0;
		for (int i=0; i<numRups; i++) {
			if ((float)solution_no_min_rates[i] != (float)initialState[i])
				rupsPerturbed++;
			if (solution_no_min_rates[i] > 0)
				numAboveWaterlevel++;
		}
		info += "\nNum rups actually perturbed: "+rupsPerturbed+"/"+numRups+" ("
		+(float)(100d*((double)rupsPerturbed/(double)numRups))+" %)";
		info += "\nAvg Perturbs Per Perturbed Rup: "+numPerturbs+"/"+rupsPerturbed+" = "
		+((double)numPerturbs/(double)rupsPerturbed);
		info += "\nNum rups above waterlevel: "+numAboveWaterlevel+"/"+numRups+" ("
		+(float)(100d*((double)numAboveWaterlevel/(double)numRups))+" %)";
		info += "\n";
		return info;
    }
    
    
    @SuppressWarnings("unchecked")
    protected NSHMSlipEnabledRuptureSet loadRupSet(File file) throws IOException, DocumentException {
        FaultSystemRupSet fsRupSet = FaultSystemIO.loadRupSet(file);
        return new NSHMSlipEnabledRuptureSet(
                fsRupSet.getClusterRuptures(),
                (List<FaultSection>) fsRupSet.getFaultSectionDataList(),
                ScalingRelationships.SHAW_2009_MOD,
                SlipAlongRuptureModels.UNIFORM);
    }

    /**
     * Runs the inversion on the specified rupture set.
     * @return the FaultSystemSolution.
     * @throws IOException
     * @throws DocumentException
     */
    public FaultSystemSolution runInversion() throws IOException, DocumentException {

    	/*
         * Slip rate constraints
         */
        constraints.add(new SlipRateInversionConstraint(this.slipRateConstraintWt_normalized, 
        		this.slipRateConstraintWt_unnormalized,
                this.slipRateWeighting, rupSet, rupSet.getSlipRateForAllSections()));

        //Experiment - define some regions 
        Region regionSansTVZ = getRegionNZ();  // the same rectangle we have for the scecVDO NZ graticule
        Region regionTVZ = getRegionTVZ();     // from Matts geometry as used for the sansTVZ crustal ruptures
        regionSansTVZ.addInterior(regionTVZ); // remove a TVZ-shaped interior from NZ
        
        //configure GR, this will use defaults unless user calls setGutenbergRichterMFD() to override 	
        GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(bValue, totalRateM5, mfdMin, mfdMax, mfdNum);
        int transitionIndex = mfd.getClosestXIndex(mfdTransitionMag);
        // snap it to the discretization if it wasn't already
        mfdTransitionMag = mfd.getX(transitionIndex);
        Preconditions.checkState(transitionIndex >= 0);       
        
        //GR Equality
        GutenbergRichterMagFreqDist equalityMFDA = new GutenbergRichterMagFreqDist(
                bValue, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);
        
        //and a different bvalue for TVZ equality
        GutenbergRichterMagFreqDist equalityMFDB = new GutenbergRichterMagFreqDist(
                0.75, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);   
        
        MFD_InversionConstraint equalityConstrA = new MFD_InversionConstraint(equalityMFDA, regionSansTVZ);
        MFD_InversionConstraint equalityConstrB = new MFD_InversionConstraint(equalityMFDB, regionTVZ);
        
        constraints.add(new MFDEqualityInversionConstraint(rupSet, mfdEqualityConstraintWt,
                Lists.newArrayList(equalityConstrA, equalityConstrB), null));

        //GR Inequality
        GutenbergRichterMagFreqDist inequalityMFDA = new GutenbergRichterMagFreqDist(
                bValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFDA.size());

        //and a different bvalue for TVZ Inequality
        GutenbergRichterMagFreqDist inequalityMFDB = new GutenbergRichterMagFreqDist(
        		0.75, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFDB.size());
        
        MFD_InversionConstraint inequalityConstrA = new MFD_InversionConstraint(inequalityMFDA, regionSansTVZ);
        MFD_InversionConstraint inequalityConstrB = new MFD_InversionConstraint(inequalityMFDB, regionTVZ);
        
        constraints.add(new MFDInequalityInversionConstraint(rupSet, mfdInequalityConstraintWt,
                Lists.newArrayList(inequalityConstrA, inequalityConstrB)));

        // weight of entropy-maximization constraint (not used in UCERF3)
        double smoothnessWt = 0;

        /*
         * Build inversion inputs
         */
        InversionInputGenerator inputGen = new InversionInputGenerator(rupSet, constraints);

        inputGen.generateInputs(true);
        // column compress it for fast annealing
        inputGen.columnCompress();

        // inversion completion criteria (how long it will run)
        // = TimeCompletionCriteria.getInMinutes(inversionMins);
        this.completionCriterias.add(TimeCompletionCriteria.getInMinutes(inversionMins));
        if (!(this.energyChangeCompletionCriteria == null))
        	this.completionCriterias.add(this.energyChangeCompletionCriteria);

        completionCriteria = new CompoundCompletionCriteria(this.completionCriterias);
        
        // Bring up window to track progress
        // criteria = new ProgressTrackingCompletionCriteria(criteria, progressReport, 0.1d);        
		completionCriteria = new ProgressTrackingCompletionCriteria(completionCriteria);
		
        // this is the "sub completion criteria" - the amount of time (or iterations) between synchronization
        CompletionCriteria subCompletionCriteria = TimeCompletionCriteria.getInSeconds(syncInterval); // 1 second;

		initialState = inputGen.getInitialSolution();
		
        tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
        		initialState, smoothnessWt, inputGen.getA_ineq(), inputGen.getD_ineq(),
                inputGen.getWaterLevelRates(), numThreads, subCompletionCriteria);
        tsa.setConstraintRanges(inputGen.getConstraintRowRanges());

        
        //From CLI metadata Analysis
        initialState = Arrays.copyOf(initialState, initialState.length);
        
        tsa.iterate(completionCriteria);

        // now assemble the solution
        double[] solution_raw = tsa.getBestSolution();

        // adjust for minimum rates if applicable
        double[] solution_adjusted = inputGen.adjustSolutionForWaterLevel(solution_raw);

        Map<ConstraintRange, Double> energies = tsa.getEnergies();
        if (energies != null) {
            System.out.println("Final energies:");
            for (ConstraintRange range : energies.keySet())
                System.out.println("\t" + range.name + ": " + energies.get(range).floatValue());
        }

        return new FaultSystemSolution(rupSet, solution_adjusted);
    }
}

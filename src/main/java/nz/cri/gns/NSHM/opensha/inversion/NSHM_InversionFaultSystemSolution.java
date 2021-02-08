package nz.cri.gns.NSHM.opensha.inversion;

//import java.awt.Color;
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
import java.util.Map;
//
//import org.dom4j.DocumentException;
////import org.jfree.chart.plot.DatasetRenderingOrder;
//import org.opensha.commons.calc.FaultMomentCalc;
//import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
//import org.opensha.commons.data.function.DiscretizedFunc;
//import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
//import org.opensha.commons.geo.Region;
//import org.opensha.commons.geo.RegionUtils;
//import org.opensha.commons.gui.plot.HeadlessGraphPanel;
//import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
//import org.opensha.commons.gui.plot.PlotLineType;
//import org.opensha.commons.gui.plot.PlotSpec;
//import org.opensha.commons.util.ClassUtils;
//import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
//import org.opensha.sha.faultSurface.FaultSection;
//import org.opensha.sha.faultSurface.FaultTrace;
//import org.opensha.sha.gui.infoTools.CalcProgressBar;
//import org.opensha.commons.gui.plot.GraphWindow;
//import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
//import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
//import org.opensha.sha.magdist.IncrementalMagFreqDist;
//import org.opensha.sha.magdist.SummedMagFreqDist;

//import scratch.UCERF3.FaultSystemRupSet;
//import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipEnabledSolution;
//import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
//import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
//import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
//import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
//import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
//import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
//import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
//import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
//import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
//port scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.logicTree.LogicTreeBranch;
//import scratch.UCERF3.logicTree.LogicTreeBranchNode;
//import scratch.UCERF3.utils.FaultSystemIO;
//import scratch.UCERF3.utils.MFD_InversionConstraint;
//import scratch.UCERF3.utils.OLD_UCERF3_MFD_ConstraintFetcher;
//import scratch.UCERF3.utils.SectionMFD_constraint;
//import scratch.UCERF3.utils.OLD_UCERF3_MFD_ConstraintFetcher.TimeAndRegion;
//import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
//import scratch.UCERF3.utils.RELM_RegionUtils;
//import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;
//
//import com.google.common.base.Preconditions;
//import com.google.common.base.Splitter;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Maps;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMSlipEnabledRuptureSet;

/**
 * This is a SlipEnabledSolution that also contains parameters used in the NSHM.NZ Inversion
 * 
 * @author chrisbc
 *
 */
@SuppressWarnings("serial")
public class NSHM_InversionFaultSystemSolution extends SlipEnabledSolution {
	
	//private InversionFaultSystemRupSet rupSet;
	private NSHMSlipEnabledRuptureSet rupSet;
	private InversionModels invModel;
	private LogicTreeBranch branch;
	
	/**
	 * Inversion constraint weights and such. Note that this won't include the initial rup model or
	 * target MFDs and cannot be used as input to InversionInputGenerator.
	 */
	private UCERF3InversionConfiguration inversionConfiguration; 
	
	private Map<String, Double> energies;
	private Map<String, Double> misfits;
	
	/**
	 * Can be used on the fly for when InversionConfiguration/energies are not available/relevant
	 * 
	 * @param rupSet
	 * @param rates
	 */
	public NSHM_InversionFaultSystemSolution(NSHMSlipEnabledRuptureSet rupSet, double[] rates) {
		this(rupSet, rates, null, null);
	}
	
	/**
	 * Default constructor, for post inversion or file loading.
	 * 
	 * @param rupSet
	 * @param rates
	 * @param config can be null
	 * @param energies can be null
	 */
	public NSHM_InversionFaultSystemSolution(NSHMSlipEnabledRuptureSet rupSet, double[] rates,
			UCERF3InversionConfiguration config, Map<String, Double> energies) {
		super();	
		init(rupSet, rates, config, energies);
	}
	
	protected void init(NSHMSlipEnabledRuptureSet rupSet, double[] rates,
			UCERF3InversionConfiguration config, Map<String, Double> energies) {
		super.init(rupSet, rates, rupSet.getInfoString(), null);
		this.rupSet = rupSet;
		
		this.branch = null;   //rupSet.getLogicTreeBranch();
		this.invModel = null; //branch.getValue(InversionModels.class);
		
		// these can all be null
		this.inversionConfiguration = config;
		this.energies = energies;
	}	
		
	@Override
	public NSHMSlipEnabledRuptureSet getRupSet() {
		return rupSet;
	}

}
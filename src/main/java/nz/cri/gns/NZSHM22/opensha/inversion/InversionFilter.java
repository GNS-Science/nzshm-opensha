package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.*;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InversionFilter {

    public static int longestRupture(FaultSystemRupSet rupSet, String parentName){
        double length = 0;
        int longestRupture = -1;
        for(int r = 0; r < rupSet.getNumRuptures(); r++){
            if(matchesName(rupSet, r, parentName) && rupSet.getLengthForRup(r) > length && rupSet.getMagForRup(r) > 0){
                longestRupture = r;
                length = rupSet.getLengthForRup(r);
            }
        }
        return longestRupture;
    }

    public static boolean matchesName(FaultSystemRupSet rupSet, int ruptureId, String name){
        boolean result = false;
        for(FaultSection section : rupSet.getFaultSectionDataForRupture(ruptureId)){
            if(section.getSectionName().contains(name)){
                result = true;
            }
        }
        return result;
    }

    public static void copyModules(ModuleContainer<OpenSHA_Module> source,
                                   ModuleContainer<OpenSHA_Module> target,
                                   Class<? extends OpenSHA_Module>... modules) {
        for (Class<? extends OpenSHA_Module> module : modules) {
            OpenSHA_Module instance = source.getModule(module);
            if (instance != null) {
                target.addModule(instance);
            }
        }
    }

    public static FaultSystemSolution filter(FaultSystemSolution originalSolution, int rupture, Double rateOverwrite) throws IOException {
        FaultSystemRupSet original = originalSolution.getRupSet();
        List<List<Integer>> filteredRups = new ArrayList<>();
        filteredRups.add(original.getSectionsIndicesForRup(rupture));

        FaultSystemRupSet rupSet = FaultSystemRupSet.builder(original.getFaultSectionDataList(), filteredRups)
                .forScalingRelationship(original.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_ScalingRelationshipNode.class))
                .build();

        rupSet.addModule(ClusterRuptures.singleStranged(rupSet));

        copyModules(original,
                rupSet,
                BuildInfoModule.class,
                PolygonFaultGridAssociations.class,
                SectSlipRates.class,
                LogicTreeBranch.class,
                TvzDomainSections.class,
                ModSectMinMags.class,
                PaleoseismicConstraintData.class);

        InversionTargetMFDs targetMFDs = original.getModule(InversionTargetMFDs.class);
        if (targetMFDs != null) {
            targetMFDs.setParent(rupSet);
            rupSet.addModule(targetMFDs);
        }

        if (original.getModule(AveSlipModule.class) != null) {
            double[] aveSlip = new double[1];
            aveSlip[0] = original.getModule(AveSlipModule.class).getAveSlip(rupture);
            AveSlipModule aveSlipModule = new AveSlipModule.Precomputed(rupSet, aveSlip);
            rupSet.addModule(aveSlipModule);
        }


        double[] rates = new double[1];
        if (rateOverwrite != null) {
            rates[0] = rateOverwrite;
        } else {
            rates[0] = originalSolution.getRateForRup(rupture);
        }

        return new FaultSystemSolution(rupSet, rates);
    }

    public static void main(String[] args) throws IOException {
        String solutionFile = "C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6MTAwMDQ4.zip";

        FaultSystemSolution old = FaultSystemSolution.load(new File(solutionFile));
        int rupture = longestRupture(old.getRupSet(), "Wellington Hutt");
        System.out.println(rupture);
        FaultSystemSolution newSolution = filter(old, rupture, null);
        newSolution.write(new File("filterd.zip"));
    }
}

package nz.cri.gns.NSHM.util;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.*;

import com.google.common.base.Preconditions;


public class NSHM_InversionRateDiagnosticsPlot extends RupSetDiagnosticsPageGen {

	public static void main(String[] args) throws IOException, DocumentException {
		System.setProperty("java.awt.headless", "true");
		create(args).generatePage();
		System.out.println("Done!");
	}

	public static NSHM_InversionRateDiagnosticsPlot create(String[] args) throws IOException, DocumentException {
		Options options = createOptions();

		CommandLineParser parser = new DefaultParser();

		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
			System.out.println("args " + args);
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(RupSetDiagnosticsPageGen.class), options, true);
			System.exit(2);
		}
		return new NSHM_InversionRateDiagnosticsPlot(cmd);
	}

	public NSHM_InversionRateDiagnosticsPlot(CommandLine cmd) throws IOException, DocumentException {
		super(cmd);
	};

	@Override
	public void generatePage() throws IOException { // throws IOException

		// just plot the mag-rate curves for now
		for (HistScalar scalar : HistScalar.values()) {
			if (scalar != HistScalar.MAG)
				continue;
			generateScalarLines(scalar, getOutputDir());
		}

//		if (distAzCacheFile != null && (numAzCached < distAzCalc.getNumCachedAzimuths()
//				|| numDistCached < distAzCalc.getNumCachedDistances())) {
//			System.out.println("Writing dist/az cache to " + distAzCacheFile.getAbsolutePath());
//			distAzCalc.writeCacheFile(distAzCacheFile);
//		}
	}

	private void generateScalarLines(HistScalar scalar, File resourcesDir) throws IOException {

		List<HistScalarValues> inputScalarVals = new ArrayList<>();

		HistScalarValues inputScalars = new HistScalarValues(scalar, getInputRupSet(), getInputSol(), getInputRups(), getDistAzCalc());
		inputScalarVals.add(inputScalars);

		// TODO: fix
		///getSummary().primaryMeta.scalars.add(new ScalarRange(inputScalars));
		
		HistScalarValues compScalars = null;

		try {
			Range yRange = null;
			plotRuptureHistogram(getOutputDir(), "MAG_rates_log", inputScalars, compScalars, getInputUniques(), getMainColor(), true,
					true, yRange);
			yRange = new Range(Math.pow(10, -6), Math.pow(10, -1));

			plotRuptureHistogram(getOutputDir(), "MAG_rates_log_fixed_yscale", inputScalars, compScalars, getInputUniques(),
					getMainColor(), true, true, yRange);

		} catch (IllegalStateException err) {
			System.out.println("Exception caught in Catch block: " + err.getLocalizedMessage());
		}
		return;
	}

	public File plotRuptureHistogram(File outputDir, String prefix, HistScalarValues scalarVals,
			HistScalarValues compScalarVals, HashSet<UniqueRupture> compUniques, Color color, boolean logY,
			boolean rateWeighted, Range yRange) throws IOException {
		List<Integer> includeIndexes = new ArrayList<>();
		for (int r = 0; r < scalarVals.getRupSet().getNumRuptures(); r++)
			includeIndexes.add(r);
		return plotRuptureHistogram(outputDir, prefix, scalarVals, includeIndexes, compScalarVals, compUniques, color,
				logY, rateWeighted, yRange);
	}

	public File plotRuptureHistogram(File outputDir, String prefix, HistScalarValues scalarVals,
			Collection<Integer> includeIndexes, HistScalarValues compScalarVals, HashSet<UniqueRupture> compUniques,
			Color color, boolean logY, boolean rateWeighted, Range yRange) throws IOException {

		List<Integer> indexesList = includeIndexes instanceof List<?> ? (List<Integer>) includeIndexes
				: new ArrayList<>(includeIndexes);
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (int r : indexesList)
			track.addValue(scalarVals.getValues().get(r));
//		if (compScalarVals != null) {
//			// used only for bounds
//			for (double scalar : compScalarVals.values)
//				track.addValue(scalar);
//		}

		HistScalar histScalar = scalarVals.getScalar();
		HistogramFunction hist = histScalar.getHistogram(track);
		boolean logX = histScalar.isLogX();

		Range xRange = null;
		if (logX)
			xRange = new Range(Math.pow(10, hist.getMinX() - 0.5 * hist.getDelta()),
					Math.pow(10, hist.getMaxX() + 0.5 * hist.getDelta()));
		else
			xRange = new Range(hist.getMinX() - 0.5 * hist.getDelta(), hist.getMaxX() + 0.5 * hist.getDelta());

//		HistogramFunction commonHist = null;
//		if (compUniques != null)
//			commonHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
//		HistogramFunction compHist = null;
//		if (compScalarVals != null && (!rateWeighted || compScalarVals.sol != null)) {
//			compHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
//			for (int i = 0; i < compScalarVals.values.size(); i++) {
//				double scalar = compScalarVals.values.get(i);
//				double y = rateWeighted ? compScalarVals.sol.getRateForRup(i) : 1d;
//				int index;
//				if (logX)
//					index = scalar <= 0 ? 0 : compHist.getClosestXIndex(Math.log10(scalar));
//				else
//					index = compHist.getClosestXIndex(scalar);
//				compHist.add(index, y);
//			}
//		}

		for (int i = 0; i < indexesList.size(); i++) {
			int rupIndex = indexesList.get(i);
			double scalar = scalarVals.getValues().get(i);
			double y = rateWeighted ? scalarVals.getSol().getRateForRup(rupIndex) : 1;
			int index;
			if (logX)
				index = scalar <= 0 ? 0 : hist.getClosestXIndex(Math.log10(scalar));
			else
				index = hist.getClosestXIndex(scalar);
			hist.add(index, y);
//			if (compUniques != null && compUniques.contains(scalarVals.rups.get(rupIndex).unique))
//				commonHist.add(index, y);
		}

		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		if (logX) {
			ArbitrarilyDiscretizedFunc linearHist = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : hist)
				linearHist.set(Math.pow(10, pt.getX()), pt.getY());

			linearHist.setName("Unique");
			funcs.add(linearHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));

//			if (commonHist != null) {
//				linearHist = new ArbitrarilyDiscretizedFunc();
//				for (Point2D pt : commonHist)
//					linearHist.set(Math.pow(10, pt.getX()), pt.getY());
//				linearHist.setName("Common To Both");
//				funcs.add(linearHist);
//				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
//			}
		} else {
			hist.setName("Unique");
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));

//			if (commonHist != null) {
//				commonHist.setName("Common To Both");
//				funcs.add(commonHist);
//				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
//			}
		}

		String title = getInputName() + " " + histScalar.getName() + " Histogram";
		String xAxisLabel = histScalar.getxAxisLabel();
		String yAxisLabel = rateWeighted ? "Annual Rate" : "Count";

		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(compUniques != null);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);

		if (yRange == null) {
			if (logY) {
				double minY = Double.POSITIVE_INFINITY;
				double maxY = 0d;
				for (DiscretizedFunc func : funcs) {
					for (Point2D pt : func) {
						double y = pt.getY();
						if (y > 0) {
							minY = Math.min(minY, y);
							maxY = Math.max(maxY, y);
						}
					}
				}
//				if (compHist != null) {
//					for (Point2D pt : compHist) {
//						double y = pt.getY();
//						if (y > 0) {
//							minY = Math.min(minY, y);
//							maxY = Math.max(maxY, y);
//						}
//					}
//				}
				yRange = new Range(Math.pow(10, Math.floor(Math.log10(minY))),
						Math.pow(10, Math.ceil(Math.log10(maxY))));

			} else {
				double maxY = hist.getMaxY();
//				if (compHist != null)
//					maxY = Math.max(maxY, compHist.getMaxY());
				yRange = new Range(0, 1.05 * maxY);
			}
		}

		gp.drawGraphPanel(spec, logX, logY, xRange, yRange);
		gp.getChartPanel().setSize(800, 600);
		File pngFile = new File(outputDir, prefix + ".png");
//		File pdfFile = new File(outputDir, prefix + ".pdf");
		gp.saveAsPNG(pngFile.getAbsolutePath());
//		gp.saveAsPDF(pdfFile.getAbsolutePath());

		return pngFile;
	}

}

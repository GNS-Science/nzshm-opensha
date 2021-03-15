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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalarValues;

import com.google.common.base.Preconditions;

public class NSHM_InversionDiagnosticsReport extends RupSetDiagnosticsPageGen {

	public static void main(String[] args) throws IOException, DocumentException {

		System.setProperty("java.awt.headless", "true");

		Options options = createOptions();

		CommandLineParser parser = new DefaultParser();

		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(RupSetDiagnosticsPageGen.class), options, true);
			System.exit(2);
			return;
		}

		new NSHM_InversionDiagnosticsReport(cmd).generatePage();
	}

	public NSHM_InversionDiagnosticsReport(CommandLine cmd) throws IOException, DocumentException {
		super(cmd);
		// TODO Auto-generated constructor stub
	};

	@Override
	public void generatePage() throws IOException { // throws IOException
		List<String> lines = new ArrayList<>();
		lines.add("# Rupture Set Diagnostics: " + inputName);

		lines.add("");
		lines.addAll(RupSetDiagnosticsPageGen.getBasicLines(inputRupSet, inputRups));
		lines.add("");

		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";

		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());

//		if (inputConfig != null) {
//			lines.add("## Plausibility Configuration");
//			lines.add(topLink); lines.add("");
//			lines.addAll(getPlausibilityLines(inputConfig, inputJumps));
//			lines.add("");
//		}	

		// length and magnitude distributions

		lines.add("## Rupture Size Histograms");
		lines.add(topLink);
		lines.add("");

		File rupHtmlDir = new File(outputDir, "hist_rup_pages");
		Preconditions.checkState(rupHtmlDir.exists() || rupHtmlDir.mkdir());

		if (!skipHistograms) {
			for (HistScalar scalar : HistScalar.values()) {
				lines.addAll(generateScalarLines(scalar, topLink, resourcesDir));
			}
		}

		System.out.println("DONE building, writing markdown and HTML");
		writeMarkdown(outputDir, summary, lines, tocIndex);

		if (indexDir != null) {
			System.out.println("Writing index to " + indexDir.getAbsolutePath());
			writeIndex(indexDir);
		}

		if (distAzCacheFile != null && (numAzCached < distAzCalc.getNumCachedAzimuths()
				|| numDistCached < distAzCalc.getNumCachedDistances())) {
			System.out.println("Writing dist/az cache to " + distAzCacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(distAzCacheFile);
		}
	}

	private List<String> generateScalarLines(HistScalar scalar, String topLink, File resourcesDir) throws IOException {

		List<HistScalarValues> inputScalarVals = new ArrayList<>();
		List<String> lines = new ArrayList<>();

		if (scalar != HistScalar.MAG)
			return lines;

		lines.add("### " + scalar.name);
		lines.add(topLink);
		lines.add("");
		lines.add(scalar.description);
		lines.add("");

		TableBuilder table = MarkdownUtils.tableBuilder();
		HistScalarValues inputScalars = new HistScalarValues(scalar, inputRupSet, inputSol, inputRups, distAzCalc);
		inputScalarVals.add(inputScalars);

		this.summary.primaryMeta.scalars.add(new ScalarRange(inputScalars));
		HistScalarValues compScalars = null;

		try {
			Range xRange = null;
			plotRuptureHistogram(outputDir, "MAG_rates_log_cbc", inputScalars, compScalars, inputUniques, MAIN_COLOR,
					true, true, xRange);
			xRange = new Range(Math.pow(10, -6), Math.pow(10, -1));

			plotRuptureHistogram(outputDir, "MAG_rates_log_cbc_fixie", inputScalars, compScalars, inputUniques,
					MAIN_COLOR, true, true, xRange);

//			plotRuptureHistograms(
//					resourcesDir, "hist_"+ scalar.name(), table, inputScalars,
//					inputUniques, compScalars, compUniques);
		} catch (IllegalStateException wee) {
			// ah well
			System.out.println("Exception caught in Catch block");
		}

		return lines;
	}

	public static File plotRuptureHistogram(File outputDir, String prefix, HistScalarValues scalarVals,
			HistScalarValues compScalarVals, HashSet<UniqueRupture> compUniques, Color color, boolean logY,
			boolean rateWeighted, Range xRange) throws IOException {
		List<Integer> includeIndexes = new ArrayList<>();
		for (int r = 0; r < scalarVals.rupSet.getNumRuptures(); r++)
			includeIndexes.add(r);
		return plotRuptureHistogram(outputDir, prefix, scalarVals, includeIndexes, compScalarVals, compUniques, color,
				logY, rateWeighted, xRange);
	}

	public static File plotRuptureHistogram(File outputDir, String prefix, HistScalarValues scalarVals,
			Collection<Integer> includeIndexes, HistScalarValues compScalarVals, HashSet<UniqueRupture> compUniques,
			Color color, boolean logY, boolean rateWeighted, Range xRange) throws IOException {

		List<Integer> indexesList = includeIndexes instanceof List<?> ? (List<Integer>) includeIndexes
				: new ArrayList<>(includeIndexes);
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (int r : indexesList)
			track.addValue(scalarVals.values.get(r));
		if (compScalarVals != null) {
			// used only for bounds
			for (double scalar : compScalarVals.values)
				track.addValue(scalar);
		}

		HistScalar histScalar = scalarVals.scalar;
		HistogramFunction hist = histScalar.getHistogram(track);
		boolean logX = histScalar.isLogX();

		// Range setup
		if (xRange == null) {
			if (logX)
				xRange = new Range(Math.pow(10, hist.getMinX() - 0.5 * hist.getDelta()),
						Math.pow(10, hist.getMaxX() + 0.5 * hist.getDelta()));
			else
				xRange = new Range(hist.getMinX() - 0.5 * hist.getDelta(), hist.getMaxX() + 0.5 * hist.getDelta());
		}
		;

		HistogramFunction commonHist = null;
		if (compUniques != null)
			commonHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
		HistogramFunction compHist = null;
		if (compScalarVals != null && (!rateWeighted || compScalarVals.sol != null)) {
			compHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
			for (int i = 0; i < compScalarVals.values.size(); i++) {
				double scalar = compScalarVals.values.get(i);
				double y = rateWeighted ? compScalarVals.sol.getRateForRup(i) : 1d;
				int index;
				if (logX)
					index = scalar <= 0 ? 0 : compHist.getClosestXIndex(Math.log10(scalar));
				else
					index = compHist.getClosestXIndex(scalar);
				compHist.add(index, y);
			}
		}

		for (int i = 0; i < indexesList.size(); i++) {
			int rupIndex = indexesList.get(i);
			double scalar = scalarVals.values.get(i);
			double y = rateWeighted ? scalarVals.sol.getRateForRup(rupIndex) : 1;
			int index;
			if (logX)
				index = scalar <= 0 ? 0 : hist.getClosestXIndex(Math.log10(scalar));
			else
				index = hist.getClosestXIndex(scalar);
			hist.add(index, y);
			if (compUniques != null && compUniques.contains(scalarVals.rups.get(rupIndex).unique))
				commonHist.add(index, y);
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

			if (commonHist != null) {
				linearHist = new ArbitrarilyDiscretizedFunc();
				for (Point2D pt : commonHist)
					linearHist.set(Math.pow(10, pt.getX()), pt.getY());
				linearHist.setName("Common To Both");
				funcs.add(linearHist);
				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
			}
		} else {
			hist.setName("Unique");
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));

			if (commonHist != null) {
				commonHist.setName("Common To Both");
				funcs.add(commonHist);
				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
			}
		}

		String title = histScalar.name + " Histogram";
		String xAxisLabel = histScalar.xAxisLabel;
		String yAxisLabel = rateWeighted ? "Annual Rate" : "Count";

		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(compUniques != null);

		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);

		Range yRange;
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
			if (compHist != null) {
				for (Point2D pt : compHist) {
					double y = pt.getY();
					if (y > 0) {
						minY = Math.min(minY, y);
						maxY = Math.max(maxY, y);
					}
				}
			}
			yRange = new Range(Math.pow(10, Math.floor(Math.log10(minY))), Math.pow(10, Math.ceil(Math.log10(maxY))));
		} else {
			double maxY = hist.getMaxY();
			if (compHist != null)
				maxY = Math.max(maxY, compHist.getMaxY());
			yRange = new Range(0, 1.05 * maxY);
		}

		gp.drawGraphPanel(spec, logX, logY, xRange, yRange);
		gp.getChartPanel().setSize(800, 600);
		File pngFile = new File(outputDir, prefix + ".png");
		File pdfFile = new File(outputDir, prefix + ".pdf");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());

		return pngFile;
	}

}

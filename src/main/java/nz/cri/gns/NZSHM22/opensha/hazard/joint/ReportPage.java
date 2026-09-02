package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The HTML scaffolding shared by the hazard reports: a title, an introduction, a summary table and
 * any number of sections holding rows of figures, written as a standalone page next to its images.
 * Clicking a figure opens it full size.
 *
 * <p>Both {@link HazardComparisonReport} and {@link HazardVariabilityReport} build one of these and
 * hand it their figures; the page itself knows nothing about hazard.
 */
public class ReportPage {

    /** Directory that images are written to, relative to the report. */
    public static final String IMAGE_DIR = "images";

    public static final String INDEX_FILE = "index.html";

    /** A figure in the report: an image, its caption and an optional line of statistics. */
    public static class Figure {
        protected final String path;
        protected final String caption;
        protected final String stats;

        protected Figure(String path, String caption, String stats) {
            this.path = path;
            this.caption = caption;
            this.stats = stats;
        }
    }

    /** A row of figures shown side by side, e.g. two maps and their difference. */
    public static class Row {
        protected final String title;
        protected final List<Figure> figures = new ArrayList<>();

        public Row(String title) {
            this.title = title;
        }

        public void add(File image, String caption, String stats) {
            figures.add(new Figure(IMAGE_DIR + "/" + image.getName(), caption, stats));
        }
    }

    /** A section of the report, e.g. all the maps. */
    public static class Section {
        protected final String title;
        protected final String id;
        protected final List<Row> rows = new ArrayList<>();

        public Section(String title, String id) {
            this.title = title;
            this.id = id;
        }

        public void add(Row row) {
            rows.add(row);
        }
    }

    /**
     * The summary table at the top of a report. The first cell of a row is its label; a row holding
     * a single value besides the label spans the remaining columns.
     */
    public static class Table {
        protected final List<String> header;
        protected final List<List<String>> rows = new ArrayList<>();

        public Table(String... header) {
            this.header = Arrays.asList(header);
        }

        public Table addRow(String label, String... values) {
            List<String> row = new ArrayList<>();
            row.add(label);
            row.addAll(Arrays.asList(values));
            rows.add(row);
            return this;
        }
    }

    /** A file offered for download below the summary, e.g. the data behind a figure. */
    protected static class Download {
        protected final String path;
        protected final String text;

        protected Download(String path, String text) {
            this.path = path;
            this.text = text;
        }
    }

    protected final String title;
    protected final File outputDir;
    protected final List<Section> sections = new ArrayList<>();
    protected final List<Download> downloads = new ArrayList<>();
    protected String intro;
    protected Table summary;
    protected String backHref;
    protected String backText;

    public ReportPage(String title, File outputDir) {
        this.title = Preconditions.checkNotNull(title, "need a title");
        this.outputDir = Preconditions.checkNotNull(outputDir, "need an output directory");
    }

    /** A line of prose below the title, e.g. what the difference maps mean. */
    public ReportPage setIntro(String intro) {
        this.intro = intro;
        return this;
    }

    public ReportPage setSummary(Table summary) {
        this.summary = summary;
        return this;
    }

    public ReportPage add(Section section) {
        sections.add(section);
        return this;
    }

    /**
     * Adds a link back to the page this one was reached from, shown above the title. Used by the
     * per-site pages of a report to get back to the report.
     *
     * @param href the target, relative to this page
     * @param text the link text
     */
    public ReportPage setBackLink(String href, String text) {
        this.backHref = href;
        this.backText = text;
        return this;
    }

    /**
     * Adds a file to the list of downloads below the summary, e.g. the CSV behind a figure.
     *
     * @param file the file, which has to sit below this page's directory
     * @param text the link text
     */
    public ReportPage addDownload(File file, String text) {
        downloads.add(new Download(relativePath(file), text));
        return this;
    }

    /**
     * A file's path relative to this page's directory, for use as an href.
     *
     * @throws IllegalArgumentException if the file does not sit below the page's directory, because
     *     a standalone report has to be able to move as one directory
     */
    protected String relativePath(File file) {
        String base = outputDir.getAbsoluteFile().toPath().normalize().toString();
        String target = file.getAbsoluteFile().toPath().normalize().toString();
        Preconditions.checkArgument(
                target.startsWith(base + File.separator),
                "%s is not below the report directory %s",
                target,
                base);
        return target.substring(base.length() + 1).replace(File.separatorChar, '/');
    }

    /** The directory images are written to, created if it does not exist yet. */
    public File imageDir() {
        File imageDir = new File(outputDir, IMAGE_DIR);
        Preconditions.checkState(
                imageDir.exists() || imageDir.mkdirs(),
                "Could not create output directory %s",
                imageDir.getAbsolutePath());
        return imageDir;
    }

    /**
     * Writes the page.
     *
     * @return the report's index.html
     */
    public File write() throws IOException {
        File index = new File(outputDir, INDEX_FILE);
        try (Writer out = Files.newBufferedWriter(index.toPath(), StandardCharsets.UTF_8)) {
            out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
            out.write("<meta charset=\"utf-8\">\n");
            out.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
            out.write("<title>" + escape(title) + "</title>\n");
            out.write("<style>\n" + css() + "</style>\n");
            out.write("</head>\n<body>\n");

            if (backHref != null) {
                out.write(
                        "<p class=\"back\"><a href=\""
                                + backHref
                                + "\">&larr; "
                                + escape(backText)
                                + "</a></p>\n");
            }
            out.write("<h1>" + escape(title) + "</h1>\n");
            out.write(
                    "<p class=\"meta\">Generated "
                            + escape(
                                    LocalDateTime.now()
                                            .format(
                                                    DateTimeFormatter.ofPattern(
                                                            "yyyy-MM-dd HH:mm")))
                            + (intro == null ? "" : ". " + escape(intro))
                            + "</p>\n");

            writeSummary(out);
            writeDownloads(out);

            out.write("<nav><ul>\n");
            for (Section section : sections) {
                out.write(
                        "<li><a href=\"#"
                                + section.id
                                + "\">"
                                + escape(section.title)
                                + "</a></li>\n");
            }
            out.write("</ul></nav>\n");

            for (Section section : sections) {
                out.write("<section id=\"" + section.id + "\">\n");
                out.write("<h2>" + escape(section.title) + "</h2>\n");
                for (Row row : section.rows) {
                    out.write("<h3>" + escape(row.title) + "</h3>\n");
                    out.write("<div class=\"figures\">\n");
                    for (Figure figure : row.figures) {
                        out.write("<figure>\n");
                        out.write(
                                "<a href=\""
                                        + figure.path
                                        + "\"><img src=\""
                                        + figure.path
                                        + "\" alt=\""
                                        + escape(figure.caption)
                                        + "\"></a>\n");
                        out.write("<figcaption>" + escape(figure.caption));
                        if (figure.stats != null) {
                            out.write("<span class=\"stats\">" + escape(figure.stats) + "</span>");
                        }
                        out.write("</figcaption>\n</figure>\n");
                    }
                    out.write("</div>\n");
                }
                out.write("</section>\n");
            }

            out.write("<div id=\"lightbox\"><img id=\"lightbox-image\" alt=\"\"></div>\n");
            out.write("<script>\n" + script() + "</script>\n");
            out.write("</body>\n</html>\n");
        }
        return index;
    }

    protected void writeDownloads(Writer out) throws IOException {
        if (downloads.isEmpty()) {
            return;
        }
        out.write("<ul class=\"downloads\">\n");
        for (Download download : downloads) {
            out.write(
                    "<li><a href=\""
                            + download.path
                            + "\">"
                            + escape(download.text)
                            + "</a></li>\n");
        }
        out.write("</ul>\n");
    }

    protected void writeSummary(Writer out) throws IOException {
        if (summary == null) {
            return;
        }
        out.write("<table class=\"summary\">\n");
        if (!summary.header.isEmpty()) {
            out.write("<tr>");
            for (String cell : summary.header) {
                out.write("<th>" + escape(cell) + "</th>");
            }
            out.write("</tr>\n");
        }
        int columns = Math.max(summary.header.size(), 2);
        for (List<String> row : summary.rows) {
            out.write("<tr><th>" + escape(row.get(0)) + "</th>");
            // a row holding a single value spans the remaining columns
            int span = row.size() == 2 ? columns - 1 : 1;
            for (String cell : row.subList(1, row.size())) {
                out.write(
                        "<td"
                                + (span > 1 ? " colspan=\"" + span + "\"" : "")
                                + ">"
                                + escape(cell)
                                + "</td>");
            }
            out.write("</tr>\n");
        }
        out.write("</table>\n");
    }

    public static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    protected static String css() {
        return "body { font-family: system-ui, Arial, sans-serif; margin: 0 auto; padding: 1.5rem;"
                + " max-width: 1600px; color: #222; }\n"
                + "h1 { font-size: 1.6rem; } h2 { font-size: 1.3rem; margin-top: 2.5rem;"
                + " border-bottom: 1px solid #ddd; padding-bottom: .3rem; }\n"
                + "h3 { font-size: 1.05rem; margin: 1.5rem 0 .5rem; color: #444; }\n"
                + ".meta { color: #666; }\n"
                + ".back { margin: 0 0 .5rem; }\n"
                + "ul.downloads { list-style: none; padding: 0; display: flex; gap: 1rem;"
                + " font-size: .9rem; }\n"
                + "table.summary { border-collapse: collapse; margin: 1rem 0; }\n"
                + "table.summary th, table.summary td { border: 1px solid #ddd; padding: .35rem"
                + " .7rem; text-align: left; font-weight: normal; }\n"
                + "table.summary tr:first-child th { font-weight: bold; background: #f4f4f4; }\n"
                + "table.summary th:first-child { font-weight: bold; }\n"
                + "nav ul { list-style: none; padding: 0; display: flex; gap: 1rem; }\n"
                + ".figures { display: flex; flex-wrap: wrap; gap: 1rem; }\n"
                + "figure { flex: 1 1 30%; min-width: 280px; margin: 0; }\n"
                + "figure img { width: 100%; height: auto; border: 1px solid #ddd; cursor:"
                + " zoom-in; }\n"
                + "figcaption { font-size: .85rem; color: #444; padding-top: .3rem; }\n"
                + "figcaption .stats { display: block; color: #777; }\n"
                + "#lightbox { display: none; position: fixed; inset: 0; background: rgba(0, 0, 0,"
                + " .85); align-items: center; justify-content: center; cursor: zoom-out; z-index:"
                + " 10; }\n"
                + "#lightbox.open { display: flex; }\n"
                + "#lightbox img { max-width: 96vw; max-height: 96vh; }\n";
    }

    protected static String script() {
        return "var box = document.getElementById('lightbox');\n"
                + "var boxImage = document.getElementById('lightbox-image');\n"
                + "document.querySelectorAll('figure a').forEach(function (link) {\n"
                + "  link.addEventListener('click', function (event) {\n"
                + "    event.preventDefault();\n"
                + "    boxImage.src = link.getAttribute('href');\n"
                + "    box.classList.add('open');\n"
                + "  });\n"
                + "});\n"
                + "box.addEventListener('click', function () { box.classList.remove('open'); });\n"
                + "document.addEventListener('keydown', function (event) {\n"
                + "  if (event.key === 'Escape') { box.classList.remove('open'); }\n"
                + "});\n";
    }
}

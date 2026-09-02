package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.util.List;

/**
 * How a rupture's contribution to a site's hazard is shared out among the fault sections it runs
 * over. Used by {@link SiteSourceContributions#getSectionRates(SectionWeighting)} to turn
 * per-rupture contributions into a per-section map.
 *
 * <p>There is no single right answer here, because a section does not have a contribution of its
 * own: hazard is calculated per rupture, and a multi-fault rupture is one indivisible event. The
 * choice of weighting is the choice of what question the map answers.
 *
 * <ul>
 *   <li>{@link #participation()} — "how much of this site's hazard passes through this section",
 *       crediting every section of a rupture with the whole of it.
 *   <li>{@link #nearest()} — "which section actually shook the site", crediting only the closest.
 *   <li>{@link #proximity()} — the same idea, softened: sections share the rupture in proportion to
 *       a distance decay, so a rupture running past the site credits the stretch that runs past it
 *       rather than one subsection.
 * </ul>
 *
 * <p>The distinction matters because a GMM sees a multi-fault rupture almost entirely through its
 * closest point: {@code rRup} is the minimum over the rupture's sections, so a distant section of a
 * rupture that also breaks next to the site adds nothing to the shaking there — it only adds area,
 * and so magnitude. Participation credits it anyway, which is why long ruptures light up faults
 * hundreds of kilometres from the site on a participation map.
 */
public interface SectionWeighting {

    /**
     * The share of a rupture's contribution that each of its sections is credited with, in the
     * order the sections are given.
     *
     * <p>Implementations that partition the rupture return weights summing to one; {@link
     * #participation()} deliberately does not, because it credits every section in full.
     *
     * @param sectionIndices the sections of the rupture
     * @param siteDistances distance from the site to every section of the rupture set, indexed by
     *     section index, as returned by {@link SiteSourceContributions#getSectionDistances()}
     */
    double[] weights(List<Integer> sectionIndices, double[] siteDistances);

    /** Short description of what the weighting credits, used to label maps and legends. */
    String getLabel();

    /**
     * Credits every section of a rupture with the rupture's whole contribution. Section rates then
     * do not sum to the site's total rate — a rupture over ten sections is counted ten times.
     */
    static SectionWeighting participation() {
        return new SectionWeighting() {
            @Override
            public double[] weights(List<Integer> sectionIndices, double[] siteDistances) {
                double[] weights = new double[sectionIndices.size()];
                java.util.Arrays.fill(weights, 1d);
                return weights;
            }

            @Override
            public String getLabel() {
                return "Hazard Through Section";
            }
        };
    }

    /**
     * Credits a rupture's whole contribution to its single closest section, the one whose proximity
     * set the rupture's {@code rRup} and so drove the ground motion the GMM produced. Section rates
     * partition the site's total rate exactly.
     */
    static SectionWeighting nearest() {
        return new SectionWeighting() {
            @Override
            public double[] weights(List<Integer> sectionIndices, double[] siteDistances) {
                double[] weights = new double[sectionIndices.size()];
                int closest = 0;
                for (int i = 1; i < sectionIndices.size(); i++) {
                    if (siteDistances[sectionIndices.get(i)]
                            < siteDistances[sectionIndices.get(closest)]) {
                        closest = i;
                    }
                }
                weights[closest] = 1d;
                return weights;
            }

            @Override
            public String getLabel() {
                return "Section Influence (nearest)";
            }
        };
    }

    /** {@link Proximity} with the default near-field distance and decay. */
    static SectionWeighting proximity() {
        return new Proximity(Proximity.DEFAULT_NEAR_FIELD_KM, Proximity.DEFAULT_DECAY);
    }

    /**
     * Shares a rupture's contribution among its sections in proportion to {@code (d + h)^-b}, where
     * {@code d} is the distance from the site to the section, {@code h} a near-field distance that
     * keeps a section the site sits on from taking everything, and {@code b} a decay exponent.
     * Section rates partition the site's total rate exactly.
     *
     * <p>This is a stand-in for the distance decay of a GMM, not a derived quantity: it spreads a
     * rupture's credit over the stretch of fault that runs near the site instead of putting all of
     * it on one subsection the way {@link #nearest()} does, while still starving the distant end of
     * a long rupture. The defaults are in the range that ground motion models decay over in the
     * near field, but treat the resulting numbers as a ranking rather than as physical shares.
     */
    class Proximity implements SectionWeighting {

        /**
         * Near-field distance in km, which bounds how much a section right at the site can take.
         */
        public static final double DEFAULT_NEAR_FIELD_KM = 10d;

        /** Decay exponent, in the range that ground motion attenuates over in the near field. */
        public static final double DEFAULT_DECAY = 1.5d;

        private final double nearFieldKm;
        private final double decay;

        public Proximity(double nearFieldKm, double decay) {
            Preconditions.checkArgument(nearFieldKm > 0, "nearFieldKm must be positive");
            Preconditions.checkArgument(decay > 0, "decay must be positive");
            this.nearFieldKm = nearFieldKm;
            this.decay = decay;
        }

        @Override
        public double[] weights(List<Integer> sectionIndices, double[] siteDistances) {
            double[] weights = new double[sectionIndices.size()];
            double sum = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = Math.pow(siteDistances[sectionIndices.get(i)] + nearFieldKm, -decay);
                sum += weights[i];
            }
            for (int i = 0; i < weights.length; i++) {
                weights[i] /= sum;
            }
            return weights;
        }

        @Override
        public String getLabel() {
            return "Section Influence";
        }
    }
}

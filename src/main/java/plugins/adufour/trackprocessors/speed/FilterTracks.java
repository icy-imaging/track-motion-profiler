/**
 * 
 */
package plugins.adufour.trackprocessors.speed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginBundled;
import icy.sequence.Sequence;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarBoolean;
import plugins.adufour.vars.lang.VarDouble;
import plugins.adufour.vars.lang.VarEnum;
import plugins.adufour.vars.lang.VarSequence;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackSegment;
import plugins.nchenouard.spot.Detection;

/**
 * @author Stephane
 */
public class FilterTracks extends Plugin implements Block, PluginBundled
{
    public enum Criterion
    {
        CRITERION_T_START("start time"), CRITERION_CURRENT_T("current time"), CRITERION_T_END("end time"),
        CRITERION_DURATION("track duration"), CRITERION_TOT_DISP("total displacement"),
        CRITERION_NET_DISP("net displacement"), CRITERION_LINEARITY("linearity"), CRITERION_EXTENT("extent"),
        CRITERION_MIN_DISP("minimum displacement"), CRITERION_MAX_DISP("maximum displacement"),
        CRITERION_AVG_DISP("average displacement");

        String description;

        private Criterion(String description)
        {
            this.description = description;
        }

        @Override
        public String toString()
        {
            return description;
        }
    }

    public enum ComparisonType
    {
        GREATER_THAN(">"), LOWER_THAN("<");

        private String symbol;

        private ComparisonType(String symbol)
        {
            this.symbol = symbol;
        }

        boolean compare(double d1, double d2)
        {
            switch (this)
            {
                case GREATER_THAN:
                    return d1 > d2;
                case LOWER_THAN:
                    return d1 < d2;
                default:
                    throw new UnsupportedOperationException("Unknown operation: " + toString());
            }
        }

        @Override
        public String toString()
        {
            return symbol;
        }
    }

    /**
     * Get enabled state (even partially) for a given track
     * @param track segment of track
     *
     * @return boolean
     **/
    public static boolean isTrackEnabled(TrackSegment track)
    {
        for (Detection detection : track.getDetectionList())
            if (detection.isEnabled())
                return true;

        return false;
    }

    /**
     * Filter given list of track on a specific criterion (keep tracks replying to the given
     * criterion)
     * 
     * @param track
     *        tracks to filter
     * @param criterion
     *        criterion to use to filter tracks
     * @param comp
     *        comparison type
     * @param value
     *        value used for comparison
     * @param xScale
     *        X scaling ratio (pixel size X in um/px) to convert displacement in real unit (instead
     *        of pixel).
     * @param yScale
     *        Y scaling ratio (pixel size Y in um/px) to convert displacement in real unit (instead
     *        of pixel).
     * @param zScale
     *        Z scaling ratio (pixel size Z in um/px) to convert displacement in real unit (instead
     *        of pixel).
     * @param tInterval
     *        Time interval between 2 frames (second/frame) to compute speed instead of
     *        displacement.<br>
     *        Set it to <code>0</code> if you want mix/max/mean displacement otherwise statistic
     *        return min/max/mean speed using the provided time interval.
     * @param setTrackEnabledFlag
     *        if set to <code>TRUE</code> then {@link TrackSegment#setAllDetectionEnabled(boolean)}
     *        and {@link Detection#setEnabled(boolean)} properties are
     *        modified
     * @return filtered list of track (tracks replying to the filter criterion)
     */
    public static boolean acceptTrack(TrackSegment track, Criterion criterion, ComparisonType comp, double value,
            double xScale, double yScale, double zScale, double tInterval, boolean setTrackEnabledFlag)
    {
        final TrackStatistic stats = TrackStatistic.computeStatistic(track, xScale, yScale, zScale, tInterval,
                criterion == Criterion.CRITERION_EXTENT, true, true, null, null, null, null);

        // accept it by default
        boolean result = true;

        // stats are valid ?
        if (stats != null)
        {
            switch (criterion)
            {
                case CRITERION_T_START:
                    result = comp.compare(stats.startTime, value);
                    break;

                case CRITERION_CURRENT_T:
                    if (setTrackEnabledFlag)
                    {
                        for (Detection det : track.getDetectionList())
                            det.setEnabled(comp.compare(det.getT() * ((tInterval != 0d) ? tInterval : 1d), value));
                    }
                    break;

                case CRITERION_T_END:
                    result = comp.compare(stats.endTime, value);
                    break;

                case CRITERION_DURATION:
                    result = comp.compare(stats.duration, value);
                    break;

                case CRITERION_LINEARITY:
                    result = comp.compare(stats.global.linearity, value);
                    break;

                case CRITERION_EXTENT:
                    result = comp.compare(stats.global.searchRadius, value);
                    break;

                case CRITERION_TOT_DISP:
                    result = comp.compare(stats.global.totalDisp, value);
                    break;

                case CRITERION_NET_DISP:
                    result = comp.compare(stats.global.netDisp, value);
                    break;

                case CRITERION_MIN_DISP:
                    result = comp.compare(stats.global.minDispOrSpeed, value);
                    break;

                case CRITERION_MAX_DISP:
                    result = comp.compare(stats.global.maxDispOrSpeed, value);
                    break;

                case CRITERION_AVG_DISP:
                    result = comp.compare(stats.global.meanDispOrSpeed, value);
                    break;

                default:
                    result = false;
                    break;
            }
        }

        // need to filter track ?
        if (setTrackEnabledFlag && !result)
            track.setAllDetectionEnabled(false);

        return result;
    }

    /**
     * Filter given list of track on a specific criterion (keep tracks replying to the given
     * criterion)
     * 
     * @param tracks
     *        tracks to filter
     * @param criterion
     *        criterion to use to filter tracks
     * @param comp
     *        comparison type
     * @param value
     *        value used for comparison
     * @param sequence
     *        sequence used to retrieve real units from (using pixel size information from
     *        metadata).<br>
     *        Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
     * @param setTrackEnabledFlag
     *        if set to <code>TRUE</code> then {@link TrackSegment#setAllDetectionEnabled(boolean)}
     *        and {@link Detection#setEnabled(boolean)} properties are
     *        modified
     * @return filtered list of track (tracks replying to the filter criterion)
     */
    public static Set<TrackSegment> filterTracks(Collection<TrackSegment> tracks, Criterion criterion,
            ComparisonType comp, double value, Sequence sequence, boolean setTrackEnabledFlag)
    {
        final Set<TrackSegment> result = new HashSet<TrackSegment>();

        final double xScale;
        final double yScale;
        final double zScale;
        final double tInterval;

        if (sequence != null)
        {
            xScale = sequence.getPixelSizeX();
            yScale = sequence.getPixelSizeY();
            zScale = sequence.getPixelSizeZ();
            tInterval = sequence.getTimeInterval();
        }
        else
        {
            xScale = 1d;
            yScale = 1d;
            zScale = 1d;
            tInterval = 1d;
        }

        for (TrackSegment track : tracks)
        {
            if (acceptTrack(track, criterion, comp, value, xScale, yScale, zScale, tInterval, setTrackEnabledFlag))
                result.add(track);
        }

        return result;
    }

    // VAR
    public final Var<TrackGroup> tracks;
    public final VarSequence sequence;
    public final VarBoolean useRealUnits;
    public final VarEnum<Criterion> criterion;
    public final VarEnum<ComparisonType> operation;
    public final VarDouble value;

    public FilterTracks()
    {
        super();

        tracks = new Var<TrackGroup>("Track group", new TrackGroup(null));
        sequence = new VarSequence("Sequence (used for units)", null);
        useRealUnits = new VarBoolean("Use real units (\u03BCm/sec)", false);
        criterion = new VarEnum<Criterion>("Filter on", Criterion.CRITERION_DURATION);
        operation = new VarEnum<ComparisonType>("Accept if", ComparisonType.GREATER_THAN);
        value = new VarDouble("Value", 0d);
    }

    @Override
    public void declareInput(VarList inputMap)
    {
        inputMap.add("trackgroup", tracks);
        inputMap.add("sequence", sequence);
        inputMap.add("useRealUnitss", useRealUnits);
        inputMap.add("criterion", criterion);
        inputMap.add("comp", operation);
        inputMap.add("value", value);

    }

    @Override
    public void declareOutput(VarList outputMap)
    {
        // no output here
    }

    @Override
    public void run()
    {
        // execution from protocol
        final TrackGroup trackGroup = tracks.getValue();

        if (trackGroup != null)
        {
            // build the list of enabled tracks
            final List<TrackSegment> tracksToFilter = new ArrayList<TrackSegment>();

            for (TrackSegment track : trackGroup.getTrackSegmentList())
                if (FilterTracks.isTrackEnabled(track))
                    tracksToFilter.add(track);

            // filter tracks (modifying internal track segment property)
            filterTracks(tracksToFilter, criterion.getValue(), operation.getValue(), value.getValue().doubleValue(),
                    useRealUnits.getValue().booleanValue() ? sequence.getValue() : null, true);
        }
    }

    @Override
    public String getMainPluginClassName()
    {
        return SpeedProfiler.class.getName();
    }
}

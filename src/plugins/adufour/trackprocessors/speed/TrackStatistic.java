package plugins.adufour.trackprocessors.speed;

import java.awt.Color;

import org.math.plot.Plot2DPanel;
import org.math.plot.Plot3DPanel;
import org.math.plot.PlotPanel;

import icy.math.ArrayMath;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackSegment;
import plugins.nchenouard.spot.Detection;

public class TrackStatistic
{
    public static class SingleStatistic
    {
        public double totalDisp;
        public double netDisp;
        public double linearity;
        public double searchRadius;

        public double minDispOrSpeed;
        public double maxDispOrSpeed;
        public double meanDispOrSpeed;
    }

    public String groupName;
    public int id;

    public double startTime;
    public double endTime;
    public double duration;

    public final SingleStatistic global;
    public final SingleStatistic x;
    public final SingleStatistic y;
    public final SingleStatistic z;

    public TrackStatistic()
    {
        super();

        global = new SingleStatistic();
        x = new SingleStatistic();
        y = new SingleStatistic();
        z = new SingleStatistic();
    }

    /**
     * Compute and return motion/speed statistic from the given list of {@link TrackSegment}.
     * 
     * @param track
     *        track we want to compute statistic for
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
     * @param wantExtend
     *        specify that we want the extend / search radius information (take sometime to compute)
     * @param computeSpeed
     *        compute speed information instead of displacement
     * @param ignoreFilteredDetection
     *        do not compute statistics for filtered detection (see
     *        {@link TrackSegment#isAllDetectionEnabled()})
     * @param xPlotPanel
     *        optional X plot panel where we will draw the X motion
     * @param yPlotPanel
     *        optional Y plot panel where we will draw the Y motion
     * @param zPlotPanel
     *        optional Z plot panel where we will draw the Z motion
     * @param globalPlotPanel
     *        optional global plot panel where we will draw all the tracks motion
     * @return motion/speed statistics for the given track, it can return <code>null</code> if track
     *         is completely filtered (see <i>ignoreFilteredDetection</i> parameter).
     */
    public static TrackStatistic computeStatistic(TrackSegment track, double xScale, double yScale, double zScale,
            double tInterval, boolean wantExtend, boolean computeSpeed, boolean ignoreFilteredDetection,
            Plot2DPanel xPlotPanel, Plot2DPanel yPlotPanel, Plot2DPanel zPlotPanel, PlotPanel globalPlotPanel)
    {
        final int nbDetections = track.getDetectionList().size();
        final int firstIndex;
        final int lastIndex;
        final int size;

        if (ignoreFilteredDetection)
        {
            int i = 0;

            // get start point
            while ((i < nbDetections) && !track.getDetectionAt(i).isEnabled())
                i++;
            firstIndex = i;
            // get end point
            while ((i < nbDetections) && track.getDetectionAt(i).isEnabled())
                i++;
            lastIndex = i - 1;
            size = i - firstIndex;

            while (i < nbDetections)
            {
                // inconsistent filtering (hole inside track) --> can't process this track
                if (track.getDetectionAt(i).isEnabled())
                    return null;

                i++;
            }
        }
        else
        {
            // process all
            firstIndex = 0;
            lastIndex = nbDetections - 1;
            size = nbDetections;
        }

        // we need at least 2 detections to have a valid track
        if (size < 2)
            return null;

        final TrackStatistic result = new TrackStatistic();
        final SingleStatistic global = result.global;
        final SingleStatistic x = result.x;
        final SingleStatistic y = result.y;
        final SingleStatistic z = result.z;

        // extract the raw track data
        final double[] xData = new double[size];
        final double[] yData = new double[size];
        final double[] zData = new double[size];

        // displacement data (size: N-1)
        final double[] disp = new double[size - 1];
        final double[] xDisp = new double[size - 1];
        final double[] yDisp = new double[size - 1];
        final double[] zDisp = new double[size - 1];

        // accumulate data here
        for (int i = 0; i < size; i++)
        {
            final Detection det = track.getDetectionAt(i + firstIndex);

            xData[i] = det.getX() * xScale;
            yData[i] = det.getY() * yScale;
            zData[i] = det.getZ() * zScale;

            if (i > 0)
            {
                final int j = i - 1;

                // retrieve displacement from previous position
                xDisp[j] = xData[i] - xData[j];
                yDisp[j] = yData[i] - yData[j];
                zDisp[j] = zData[i] - zData[j];
                disp[j] = Math.sqrt((xDisp[j] * xDisp[j]) + (yDisp[j] * yDisp[j]) + (zDisp[j] * zDisp[j]));
            }
        }

        // track start/end
        result.startTime = track.getDetectionAt(firstIndex).getT();
        result.endTime = track.getDetectionAt(lastIndex).getT();
        // track duration
        result.duration = (size - 1);

        // real unit ?
        if (tInterval != 0d)
        {
            result.startTime *= tInterval;
            result.endTime *= tInterval;
            result.duration *= tInterval;
        }

        // relative displacement (start to end)
        x.netDisp = Math.abs(xData[xData.length - 1] - xData[0]);
        y.netDisp = Math.abs(yData[yData.length - 1] - yData[0]);
        z.netDisp = Math.abs(zData[zData.length - 1] - zData[0]);
        global.netDisp = Math.sqrt((x.netDisp * x.netDisp) + (y.netDisp * y.netDisp) + (z.netDisp * z.netDisp));

        // total (sum of) displacement(s)
        x.totalDisp = ArrayMath.sum(ArrayMath.abs(xDisp, false));
        y.totalDisp = ArrayMath.sum(ArrayMath.abs(yDisp, false));
        z.totalDisp = ArrayMath.sum(ArrayMath.abs(zDisp, false));
        global.totalDisp = ArrayMath.sum(ArrayMath.abs(disp, false));

        // linearity (ratio of total to net displacement)
        x.linearity = (x.totalDisp == 0d) ? 0d : x.netDisp / x.totalDisp;
        y.linearity = (y.totalDisp == 0d) ? 0d : y.netDisp / y.totalDisp;
        z.linearity = (z.totalDisp == 0d) ? 0d : z.netDisp / z.totalDisp;
        global.linearity = (global.totalDisp == 0d) ? 0d : global.netDisp / global.totalDisp;

        // minimum displacement
        x.minDispOrSpeed = ArrayMath.min(xDisp);
        y.minDispOrSpeed = ArrayMath.min(yDisp);
        z.minDispOrSpeed = ArrayMath.min(zDisp);
        global.minDispOrSpeed = ArrayMath.min(disp);

        // maximum displacement
        x.maxDispOrSpeed = ArrayMath.max(xDisp);
        y.maxDispOrSpeed = ArrayMath.max(yDisp);
        z.maxDispOrSpeed = ArrayMath.max(zDisp);
        global.maxDispOrSpeed = ArrayMath.max(disp);

        // mean displacement
        x.meanDispOrSpeed = ArrayMath.mean(xDisp);
        y.meanDispOrSpeed = ArrayMath.mean(yDisp);
        z.meanDispOrSpeed = ArrayMath.mean(zDisp);
        global.meanDispOrSpeed = ArrayMath.mean(disp);

        // prefer speed instead of displacement ? --> divide by time then...
        if (computeSpeed && (tInterval != 0d))
        {
            x.minDispOrSpeed /= tInterval;
            y.minDispOrSpeed /= tInterval;
            z.minDispOrSpeed /= tInterval;
            global.minDispOrSpeed /= tInterval;

            x.maxDispOrSpeed /= tInterval;
            y.maxDispOrSpeed /= tInterval;
            z.maxDispOrSpeed /= tInterval;
            global.maxDispOrSpeed /= tInterval;

            x.meanDispOrSpeed /= tInterval;
            y.meanDispOrSpeed /= tInterval;
            z.meanDispOrSpeed /= tInterval;
            global.meanDispOrSpeed /= tInterval;
        }

        double xSearchRadius = 0d;
        double ySearchRadius = 0d;
        double zSearchRadius = 0d;
        double searchRadius = 0d;

        if (wantExtend)
        {
            // find track extent (largest distance between any 2 detections)
            // O(n²) complexity, can take a long time :-(
            for (int i = 0; i < size; i++)
            {
                for (int j = i + 1; j < size; j++)
                {
                    final double dx = Math.abs(xData[i] - xData[j]);
                    final double dy = Math.abs(yData[i] - yData[j]);
                    final double dz = Math.abs(zData[i] - zData[j]);
                    final double dist = (dx * dx) + (dy * dy) + (dz * dz);

                    if (dist > searchRadius)
                        searchRadius = dist;
                    if (dx > xSearchRadius)
                        xSearchRadius = dx;
                    if (dy > ySearchRadius)
                        ySearchRadius = dy;
                    if (dz > zSearchRadius)
                        zSearchRadius = dz;
                }
            }

            searchRadius = Math.sqrt(searchRadius);
        }

        x.searchRadius = xSearchRadius;
        y.searchRadius = ySearchRadius;
        z.searchRadius = zSearchRadius;
        global.searchRadius = searchRadius;

        // plotting
        final Color color = track.getFirstDetection().getColor();
        final TrackGroup group = track.getOwnerTrackGroup();

        if (group != null)
        {
            // group name
            result.groupName = group.getDescription();
            // track id
            result.id = group.getTrackSegmentList().indexOf(track);
        }
        else
        {
            // default empty values
            result.groupName = "";
            result.id = -1;
        }

        // track name
        final String trackName = result.groupName + " - Track " + result.id;

        // center all tracks from plotting purposes
        ArrayMath.subtract(xData, xData[0], xData);
        ArrayMath.subtract(yData, yData[0], yData);
        ArrayMath.subtract(zData, zData[0], zData);

        if (xPlotPanel != null)
            xPlotPanel.addLinePlot(trackName, color, xDisp);
        if (yPlotPanel != null)
            yPlotPanel.addLinePlot(trackName, color, yDisp);
        if (zPlotPanel != null)
            zPlotPanel.addLinePlot(trackName, color, zDisp);
        if (globalPlotPanel instanceof Plot3DPanel)
            ((Plot3DPanel) globalPlotPanel).addLinePlot(trackName, color, xData, yData, zData);
        else if (globalPlotPanel instanceof Plot2DPanel)
            ((Plot2DPanel) globalPlotPanel).addLinePlot(trackName, color, xData, yData);

        return result;
    }
}

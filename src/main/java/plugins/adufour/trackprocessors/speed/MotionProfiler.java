/**
 * 
 */
package plugins.adufour.trackprocessors.speed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.math.plot.Plot2DPanel;
import org.math.plot.Plot3DPanel;
import org.math.plot.PlotPanel;

import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginBundled;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import icy.util.XLSUtil;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarBoolean;
import plugins.adufour.vars.lang.VarMutable;
import plugins.adufour.vars.lang.VarSequence;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackSegment;

/**
 * @author Stephane
 */
public class MotionProfiler extends Plugin implements Block, PluginBundled
{
    private static final int COL_TRACK_GP = 0;
    private static final int COL_TRACK_ID = 1;
    private static final int COL_TRACK_START = 2;
    private static final int COL_TRACK_END = 3;
    private static final int COL_DURATION = 4;
    private static final int COL_TOT_DISP = 5;
    private static final int COL_NET_DISP = 6;
    private static final int COL_LINEARITY = 7;
    private static final int COL_RADIUS = 8;
    private static final int COL_MIN_DISP = 9;
    private static final int COL_MAX_DISP = 10;
    private static final int COL_AVG_DISP = 11;

    /**
     * Computes and returns motion/speed statistics from the given list of {@link TrackSegment}.<br>
     * Note that if may contains <code>null</code> entry for filtered tracks.
     * 
     * @param tracks
     *        tracks we want to export data fort
     * @param sequence
     *        sequence used to retrieve real units from (using pixel size information from
     *        metadata).<br>
     *        Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
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
     *
     * @return List of track statistics
     * @throws IOException IOException
     * @throws WriteException WriteException
     */
    public static List<TrackStatistic> computeTracksStatistics(Collection<TrackSegment> tracks, Sequence sequence,
            boolean computeSpeed, boolean ignoreFilteredDetection, Plot2DPanel xPlotPanel, Plot2DPanel yPlotPanel,
            Plot2DPanel zPlotPanel, PlotPanel globalPlotPanel) throws IOException, WriteException
    {
        final List<TrackStatistic> result = new ArrayList<TrackStatistic>();
        final double xScale;
        final double yScale;
        final double zScale;
        final double tScale;

        if (sequence != null)
        {
            xScale = sequence.getPixelSizeX();
            yScale = sequence.getPixelSizeY();
            zScale = sequence.getPixelSizeZ();
            tScale = sequence.getTimeInterval();
        }
        else
        {
            xScale = 1d;
            yScale = 1d;
            zScale = 1d;
            tScale = 1d;
        }

        // if we have plot wanted, take care of cleaning them first
        if (xPlotPanel != null)
            xPlotPanel.removeAllPlots();
        if (yPlotPanel != null)
            yPlotPanel.removeAllPlots();
        if (zPlotPanel != null)
            zPlotPanel.removeAllPlots();
        if (globalPlotPanel != null)
            globalPlotPanel.removeAllPlots();

        int index = 0;
        for (TrackSegment track : tracks)
        {
            final TrackStatistic stat = TrackStatistic.computeStatistic(track, xScale, yScale, zScale, tScale, true,
                    computeSpeed, ignoreFilteredDetection, xPlotPanel, yPlotPanel, zPlotPanel, globalPlotPanel);

            if (stat != null)
            {
                // fill missing information
                if (StringUtil.isEmpty(stat.groupName))
                    stat.groupName = "TrackGroup";
                if (stat.id == -1)
                    stat.id = index;

            }

            // add stat to result (even if null to preserve consistency with input collection)
            result.add(stat);
            index++;
        }

        if (globalPlotPanel != null) {
            final double minX = globalPlotPanel.plotCanvas.base.getMinBounds()[0];
            final double minY = globalPlotPanel.plotCanvas.base.getMinBounds()[1];

            final double maxX = globalPlotPanel.plotCanvas.base.getMaxBounds()[0];
            final double maxY = globalPlotPanel.plotCanvas.base.getMaxBounds()[1];

            double maxVal = 0d;
            maxVal = Math.max(maxVal, Math.max(maxX, maxY));

            double minVal = 0d;
            minVal = Math.min(minVal, Math.min(minX, minY));

            if (globalPlotPanel instanceof Plot3DPanel) {
                final double minZ = globalPlotPanel.plotCanvas.base.getMinBounds()[2];
                final double maxZ = globalPlotPanel.plotCanvas.base.getMaxBounds()[2];

                maxVal = Math.max(maxVal, maxZ);
                minVal = Math.min(minVal, minZ);
            }

            final double setVal = Math.ceil(Math.max(Math.abs(maxVal), Math.abs(minVal)));

            globalPlotPanel.plotCanvas.setFixedBounds(0, -setVal, setVal);
            globalPlotPanel.plotCanvas.setFixedBounds(1, -setVal, setVal);

            if (globalPlotPanel instanceof Plot3DPanel)
                globalPlotPanel.plotCanvas.setFixedBounds(2, -setVal, setVal);
        }

        return result;
    }

    /**
     * Get motion data in XLS (WorkBook) format from the given list of {@link TrackSegment}.<br>
     * WARNING: you need to explicitly use {@link XLSUtil#saveAndClose(WritableWorkbook)} method to
     * save the obtained XLS into <code>outputPath</code>
     * 
     * @param tracks
     *        tracks we want to export data fort
     * @param sequence
     *        sequence used to retrieve real units from (using pixel size information from
     *        metadata).<br>
     *        Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
     * @param displaySpeed
     *        display speed information instead of displacement
     * @param ignoreFilteredDetection
     *        do not compute statistics for filtered detection (see
     *        {@link TrackSegment#isAllDetectionEnabled()})
     * @param outputPath
     *        XLS output path (should contains <i>.xls</i> extension)
     * @param xPlotPanel
     *        optional X plot panel where we will draw the X motion
     * @param yPlotPanel
     *        optional Y plot panel where we will draw the Y motion
     * @param zPlotPanel
     *        optional Z plot panel where we will draw the Z motion
     * @param globalPlotPanel
     *        optional global plot panel where we will draw all the tracks motion
     * @return the filled {@link WritableWorkbook} ready to save on disk.
     * @throws IOException IOException
     * @throws WriteException WriteException
     */
    public static WritableWorkbook getTrackMotionXLS(Collection<TrackSegment> tracks, Sequence sequence,
            boolean displaySpeed, boolean ignoreFilteredDetection, String outputPath, Plot2DPanel xPlotPanel,
            Plot2DPanel yPlotPanel, Plot2DPanel zPlotPanel, PlotPanel globalPlotPanel)
            throws IOException, WriteException
    {
        // create the workbook
        final WritableWorkbook book = XLSUtil.createWorkbook(outputPath);

        // create the sheets
        final WritableSheet globalSheet = XLSUtil.createNewPage(book, "Global");
        final WritableSheet xSheet = XLSUtil.createNewPage(book, "X");
        final WritableSheet ySheet = XLSUtil.createNewPage(book, "Y");
        final WritableSheet zSheet = XLSUtil.createNewPage(book, "Z");

        // write headers
        final String timeUnit = (sequence != null) ? "sec" : "frames";
        final String spaceUnit = (sequence != null) ? "\u03BCm" : "px";
        final String speedUnit = displaySpeed ? "speed (" + spaceUnit + "/" + timeUnit + ")"
                : "disp. (" + spaceUnit + ")";

        final List<WritableSheet> sheets = new ArrayList<WritableSheet>(4);

        sheets.add(globalSheet);
        sheets.add(xSheet);
        sheets.add(ySheet);
        sheets.add(zSheet);

        // write headers
        for (WritableSheet sheet : sheets)
        {
            XLSUtil.setCellString(sheet, COL_TRACK_GP, 0, "Group");
            XLSUtil.setCellString(sheet, COL_TRACK_ID, 0, "Track #");
            XLSUtil.setCellString(sheet, COL_TRACK_START, 0, "Start (" + timeUnit + ")");
            XLSUtil.setCellString(sheet, COL_TRACK_END, 0, "End (" + timeUnit + ")");
            XLSUtil.setCellString(sheet, COL_DURATION, 0, "Duration (" + timeUnit + ")");
            XLSUtil.setCellString(sheet, COL_TOT_DISP, 0, "Total disp. (" + spaceUnit + ")");
            XLSUtil.setCellString(sheet, COL_NET_DISP, 0, "Net disp. (" + spaceUnit + ")");
            XLSUtil.setCellString(sheet, COL_LINEARITY, 0, "Linearity" + " (%)");
            XLSUtil.setCellString(sheet, COL_RADIUS, 0, "Search radius" + " (" + spaceUnit + ")");
            XLSUtil.setCellString(sheet, COL_MIN_DISP, 0, "Min. " + speedUnit);
            XLSUtil.setCellString(sheet, COL_MAX_DISP, 0, "Max. " + speedUnit);
            XLSUtil.setCellString(sheet, COL_AVG_DISP, 0, "Avg. " + speedUnit);
        }

        final List<TrackStatistic> stats = computeTracksStatistics(tracks, sequence, displaySpeed,
                ignoreFilteredDetection, xPlotPanel, yPlotPanel, zPlotPanel, globalPlotPanel);

        int row = 1;
        for (TrackStatistic ts : stats)
        {
            if (ts == null)
                continue;

            // store in the spreadsheet
            XLSUtil.setCellString(globalSheet, COL_TRACK_GP, row, ts.groupName);
            XLSUtil.setCellString(xSheet, COL_TRACK_GP, row, ts.groupName);
            XLSUtil.setCellString(ySheet, COL_TRACK_GP, row, ts.groupName);
            XLSUtil.setCellString(zSheet, COL_TRACK_GP, row, ts.groupName);

            XLSUtil.setCellNumber(globalSheet, COL_TRACK_ID, row, ts.id);
            XLSUtil.setCellNumber(xSheet, COL_TRACK_ID, row, ts.id);
            XLSUtil.setCellNumber(ySheet, COL_TRACK_ID, row, ts.id);
            XLSUtil.setCellNumber(zSheet, COL_TRACK_ID, row, ts.id);

            XLSUtil.setCellNumber(globalSheet, COL_TRACK_START, row, ts.startTime);
            XLSUtil.setCellNumber(xSheet, COL_TRACK_START, row, ts.startTime);
            XLSUtil.setCellNumber(ySheet, COL_TRACK_START, row, ts.startTime);
            XLSUtil.setCellNumber(zSheet, COL_TRACK_START, row, ts.startTime);

            XLSUtil.setCellNumber(globalSheet, COL_TRACK_END, row, ts.endTime);
            XLSUtil.setCellNumber(xSheet, COL_TRACK_END, row, ts.endTime);
            XLSUtil.setCellNumber(ySheet, COL_TRACK_END, row, ts.endTime);
            XLSUtil.setCellNumber(zSheet, COL_TRACK_END, row, ts.endTime);

            XLSUtil.setCellNumber(globalSheet, COL_DURATION, row, ts.duration);
            XLSUtil.setCellNumber(xSheet, COL_DURATION, row, ts.duration);
            XLSUtil.setCellNumber(ySheet, COL_DURATION, row, ts.duration);
            XLSUtil.setCellNumber(zSheet, COL_DURATION, row, ts.duration);

            XLSUtil.setCellNumber(globalSheet, COL_TOT_DISP, row, ts.global.totalDisp);
            XLSUtil.setCellNumber(xSheet, COL_TOT_DISP, row, ts.x.totalDisp);
            XLSUtil.setCellNumber(ySheet, COL_TOT_DISP, row, ts.y.totalDisp);
            XLSUtil.setCellNumber(zSheet, COL_TOT_DISP, row, ts.z.totalDisp);

            XLSUtil.setCellNumber(globalSheet, COL_NET_DISP, row, ts.global.netDisp);
            XLSUtil.setCellNumber(xSheet, COL_NET_DISP, row, ts.x.netDisp);
            XLSUtil.setCellNumber(ySheet, COL_NET_DISP, row, ts.y.netDisp);
            XLSUtil.setCellNumber(zSheet, COL_NET_DISP, row, ts.z.netDisp);

            XLSUtil.setCellNumber(globalSheet, COL_LINEARITY, row, ts.global.linearity);
            XLSUtil.setCellNumber(xSheet, COL_LINEARITY, row, ts.x.linearity);
            XLSUtil.setCellNumber(ySheet, COL_LINEARITY, row, ts.y.linearity);
            XLSUtil.setCellNumber(zSheet, COL_LINEARITY, row, ts.z.linearity);

            XLSUtil.setCellNumber(globalSheet, COL_RADIUS, row, ts.global.searchRadius);
            XLSUtil.setCellNumber(xSheet, COL_RADIUS, row, ts.x.searchRadius);
            XLSUtil.setCellNumber(ySheet, COL_RADIUS, row, ts.y.searchRadius);
            XLSUtil.setCellNumber(zSheet, COL_RADIUS, row, ts.z.searchRadius);

            XLSUtil.setCellNumber(globalSheet, COL_MIN_DISP, row, ts.global.minDispOrSpeed);
            XLSUtil.setCellNumber(xSheet, COL_MIN_DISP, row, ts.x.minDispOrSpeed);
            XLSUtil.setCellNumber(ySheet, COL_MIN_DISP, row, ts.y.minDispOrSpeed);
            XLSUtil.setCellNumber(zSheet, COL_MIN_DISP, row, ts.z.minDispOrSpeed);

            XLSUtil.setCellNumber(globalSheet, COL_MAX_DISP, row, ts.global.maxDispOrSpeed);
            XLSUtil.setCellNumber(xSheet, COL_MAX_DISP, row, ts.x.maxDispOrSpeed);
            XLSUtil.setCellNumber(ySheet, COL_MAX_DISP, row, ts.y.maxDispOrSpeed);
            XLSUtil.setCellNumber(zSheet, COL_MAX_DISP, row, ts.z.maxDispOrSpeed);

            XLSUtil.setCellNumber(globalSheet, COL_AVG_DISP, row, ts.global.meanDispOrSpeed);
            XLSUtil.setCellNumber(xSheet, COL_AVG_DISP, row, ts.x.meanDispOrSpeed);
            XLSUtil.setCellNumber(ySheet, COL_AVG_DISP, row, ts.y.meanDispOrSpeed);
            XLSUtil.setCellNumber(zSheet, COL_AVG_DISP, row, ts.z.meanDispOrSpeed);

            row++;
        }

        return book;
    }

    /**
     * Get motion data in XLS (WorkBook) format from the given list of {@link TrackSegment}.<br>
     * WARNING: you need to explicitly use {@link XLSUtil#saveAndClose(WritableWorkbook)} method to
     * save the obtained XLS into <code>outputPath</code>
     * 
     * @param tracks
     *        tracks we want to export data fort
     * @param sequence
     *        sequence used to retrieve real units from (using pixel size information from
     *        metadata).<br>
     *        Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
     * @param displaySpeed
     *        display speed information instead of displacement
     * @param ignoreFilteredDetection
     *        do not compute statistics for filtered detection (see
     *        {@link TrackSegment#isAllDetectionEnabled()})
     * @param outputPath
     *        XLS output path (should contains <i>.xls</i> extension)
     * @return the filled {@link WritableWorkbook} ready to save on disk.
     */
    public static WritableWorkbook getTrackMotionXLS(Collection<TrackSegment> tracks, Sequence sequence,
            boolean displaySpeed, boolean ignoreFilteredDetection, String outputPath) throws WriteException, IOException
    {
        return getTrackMotionXLS(tracks, sequence, displaySpeed, ignoreFilteredDetection, outputPath, null, null, null,
                null);
    }

    // VAR
    public final Var<TrackGroup> tracks;
    public final VarSequence sequence;
    public final VarBoolean displaySpeed;
    public final VarMutable outputFile;

    public MotionProfiler()
    {
        super();

        tracks = new Var<TrackGroup>("Track group", new TrackGroup(null));
        sequence = new VarSequence("Sequence (for units info.)", null);
        displaySpeed = new VarBoolean("Get speed instead of disp.", false);
        outputFile = new VarMutable("Output file", null)
        {
            @Override
            public boolean isAssignableFrom(@SuppressWarnings("rawtypes") Var source)
            {
                return (String.class == source.getType()) || (File.class == source.getType());
            }
        };
    }

    @Override
    public void declareInput(VarList inputMap)
    {
        inputMap.add("trackgroup", tracks);
        inputMap.add("sequence", sequence);
        inputMap.add("displaySpeed", displaySpeed);
        inputMap.add("output", outputFile);
    }

    @Override
    public void declareOutput(VarList outputMap)
    {
        // nothing here
    }

    @Override
    public void run()
    {
        // execution from protocol
        final TrackGroup trackGroup = tracks.getValue();
        final Object obj = outputFile.getValue();

        if ((trackGroup != null) && (obj != null))
        {
            // build the list of enabled tracks
            final List<TrackSegment> tracksToExport = new ArrayList<TrackSegment>();

            for (TrackSegment track : trackGroup.getTrackSegmentList())
                if (track.isAllDetectionEnabled())
                    tracksToExport.add(track);

            final File f;

            if (obj instanceof String)
                f = new File((String) obj);
            else
                f = (File) obj;

            try
            {
                // get track motion info
                final WritableWorkbook wb = getTrackMotionXLS(tracksToExport, sequence.getValue(),
                        displaySpeed.getValue().booleanValue(), true, f.getAbsolutePath());
                // save them
                XLSUtil.saveAndClose(wb);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getMainPluginClassName()
    {
        return SpeedProfiler.class.getName();
    }
}

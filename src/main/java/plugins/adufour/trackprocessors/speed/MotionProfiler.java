/*
 * Copyright (c) 2010-2023. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.adufour.trackprocessors.speed;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import fr.icy.common.string.StringUtil;
import fr.icy.extension.plugin.abstract_.Plugin;
import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.io.xls.XLSXUtil;
import fr.icy.model.sequence.Sequence;
import org.jetbrains.annotations.NotNull;
import org.math.plot.Plot2DPanel;
import org.math.plot.Plot3DPanel;
import org.math.plot.PlotPanel;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.vars.lang.Var;
import plugins.adufour.vars.lang.VarBoolean;
import plugins.adufour.vars.lang.VarMutable;
import plugins.adufour.vars.lang.VarSequence;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackSegment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Stephane Dallongeville
 */
@IcyPluginName("Motion Profiler")
public class MotionProfiler extends Plugin implements Block {
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
     * @param tracks                  tracks we want to export data fort
     * @param sequence                sequence used to retrieve real units from (using pixel size information from
     *                                metadata).<br>
     *                                Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
     * @param computeSpeed            compute speed information instead of displacement
     * @param ignoreFilteredDetection do not compute statistics for filtered detection (see
     *                                {@link TrackSegment#isAllDetectionEnabled()})
     * @param xPlotPanel              optional X plot panel where we will draw the X motion
     * @param yPlotPanel              optional Y plot panel where we will draw the Y motion
     * @param zPlotPanel              optional Z plot panel where we will draw the Z motion
     * @param globalPlotPanel         optional global plot panel where we will draw all the tracks motion
     * @return List of track statistics
     */
    public static @NotNull List<TrackStatistic> computeTracksStatistics(
            final Collection<TrackSegment> tracks,
            final Sequence sequence,
            final boolean computeSpeed,
            final boolean ignoreFilteredDetection,
            final Plot2DPanel xPlotPanel,
            final Plot2DPanel yPlotPanel,
            final Plot2DPanel zPlotPanel,
            final PlotPanel globalPlotPanel
    ) {
        final List<TrackStatistic> result = new ArrayList<>();
        final double xScale;
        final double yScale;
        final double zScale;
        final double tScale;

        if (sequence != null) {
            xScale = sequence.getPixelSizeX();
            yScale = sequence.getPixelSizeY();
            zScale = sequence.getPixelSizeZ();
            tScale = sequence.getTimeInterval();
        }
        else {
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
        for (final TrackSegment track : tracks) {
            final TrackStatistic stat = TrackStatistic.computeStatistic(
                    track,
                    xScale,
                    yScale,
                    zScale,
                    tScale,
                    true,
                    computeSpeed,
                    ignoreFilteredDetection,
                    xPlotPanel,
                    yPlotPanel,
                    zPlotPanel,
                    globalPlotPanel
            );

            if (stat != null) {
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

        // Center the plot
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
     * WARNING: you need to explicitly use {@link XLSXUtil#saveAndClose(Workbook, File)} method to
     * save the obtained XLS into <code>outputPath</code>
     *
     * @param tracks                  tracks we want to export data fort
     * @param sequence                sequence used to retrieve real units from (using pixel size information from
     *                                metadata).<br>
     *                                Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
     * @param displaySpeed            display speed information instead of displacement
     * @param ignoreFilteredDetection do not compute statistics for filtered detection (see
     *                                {@link TrackSegment#isAllDetectionEnabled()})
     * @param outputPath              XLSX output path (should contains <i>.xlsx</i> extension)
     * @param xPlotPanel              optional X plot panel where we will draw the X motion
     * @param yPlotPanel              optional Y plot panel where we will draw the Y motion
     * @param zPlotPanel              optional Z plot panel where we will draw the Z motion
     * @param globalPlotPanel         optional global plot panel where we will draw all the tracks motion
     * @return the filled {@link Workbook} ready to save on disk.
     * @throws IOException IOException
     */
    public static @NotNull Workbook getTrackMotionXLS(
            final Collection<TrackSegment> tracks,
            final Sequence sequence,
            final boolean displaySpeed,
            final boolean ignoreFilteredDetection,
            final String outputPath,
            final Plot2DPanel xPlotPanel,
            final Plot2DPanel yPlotPanel,
            final Plot2DPanel zPlotPanel,
            final PlotPanel globalPlotPanel
    ) throws IOException {
        // create the workbook
        final File XLSFile = new File(outputPath);

        final Workbook book;
        if (XLSFile.exists())
            book = XLSXUtil.loadWorkbookForWrite(XLSFile);
        else
            book = XLSXUtil.createWorkbook();

        // create the sheets
        final Sheet globalSheet = XLSXUtil.createNewPage(book, "Global");
        final Sheet xSheet = XLSXUtil.createNewPage(book, "X");
        final Sheet ySheet = XLSXUtil.createNewPage(book, "Y");
        final Sheet zSheet = XLSXUtil.createNewPage(book, "Z");

        // write headers
        final String timeUnit = (sequence != null) ? "sec" : "frames";
        final String spaceUnit = (sequence != null) ? "μm" : "px";
        final String speedUnit = displaySpeed ? "speed (" + spaceUnit + "/" + timeUnit + ")" : "disp. (" + spaceUnit + ")";

        final List<Sheet> sheets = new ArrayList<>(4);

        sheets.add(globalSheet);
        sheets.add(xSheet);
        sheets.add(ySheet);
        sheets.add(zSheet);

        // write headers
        for (final Sheet sheet : sheets) {
            XLSXUtil.setCellString(sheet, COL_TRACK_GP, 0, "Group");
            XLSXUtil.setCellString(sheet, COL_TRACK_ID, 0, "Track #");
            XLSXUtil.setCellString(sheet, COL_TRACK_START, 0, "Start (" + timeUnit + ")");
            XLSXUtil.setCellString(sheet, COL_TRACK_END, 0, "End (" + timeUnit + ")");
            XLSXUtil.setCellString(sheet, COL_DURATION, 0, "Duration (" + timeUnit + ")");
            XLSXUtil.setCellString(sheet, COL_TOT_DISP, 0, "Total disp. (" + spaceUnit + ")");
            XLSXUtil.setCellString(sheet, COL_NET_DISP, 0, "Net disp. (" + spaceUnit + ")");
            XLSXUtil.setCellString(sheet, COL_LINEARITY, 0, "Linearity" + " (%)");
            XLSXUtil.setCellString(sheet, COL_RADIUS, 0, "Search radius" + " (" + spaceUnit + ")");
            XLSXUtil.setCellString(sheet, COL_MIN_DISP, 0, "Min. " + speedUnit);
            XLSXUtil.setCellString(sheet, COL_MAX_DISP, 0, "Max. " + speedUnit);
            XLSXUtil.setCellString(sheet, COL_AVG_DISP, 0, "Avg. " + speedUnit);
        }

        final List<TrackStatistic> stats = computeTracksStatistics(
                tracks,
                sequence,
                displaySpeed,
                ignoreFilteredDetection,
                xPlotPanel,
                yPlotPanel,
                zPlotPanel,
                globalPlotPanel
        );

        int row = 1;
        for (final TrackStatistic ts : stats) {
            if (ts == null)
                continue;

            // store in the spreadsheet
            XLSXUtil.setCellString(globalSheet, COL_TRACK_GP, row, ts.groupName);
            XLSXUtil.setCellString(xSheet, COL_TRACK_GP, row, ts.groupName);
            XLSXUtil.setCellString(ySheet, COL_TRACK_GP, row, ts.groupName);
            XLSXUtil.setCellString(zSheet, COL_TRACK_GP, row, ts.groupName);

            XLSXUtil.setCellNumber(globalSheet, COL_TRACK_ID, row, ts.id);
            XLSXUtil.setCellNumber(xSheet, COL_TRACK_ID, row, ts.id);
            XLSXUtil.setCellNumber(ySheet, COL_TRACK_ID, row, ts.id);
            XLSXUtil.setCellNumber(zSheet, COL_TRACK_ID, row, ts.id);

            XLSXUtil.setCellNumber(globalSheet, COL_TRACK_START, row, ts.startTime);
            XLSXUtil.setCellNumber(xSheet, COL_TRACK_START, row, ts.startTime);
            XLSXUtil.setCellNumber(ySheet, COL_TRACK_START, row, ts.startTime);
            XLSXUtil.setCellNumber(zSheet, COL_TRACK_START, row, ts.startTime);

            XLSXUtil.setCellNumber(globalSheet, COL_TRACK_END, row, ts.endTime);
            XLSXUtil.setCellNumber(xSheet, COL_TRACK_END, row, ts.endTime);
            XLSXUtil.setCellNumber(ySheet, COL_TRACK_END, row, ts.endTime);
            XLSXUtil.setCellNumber(zSheet, COL_TRACK_END, row, ts.endTime);

            XLSXUtil.setCellNumber(globalSheet, COL_DURATION, row, ts.duration);
            XLSXUtil.setCellNumber(xSheet, COL_DURATION, row, ts.duration);
            XLSXUtil.setCellNumber(ySheet, COL_DURATION, row, ts.duration);
            XLSXUtil.setCellNumber(zSheet, COL_DURATION, row, ts.duration);

            XLSXUtil.setCellNumber(globalSheet, COL_TOT_DISP, row, ts.global.totalDisp);
            XLSXUtil.setCellNumber(xSheet, COL_TOT_DISP, row, ts.x.totalDisp);
            XLSXUtil.setCellNumber(ySheet, COL_TOT_DISP, row, ts.y.totalDisp);
            XLSXUtil.setCellNumber(zSheet, COL_TOT_DISP, row, ts.z.totalDisp);

            XLSXUtil.setCellNumber(globalSheet, COL_NET_DISP, row, ts.global.netDisp);
            XLSXUtil.setCellNumber(xSheet, COL_NET_DISP, row, ts.x.netDisp);
            XLSXUtil.setCellNumber(ySheet, COL_NET_DISP, row, ts.y.netDisp);
            XLSXUtil.setCellNumber(zSheet, COL_NET_DISP, row, ts.z.netDisp);

            XLSXUtil.setCellNumber(globalSheet, COL_LINEARITY, row, ts.global.linearity);
            XLSXUtil.setCellNumber(xSheet, COL_LINEARITY, row, ts.x.linearity);
            XLSXUtil.setCellNumber(ySheet, COL_LINEARITY, row, ts.y.linearity);
            XLSXUtil.setCellNumber(zSheet, COL_LINEARITY, row, ts.z.linearity);

            XLSXUtil.setCellNumber(globalSheet, COL_RADIUS, row, ts.global.searchRadius);
            XLSXUtil.setCellNumber(xSheet, COL_RADIUS, row, ts.x.searchRadius);
            XLSXUtil.setCellNumber(ySheet, COL_RADIUS, row, ts.y.searchRadius);
            XLSXUtil.setCellNumber(zSheet, COL_RADIUS, row, ts.z.searchRadius);

            XLSXUtil.setCellNumber(globalSheet, COL_MIN_DISP, row, ts.global.minDispOrSpeed);
            XLSXUtil.setCellNumber(xSheet, COL_MIN_DISP, row, ts.x.minDispOrSpeed);
            XLSXUtil.setCellNumber(ySheet, COL_MIN_DISP, row, ts.y.minDispOrSpeed);
            XLSXUtil.setCellNumber(zSheet, COL_MIN_DISP, row, ts.z.minDispOrSpeed);

            XLSXUtil.setCellNumber(globalSheet, COL_MAX_DISP, row, ts.global.maxDispOrSpeed);
            XLSXUtil.setCellNumber(xSheet, COL_MAX_DISP, row, ts.x.maxDispOrSpeed);
            XLSXUtil.setCellNumber(ySheet, COL_MAX_DISP, row, ts.y.maxDispOrSpeed);
            XLSXUtil.setCellNumber(zSheet, COL_MAX_DISP, row, ts.z.maxDispOrSpeed);

            XLSXUtil.setCellNumber(globalSheet, COL_AVG_DISP, row, ts.global.meanDispOrSpeed);
            XLSXUtil.setCellNumber(xSheet, COL_AVG_DISP, row, ts.x.meanDispOrSpeed);
            XLSXUtil.setCellNumber(ySheet, COL_AVG_DISP, row, ts.y.meanDispOrSpeed);
            XLSXUtil.setCellNumber(zSheet, COL_AVG_DISP, row, ts.z.meanDispOrSpeed);

            row++;
        }

        return book;
    }

    /**
     * Get motion data in XLS (WorkBook) format from the given list of {@link TrackSegment}.<br>
     * WARNING: you need to explicitly use {@link XLSXUtil#saveAndClose(Workbook, File)} method to
     * save the obtained XLS into <code>outputPath</code>
     *
     * @param tracks                  tracks we want to export data fort
     * @param sequence                sequence used to retrieve real units from (using pixel size information from
     *                                metadata).<br>
     *                                Keep this value to <code>null</code> if you want use raw pixel / frame units instead.
     * @param displaySpeed            display speed information instead of displacement
     * @param ignoreFilteredDetection do not compute statistics for filtered detection (see
     *                                {@link TrackSegment#isAllDetectionEnabled()})
     * @param outputPath              XLS output path (should contains <i>.xls</i> extension)
     * @return the filled {@link Workbook} ready to save on disk.
     */
    public static @NotNull Workbook getTrackMotionXLS(
            final Collection<TrackSegment> tracks,
            final Sequence sequence,
            final boolean displaySpeed,
            final boolean ignoreFilteredDetection,
            final String outputPath
    ) throws IOException {
        return getTrackMotionXLS(
                tracks,
                sequence,
                displaySpeed,
                ignoreFilteredDetection,
                outputPath,
                null,
                null,
                null,
                null
        );
    }

    // VAR
    public final Var<TrackGroup> tracks;
    public final VarSequence sequence;
    public final VarBoolean displaySpeed;
    public final VarMutable outputFile;

    public MotionProfiler() {
        super();

        tracks = new Var<>("Track group", new TrackGroup(null));
        sequence = new VarSequence("Sequence (for units info.)", null);
        displaySpeed = new VarBoolean("Get speed instead of disp.", false);
        outputFile = new VarMutable("Output file (XLSX)", null) {
            @Override
            public boolean isAssignableFrom(final Var source) {
                return (String.class == source.getType()) || (File.class == source.getType());
            }
        };
    }

    @Override
    public void declareInput(final @NotNull VarList inputMap) {
        inputMap.add("trackgroup", tracks);
        inputMap.add("sequence", sequence);
        inputMap.add("displaySpeed", displaySpeed);
        inputMap.add("output", outputFile);
    }

    @Override
    public void declareOutput(final VarList outputMap) {
        // nothing here
    }

    @Override
    public void run() {
        // execution from protocol
        final TrackGroup trackGroup = tracks.getValue();
        final Object obj = outputFile.getValue();

        if ((trackGroup != null) && (obj != null)) {
            // build the list of enabled tracks
            final List<TrackSegment> tracksToExport = new ArrayList<>();

            for (final TrackSegment track : trackGroup.getTrackSegmentList())
                if (track.isAllDetectionEnabled())
                    tracksToExport.add(track);

            final File f;

            if (obj instanceof String)
                f = new File((String) obj);
            else
                f = (File) obj;

            try {
                // get track motion info
                final Workbook wb = getTrackMotionXLS(
                        tracksToExport,
                        sequence.getValue(),
                        displaySpeed.getValue(),
                        true,
                        f.getAbsolutePath()
                );
                // save them
                XLSXUtil.saveAndClose(wb, f);
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

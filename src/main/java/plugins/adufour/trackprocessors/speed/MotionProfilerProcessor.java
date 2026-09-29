/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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
import fr.icy.extension.plugin.annotation_.IcyPluginName;
import fr.icy.gui.component.scrollpane.ExcelTable;
import fr.icy.gui.dialog.MessageDialog;
import fr.icy.gui.dialog.SaveDialog;
import fr.icy.gui.frame.progress.AnnounceFrame;
import fr.icy.io.FileUtil;
import fr.icy.io.xls.XLSXUtil;
import fr.icy.model.sequence.Sequence;
import fr.icy.system.preferences.XMLPreferences;
import org.jetbrains.annotations.NotNull;
import org.math.plot.Plot2DPanel;
import org.math.plot.Plot3DPanel;
import org.math.plot.PlotPanel;
import plugins.fab.trackmanager.PluginTrackManagerProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@IcyPluginName("Motion Profiler Processor")
public class MotionProfilerProcessor extends PluginTrackManagerProcessor {
    private static final Logger LOGGER = Logger.getLogger(MotionProfilerProcessor.class.getName());

    private JCheckBox useRealUnits;
    private JCheckBox showSpeed;

    private JTabbedPane statistics = new JTabbedPane();
    private JPanel xTab = new JPanel();
    private Plot2DPanel xPlot = new Plot2DPanel();
    private JPanel yTab = new JPanel();
    private Plot2DPanel yPlot = new Plot2DPanel();
    private JPanel zTab = new JPanel();
    private Plot2DPanel zPlot = new Plot2DPanel();
    private JPanel globalTab = new JPanel();
    private PlotPanel globalPlot;

    // internal
    XMLPreferences preferences;

    private final ActionListener updater = e -> trackPool.fireTrackEditorProcessorChange();

    public MotionProfilerProcessor() {
        super();

        preferences = getPreferencesRoot();

        setName("Motion Profiler");
        buildGui();
    }

    void buildGui() {
        final JPanel options = new JPanel();
        useRealUnits = new JCheckBox("Use real units");
        showSpeed = new JCheckBox("Show speed instead of displacement");

        statistics = new JTabbedPane();
        xTab = new JPanel();
        xPlot = new Plot2DPanel();
        yTab = new JPanel();
        yPlot = new Plot2DPanel();
        zTab = new JPanel();
        zPlot = new Plot2DPanel();
        globalTab = new JPanel();
        final JButton export = new JButton("Export statistics...");

        // initialize tabs
        xTab.setLayout(new BoxLayout(xTab, BoxLayout.Y_AXIS));
        yTab.setLayout(new BoxLayout(yTab, BoxLayout.Y_AXIS));
        zTab.setLayout(new BoxLayout(zTab, BoxLayout.Y_AXIS));
        globalTab.setLayout(new BoxLayout(globalTab, BoxLayout.Y_AXIS));
        statistics.addTab("Global", globalTab);
        statistics.addTab("Along X", xTab);
        statistics.addTab("Along Y", yTab);
        statistics.addTab("Along Z", zTab);

        xPlot.setAxisLabels("Time", "Displacement along X");
        yPlot.setAxisLabels("Time", "Displacement along Y");
        zPlot.setAxisLabels("Time", "Displacement along Z");

        // set the tab pane's height
        final Dimension dim = statistics.getPreferredSize();
        dim.height = 900;
        statistics.setPreferredSize(dim);

        // options panel
        useRealUnits.setSelected(false);
        useRealUnits.addActionListener(updater);

        showSpeed.setSelected(false);
        showSpeed.addActionListener(updater);

        options.setLayout(new BoxLayout(options, BoxLayout.X_AXIS));
        options.add(useRealUnits);
        options.add(showSpeed);
        options.add(Box.createHorizontalGlue());
        options.add(export);

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(options);
        panel.add(statistics);

        // make the button do something
        export.addActionListener(arg0 -> new Thread(() -> {
            // Restore last used folder
            final String xlsFolder = preferences.get("xlsFolder", null);
            final String path = SaveDialog.chooseFile("Export motion data", xlsFolder, "Speed", ".xlsx");

            if (path == null)
                return;

            // store the folder in the preferences
            preferences.put("xlsFolder", FileUtil.getDirectory(path));

            final AnnounceFrame message = new AnnounceFrame("Saving motion data...", 0);
            try {
                export(path);
            }
            finally {
                message.close();
            }
        }).start());
    }

    @Override
    public void Close() {
        //
    }

    @Override
    public void Compute() {
        if (!isEnabled())
            return;

        final Sequence sequence = trackPool.getDisplaySequence();

        if ((sequence == null) && useRealUnits.isSelected())
            useRealUnits.setSelected(false);

        // can't use real unit when sequence is not defined
        useRealUnits.setEnabled(sequence != null);

        // remove / clean graphical components while we work on them
        panel.setVisible(false);
        try {
            // prepare GUI to receive results
            globalTab.removeAll();
            xTab.removeAll();
            yTab.removeAll();
            zTab.removeAll();

            if (statistics.getTabCount() > 3)
                statistics.removeTabAt(3);

            final boolean show3D = ((sequence == null) || sequence.getSizeZ() > 1);

            if (show3D) {
                if ((globalPlot == null) || (globalPlot instanceof Plot2DPanel)) {
                    globalPlot = new Plot3DPanel();
                    globalPlot.setAxisLabels("X", "Y", "Z");
                }
                // add plot Z panel
                statistics.addTab("Along Z", zTab);
            }
            else {
                if ((globalPlot == null) || (globalPlot instanceof Plot3DPanel)) {
                    globalPlot = new Plot2DPanel();
                    globalPlot.setAxisLabels("X", "Y");
                }
            }

            try {
                // dummy XLS file
                final File tmpFile = new File(FileUtil.getTempDirectory(), "Motion profiler.xlsx");
                //final File tmpFile = File.createTempFile("Motion profiler", "xls");
                // get workbook
                final Workbook workbook = getStatistics(tmpFile.getAbsolutePath(), xPlot, yPlot, zPlot, globalPlot);

                // get the generated sheets
                //final Sheet globalSheet = workbook.getSheet("Global");
                final Sheet globalSheet = XLSXUtil.getPage(workbook, "Global");
                //final Sheet xSheet = workbook.getSheet("X");
                final Sheet xSheet = XLSXUtil.getPage(workbook, "X");
                //final Sheet ySheet = workbook.getSheet("Y");
                final Sheet ySheet = XLSXUtil.getPage(workbook, "Y");
                //final Sheet zSheet = workbook.getSheet("Z");
                final Sheet zSheet = XLSXUtil.getPage(workbook, "Z");

                // X display
                final ExcelTable xTable = new ExcelTable(xSheet);
                xTable.setPreferredSize(new Dimension(0, 200));
                xTab.add(xTable);
                xPlot.setPreferredSize(new Dimension(0, 300));
                xTab.add(xPlot);

                // Y display
                final ExcelTable yTable = new ExcelTable(ySheet);
                yTable.setPreferredSize(new Dimension(0, 200));
                yTab.add(yTable);
                yPlot.setPreferredSize(new Dimension(0, 300));
                yTab.add(yPlot);

                if (show3D) {
                    // Z display
                    final ExcelTable zTable = new ExcelTable(zSheet);
                    zTable.setPreferredSize(new Dimension(0, 200));
                    zTab.add(zTable);
                    zPlot.setPreferredSize(new Dimension(0, 300));
                    zTab.add(zPlot);
                }

                // global display
                final ExcelTable globalTable = new ExcelTable(globalSheet);
                globalTable.setPreferredSize(new Dimension(0, 200));
                globalTab.add(globalTable);
                globalPlot.setPreferredSize(new Dimension(0, 600));
                globalTab.add(globalPlot);

                // cleanup
                workbook.close();
                if (!tmpFile.delete())
                    tmpFile.deleteOnExit();
            }
            catch (final Exception e) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Unable to generate XLSX motion statistics.", e);
            }
        }
        finally {
            panel.setVisible(true);
        }
    }

    @Override
    public void displaySequenceChanged() {
        Compute();
    }

    private @NotNull Workbook getStatistics(
            final String path,
            final Plot2DPanel xPlotPanel,
            final Plot2DPanel yPlotPanel,
            final Plot2DPanel zPlotPanel,
            final PlotPanel globalPlotPanel
    ) throws IOException {
        // get workbook from temp XLS file
        return MotionProfiler.getTrackMotionXLS(
                trackPool.getTrackSegmentList(),
                useRealUnits.isSelected() ? trackPool.getDisplaySequence() : null,
                showSpeed.isSelected(),
                true,
                path,
                xPlotPanel,
                yPlotPanel,
                zPlotPanel,
                globalPlotPanel
        );
    }

    private @NotNull Workbook getStatistics(final String path) throws IOException {
        // get workbook from temp XLS file
        return getStatistics(path, null, null, null, null);
    }

    void export(final String path) {
        try {
            // get workbook with wanted output path
            final Workbook wb = getStatistics(path);
            // save them
            XLSXUtil.saveAndClose(wb, new File(path));
        }
        catch (final Exception e) {
            MessageDialog.showDialog("Cannot export file", JOptionPane.ERROR_MESSAGE);
        }
    }
}

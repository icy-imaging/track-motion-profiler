package plugins.adufour.trackprocessors.speed;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.math.plot.Plot2DPanel;
import org.math.plot.Plot3DPanel;
import org.math.plot.PlotPanel;

import icy.file.FileUtil;
import icy.gui.component.ExcelTable;
import icy.gui.dialog.MessageDialog;
import icy.gui.dialog.SaveDialog;
import icy.gui.frame.progress.AnnounceFrame;
import icy.plugin.interface_.PluginBundled;
import icy.preferences.XMLPreferences;
import icy.sequence.Sequence;
import icy.system.IcyExceptionHandler;
import icy.util.XLSUtil;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import plugins.fab.trackmanager.PluginTrackManagerProcessor;

public class MotionProfilerProcessor extends PluginTrackManagerProcessor implements PluginBundled
{
    // GUI
    private JPanel options;
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
    private JButton export = new JButton("Export statistics...");

    // internal
    XMLPreferences preferences;

    private ActionListener updater = new ActionListener()
    {
        @Override
        public void actionPerformed(ActionEvent arg0)
        {
            trackPool.fireTrackEditorProcessorChange();
        }
    };

    public MotionProfilerProcessor()
    {
        super();

        preferences = getPreferencesRoot();

        setName("Motion Profiler");
        buildGui();
    }

    void buildGui()
    {
        options = new JPanel();
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
        export = new JButton("Export statistics...");

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
        Dimension dim = statistics.getPreferredSize();
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
        export.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        // Restore last used folder
                        String xlsFolder = preferences.get("xlsFolder", null);
                        String path = SaveDialog.chooseFile("Export motion data", xlsFolder, "Speed", ".xls");

                        if (path == null)
                            return;

                        // store the folder in the preferences
                        preferences.put("xlsFolder", FileUtil.getDirectory(path));

                        AnnounceFrame message = new AnnounceFrame("Saving motion data...", 0);
                        try
                        {
                            export(path);
                        }
                        finally
                        {
                            message.close();
                        }
                    }
                }.start();
            }
        });
    }

    @Override
    public void Close()
    {
        //
    }

    @Override
    public void Compute()
    {
        if (!isEnabled())
            return;

        final Sequence sequence = trackPool.getDisplaySequence();

        if ((sequence == null) && useRealUnits.isSelected())
            useRealUnits.setSelected(false);

        // can't use real unit when sequence is not defined
        useRealUnits.setEnabled(sequence != null);

        // remove / clean graphical components while we work on them
        panel.setVisible(false);
        try
        {
            // prepare GUI to receive results
            globalTab.removeAll();
            xTab.removeAll();
            yTab.removeAll();
            zTab.removeAll();

            if (statistics.getTabCount() > 3)
                statistics.removeTabAt(3);

            final boolean show3D = ((sequence == null) || sequence.getSizeZ() > 1);

            if (show3D)
            {
                if ((globalPlot == null) || (globalPlot instanceof Plot2DPanel))
                {
                    globalPlot = new Plot3DPanel();
                    globalPlot.setAxisLabels("X", "Y", "Z");
                }
                // add plot Z panel
                statistics.addTab("Along Z", zTab);
            }
            else
            {
                if ((globalPlot == null) || (globalPlot instanceof Plot3DPanel))
                {
                    globalPlot = new Plot2DPanel();
                    globalPlot.setAxisLabels("X", "Y");
                }
            }

            try
            {
                // dummy XLS file
                final File tmpFile = File.createTempFile("Motion profiler", "xls");
                // get workbook
                final WritableWorkbook workbook = getStatistics(tmpFile.getAbsolutePath(), xPlot, yPlot, zPlot,
                        globalPlot);

                // get the generated sheets
                final WritableSheet globalSheet = workbook.getSheet("Global");
                final WritableSheet xSheet = workbook.getSheet("X");
                final WritableSheet ySheet = workbook.getSheet("Y");
                final WritableSheet zSheet = workbook.getSheet("Z");

                // X display
                ExcelTable xTable = new ExcelTable(xSheet);
                xTable.setPreferredSize(new Dimension(0, 200));
                xTab.add(xTable);
                xPlot.setPreferredSize(new Dimension(0, 300));
                xTab.add(xPlot);

                // Y display
                ExcelTable yTable = new ExcelTable(ySheet);
                yTable.setPreferredSize(new Dimension(0, 200));
                yTab.add(yTable);
                yPlot.setPreferredSize(new Dimension(0, 300));
                yTab.add(yPlot);

                if (show3D)
                {
                    // Z display
                    ExcelTable zTable = new ExcelTable(zSheet);
                    zTable.setPreferredSize(new Dimension(0, 200));
                    zTab.add(zTable);
                    zPlot.setPreferredSize(new Dimension(0, 300));
                    zTab.add(zPlot);
                }

                // global display
                ExcelTable globalTable = new ExcelTable(globalSheet);
                globalTable.setPreferredSize(new Dimension(0, 200));
                globalTab.add(globalTable);
                globalPlot.setPreferredSize(new Dimension(0, 600));
                globalTab.add(globalPlot);

                // cleanup
                workbook.close();
                if (!tmpFile.delete())
                    tmpFile.deleteOnExit();
            }
            catch (Exception e)
            {
                System.err.println("[Motion Profiler] Unable to generate XLS motion statistics;");
                IcyExceptionHandler.showErrorMessage(e, true);
                return;
            }
        }
        finally
        {
            panel.setVisible(true);
        }
    }

    @Override
    public void displaySequenceChanged()
    {
        Compute();
    }

    private WritableWorkbook getStatistics(String path, Plot2DPanel xPlotPanel, Plot2DPanel yPlotPanel,
            Plot2DPanel zPlotPanel, PlotPanel globalPlotPanel) throws WriteException, IOException
    {
        // get workbook from temp XLS file
        return MotionProfiler.getTrackMotionXLS(trackPool.getTrackSegmentList(),
                useRealUnits.isSelected() ? trackPool.getDisplaySequence() : null, showSpeed.isSelected(), true, path,
                xPlotPanel, yPlotPanel, zPlotPanel, globalPlotPanel);
    }

    private WritableWorkbook getStatistics(String path) throws WriteException, IOException
    {
        // get workbook from temp XLS file
        return getStatistics(path, null, null, null, null);
    }

    void export(String path)
    {
        try
        {
            // get workbook with wanted output path
            final WritableWorkbook wb = getStatistics(path);
            // save them
            XLSUtil.saveAndClose(wb);
        }
        catch (Exception e)
        {
            MessageDialog.showDialog("Cannot export file", MessageDialog.ERROR_MESSAGE);
        }
    }

    @Override
    public String getMainPluginClassName()
    {
        return SpeedProfiler.class.getName();
    }
}

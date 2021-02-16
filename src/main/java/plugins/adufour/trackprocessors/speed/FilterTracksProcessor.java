package plugins.adufour.trackprocessors.speed;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.gui.component.NumberTextField;
import icy.gui.component.NumberTextField.ValueChangeListener;
import icy.main.Icy;
import icy.painter.Overlay;
import icy.plugin.interface_.PluginBundled;
import icy.sequence.Sequence;
import plugins.adufour.trackprocessors.speed.FilterTracks.ComparisonType;
import plugins.adufour.trackprocessors.speed.FilterTracks.Criterion;
import plugins.fab.trackmanager.PluginTrackManagerProcessor;
import plugins.fab.trackmanager.TrackSegment;

public class FilterTracksProcessor extends PluginTrackManagerProcessor implements PluginBundled
{
    // GUI
    private JPanel options;
    private JLabel dispUnit;
    private JPanel filter_panel;

    // VAR
    private JCheckBox useRealUnits;
    private JComboBox filter_criterion;
    private JComboBox filter_operation;
    private NumberTextField filter_value;

    // INTERNAL
    private final ActionListener updater;

    public FilterTracksProcessor()
    {
        super();

        updater = new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                updateUnit();
                trackPool.fireTrackEditorProcessorChange();
            }
        };
        // discardedTracks = new HashSet<TrackSegment>();

        setName("Track filter");

        if (!Icy.getMainInterface().isHeadLess())
            buildGui();
        updateUnit();
    }

    private void buildGui()
    {
        options = new JPanel();
        filter_panel = new JPanel();
        dispUnit = new JLabel();

        useRealUnits = new JCheckBox("Use real units (\u03BCm/sec)", false);
        useRealUnits.addActionListener(updater);
        filter_criterion = new JComboBox(Criterion.values());
        filter_operation = new JComboBox(ComparisonType.values());
        filter_value = new NumberTextField(false);

        // OPTIONS
        options.setLayout(new BoxLayout(options, BoxLayout.X_AXIS));
        options.add(useRealUnits);
        options.add(Box.createHorizontalGlue());

        // FILTERS
        filter_panel.setLayout(new BoxLayout(filter_panel, BoxLayout.X_AXIS));
        filter_panel.add(new JLabel(" Keep tracks with "));
        filter_criterion.setFocusable(false);
        filter_criterion.addActionListener(updater);
        filter_panel.add(filter_criterion);
        filter_operation.setFocusable(false);
        filter_operation.addActionListener(updater);
        filter_panel.add(filter_operation);
        filter_panel.add(filter_value);
        filter_panel.add(dispUnit);
        filter_value.setNumericValue(3);
        filter_value.addValueListener(new ValueChangeListener()
        {
            @Override
            public void valueChanged(double newValue, boolean validate)
            {
                if (validate)
                    trackPool.fireTrackEditorProcessorChange();
            }
        });

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(options);
        panel.add(filter_panel);
    }

    void updateUnit()
    {
        switch ((Criterion) filter_criterion.getSelectedItem())
        {
            case CRITERION_T_START:
            case CRITERION_CURRENT_T:
            case CRITERION_T_END:
            case CRITERION_DURATION:
                dispUnit.setText(useRealUnits.isSelected() ? "sec" : "frames");
                break;

            case CRITERION_EXTENT:
            case CRITERION_TOT_DISP:
            case CRITERION_NET_DISP:
                dispUnit.setText(useRealUnits.isSelected() ? "\u03BCm" : "px");
                break;

            case CRITERION_MIN_DISP:
            case CRITERION_MAX_DISP:
            case CRITERION_AVG_DISP:
                dispUnit.setText(useRealUnits.isSelected() ? "\u03BCm/sec" : "px/frame");
                break;

            case CRITERION_LINEARITY:
                dispUnit.setText("%");
                break;

            default:
                dispUnit.setText("");
                break;
        }
    }

    @Override
    public void Compute()
    {
        if (!isEnabled())
            return;

        final Sequence sequence = trackPool.getDisplaySequence();

        if (sequence == null && useRealUnits.isSelected())
            useRealUnits.setSelected(false);

        // can't use real unit when sequence is not defined
        useRealUnits.setEnabled(sequence != null);

        // build the list of enabled tracks
        final List<TrackSegment> tracksToFilter = new ArrayList<TrackSegment>();

        for (TrackSegment ts : this.trackPool.getTrackSegmentList())
            if (FilterTracks.isTrackEnabled(ts))
                tracksToFilter.add(ts);

        FilterTracks.filterTracks(tracksToFilter, (Criterion) filter_criterion.getSelectedItem(),
                (ComparisonType) filter_operation.getSelectedItem(), filter_value.getNumericValue(),
                useRealUnits.isSelected() ? sequence : null, true);

        if (sequence != null)
            sequence.overlayChanged((Overlay) null);
    }

    @Override
    public void Close()
    {
        // nothing to do here
    }

    @Override
    public void displaySequenceChanged()
    {
        Compute();
    }

    @Override
    public String getMainPluginClassName()
    {
        return SpeedProfiler.class.getName();
    }
}

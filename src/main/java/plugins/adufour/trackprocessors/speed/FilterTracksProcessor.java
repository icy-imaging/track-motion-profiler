/*
 * Copyright (c) 2010-2024. Institut Pasteur.
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

import org.bioimageanalysis.icy.Icy;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginIcon;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.bioimageanalysis.icy.gui.component.field.NumberTextField;
import org.bioimageanalysis.icy.model.overlay.Overlay;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import plugins.adufour.trackprocessors.speed.FilterTracks.ComparisonType;
import plugins.adufour.trackprocessors.speed.FilterTracks.Criterion;
import plugins.fab.trackmanager.PluginTrackManagerProcessor;
import plugins.fab.trackmanager.TrackSegment;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

@IcyPluginName("Filter Tracks Processor")
@IcyPluginIcon(path = "/motion-profiler.png")
public class FilterTracksProcessor extends PluginTrackManagerProcessor {
    private JLabel dispUnit;

    // VAR
    private JCheckBox useRealUnits;
    private JComboBox<Criterion> filter_criterion;
    private JComboBox<ComparisonType> filter_operation;
    private NumberTextField filter_value;

    // INTERNAL
    private final ActionListener updater;

    public FilterTracksProcessor() {
        super();

        updater = arg0 -> {
            updateUnit();
            trackPool.fireTrackEditorProcessorChange();
        };
        // discardedTracks = new HashSet<TrackSegment>();

        setName("Track filter");

        if (!Icy.getMainInterface().isHeadLess())
            buildGui();
        updateUnit();
    }

    private void buildGui() {
        // GUI
        final JPanel options = new JPanel();
        final JPanel filter_panel = new JPanel();
        dispUnit = new JLabel();

        useRealUnits = new JCheckBox("Use real units (μm/sec)", false);
        useRealUnits.addActionListener(updater);
        filter_criterion = new JComboBox<>(Criterion.values());
        filter_operation = new JComboBox<>(ComparisonType.values());
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
        filter_value.addValueListener((newValue, validate) -> {
            if (validate)
                trackPool.fireTrackEditorProcessorChange();
        });

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(options);
        panel.add(filter_panel);
    }

    void updateUnit() {
        final Criterion item = (Criterion) filter_criterion.getSelectedItem();
        assert item != null;
        switch (item) {
            case CRITERION_T_START:
            case CRITERION_CURRENT_T:
            case CRITERION_T_END:
            case CRITERION_DURATION:
                dispUnit.setText(useRealUnits.isSelected() ? "sec" : "frames");
                break;

            case CRITERION_EXTENT:
            case CRITERION_TOT_DISP:
            case CRITERION_NET_DISP:
                dispUnit.setText(useRealUnits.isSelected() ? "μm" : "px");
                break;

            case CRITERION_MIN_DISP:
            case CRITERION_MAX_DISP:
            case CRITERION_AVG_DISP:
                dispUnit.setText(useRealUnits.isSelected() ? "μm/sec" : "px/frame");
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
    public void Compute() {
        if (!isEnabled())
            return;

        final Sequence sequence = trackPool.getDisplaySequence();

        if (sequence == null && useRealUnits.isSelected())
            useRealUnits.setSelected(false);

        // can't use real unit when sequence is not defined
        useRealUnits.setEnabled(sequence != null);

        // build the list of enabled tracks
        final List<TrackSegment> tracksToFilter = new ArrayList<>();

        for (final TrackSegment ts : this.trackPool.getTrackSegmentList())
            if (FilterTracks.isTrackEnabled(ts))
                tracksToFilter.add(ts);

        FilterTracks.filterTracks(tracksToFilter, (Criterion) filter_criterion.getSelectedItem(),
                (ComparisonType) filter_operation.getSelectedItem(), filter_value.getNumericValue(),
                useRealUnits.isSelected() ? sequence : null, true);

        if (sequence != null)
            sequence.overlayChanged((Overlay) null);
    }

    @Override
    public void Close() {
        // nothing to do here
    }

    @Override
    public void displaySequenceChanged() {
        Compute();
    }
}

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

import org.bioimageanalysis.icy.extension.plugin.abstract_.Plugin;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginIcon;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;

/**
 * TrackManager plug-in that compute motion and speed statistics from tracks and filter them based
 * on these statistics
 *
 * @author Alexandre Dufour
 */
@IcyPluginName("Speed Profiler")
@IcyPluginIcon(path = "/motion-profiler.png")
public class SpeedProfiler extends Plugin {
    // old class name, need to preserve it to identify the plugin.
    public SpeedProfiler() {
        // nothing to do here, just a container for 2 plugins
    }
}

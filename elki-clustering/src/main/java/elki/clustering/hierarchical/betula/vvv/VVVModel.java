/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 * 
 * Copyright (C) 2020
 * ELKI Development Team
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package elki.clustering.hierarchical.betula.vvv;

import elki.clustering.hierarchical.betula.CFModel;
import elki.clustering.hierarchical.betula.distance.CFDistance;
import elki.clustering.hierarchical.betula.distance.RadiusDistance;
import elki.clustering.hierarchical.betula.distance.VarianceIncreaseDistance;
import elki.utilities.optionhandling.OptionID;
import elki.utilities.optionhandling.Parameterizer;
import elki.utilities.optionhandling.parameterization.Parameterization;
import elki.utilities.optionhandling.parameters.ObjectParameter;

/**
 * Factory for the generation of Clustering Features
 * 
 * @author Andreas Lang
 */
public final class VVVModel implements CFModel<ClusteringFeature> {
  /**
   * BIRCH distance function to use
   */
  CFDistance dist;

  /**
   * BIRCH distance function to use for point absorption
   */
  CFDistance abs;

  /**
   * Constructor.
   *
   * @param dist Distance Function
   * @param abs Absorption Criteria
   */
  public VVVModel(CFDistance dist, CFDistance abs) {
    this.dist = dist;
    this.abs = abs;
  }

  @Override
  public ClusteringFeature make(int d) {
    return new ClusteringFeature(d);
  }

  @Override
  public TreeNode treeNode(int d, int capacity) {
    return new TreeNode(d, capacity);
  }

  /**
   * Parameterization class for CFTrees.
   *
   * @author Andreas Lang
   */
  public static class Par implements Parameterizer {
    /**
     * Distance function parameter.
     */
    public static final OptionID DISTANCE_ID = new OptionID("cftree.distance", "Distance function to use for node assignment.");

    /**
     * Absorption parameter.
     */
    public static final OptionID ABSORPTION_ID = new OptionID("cftree.absorption", "Absorption criterion to use.");

    /**
     * BIRCH distance function to use
     */
    CFDistance dist;

    /**
     * BIRCH distance function to use for point absorption
     */
    CFDistance abs;

    @Override
    public void configure(Parameterization config) {
      new ObjectParameter<CFDistance>(DISTANCE_ID, CFDistance.class, VarianceIncreaseDistance.class) //
          .grab(config, x -> dist = x);
      new ObjectParameter<CFDistance>(ABSORPTION_ID, CFDistance.class, RadiusDistance.class) //
          .grab(config, x -> abs = x);
    }

    @Override
    public VVVModel make() {
      return new VVVModel(dist, abs);
    }
  }

  @Override
  public CFDistance distance() {
    return dist;
  }

  @Override
  public CFDistance absorption() {
    return abs;
  }
}

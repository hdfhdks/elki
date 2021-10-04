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
package elki.clustering.hierarchical.betula.vvi;

import elki.data.NumberVector;
import elki.utilities.Alias;
import elki.utilities.documentation.Reference;
import elki.utilities.optionhandling.Parameterizer;

/**
 * Average intercluster distance.
 * <p>
 * Reference:
 * <p>
 * Data Clustering for Very Large Datasets Plus Applications<br>
 * T. Zhang<br>
 * Doctoral Dissertation, 1997.
 *
 * @author Andreas Lang
 */
@Alias({ "D2" })
@Reference(authors = "T. Zhang", //
    title = "Data Clustering for Very Large Datasets Plus Applications", //
    booktitle = "University of Wisconsin Madison, Technical Report #1355", //
    url = "ftp://ftp.cs.wisc.edu/pub/techreports/1997/TR1355.pdf", //
    bibkey = "tr/wisc/Zhang97")
public class AverageInterclusterDistance implements BIRCHDistance {
  /**
   * Static instance.
   */
  public static final AverageInterclusterDistance STATIC = new AverageInterclusterDistance();

  @Override
  public double squaredDistance(NumberVector nv, ClusteringFeature cf) {
    final int dim = nv.getDimensionality();
    assert dim == cf.getDimensionality();
    double sum = 0;
    final double div = 1. / cf.n;
    for(int d = 0; d < dim; d++) {
      final double delta = cf.centroid(d) - nv.doubleValue(d);
      sum += div * cf.ssd[d] + (delta * delta);
    }
    return sum > 0 ? sum : 0;
  }

  @Override
  public double squaredDistance(ClusteringFeature cf1, ClusteringFeature cf2) {
    final int dim = cf1.getDimensionality();
    assert dim == cf2.getDimensionality();
    final double div1 = 1. / cf1.n, div2 = 1. / cf2.n;
    double sum = 0;
    for(int d = 0; d < dim; d++) {
      final double delta = cf1.centroid(d) - cf2.centroid(d);
      sum += div1 * cf1.ssd[d] + div2 * cf2.ssd[d] + (delta * delta);
    }
    return sum > 0 ? sum : 0;
  }

  /**
   * Parameterization class.
   *
   * @author Andreas Lang
   */
  public static class Par implements Parameterizer {
    @Override
    public AverageInterclusterDistance make() {
      return STATIC;
    }
  }
}

/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 * 
 * Copyright (C) 2019
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
package elki.clustering.hierarchical.betula.birch;

import java.util.Arrays;

import elki.clustering.hierarchical.betula.CFInterface;
import elki.data.NumberVector;
import elki.math.linearalgebra.VMath;

/**
 * Clustering Feature of BIRCH, only for comparison
 * 
 * @author Erich Schubert
 * @since 0.7.5
 */
public class ClusteringFeature implements CFInterface {
  /**
   * Number of objects
   */
  int n;

  /**
   * Linear sum.
   */
  double[] ls;

  /**
   * Sum of squares (see original thesis, this is a scalar).
   */
  double ss;

  /**
   * Constructor.
   *
   * @param dimensionality Dimensionality
   */
  public ClusteringFeature(int dimensionality) {
    this.n = 0;
    this.ls = new double[dimensionality];
  }

  @Override
  public void addToStatistics(NumberVector nv) {
    final int d = nv.getDimensionality();
    assert (d == ls.length);
    this.n++;
    for(int i = 0; i < d; i++) {
      double v = nv.doubleValue(i);
      ls[i] += v;
      ss += v * v;
    }
  }

  @Override
  public void addToStatistics(CFInterface other) {
    addToStatistics((ClusteringFeature) other);
  }

  // @Override
  public void addToStatistics(ClusteringFeature other) {
    n += other.n;
    VMath.plusEquals(ls, other.ls);
    ss += other.ss;
  }

  @Override
  public void resetStatistics() {
    n = 0;
    Arrays.fill(ls, 0.);
    ss = 0;
  }

  @Override
  public double centroid(int i) {
    return n > 0 ? ls[i] / n : 0.;
  }

  /**
   * Sum over all dimensions of sums of squares.
   *
   * @return Sum of SS
   */
  public double sumOfSumOfSquares() {
    return ss;
  }

  /**
   * Sum over all dimensions of squares of linear sums.
   *
   * @return Sum of LS
   */
  public double sumOfSquaresOfSums() {
    double sum = 0.;
    for(int i = 0; i < ls.length; i++) {
      double v = ls[i];
      sum += v * v;
    }
    return sum;
  }

  @Override
  public int getDimensionality() {
    return ls.length;
  }

  /**
   * Compute the sum of squares of a vector.
   *
   * @param v Vector
   * @return Sum of squares
   */
  public static double sumOfSquares(NumberVector v) {
    final int dim = v.getDimensionality();
    double sum = 0;
    for(int d = 0; d < dim; d++) {
      double x = v.doubleValue(d);
      sum += x * x;
    }
    return sum;
  }

  @Override
  public double variance(int i) {
    int dim = ls.length;
    double v = (1. / n) * ss;
    for(int d = 0; d < ls.length; d++) {
      v -= (ls[d] / n) * (ls[d] / n);
    }
    double var = 1. / dim * v;
    return var >= 0. ? var : 0.;
  }

  @Override
  public int getWeight() {
    return n;
  }

  @Override
  public double variance() {
    double v = (1. / n) * ss;
    for(int d = 0; d < ls.length; d++) {
      v -= (ls[d] / n) * (ls[d] / n);
    }
    return v;
  }

  @Override
  public double SoD() {
    double v = (1. / n) * ss;
    for(int d = 0; d < ls.length; d++) {
      v -= (ls[d] / n) * (ls[d] / n);
    }
    return v * n; // TODO improve performance
  }

  @Override
  public double[][] covariance() {
    throw new IllegalStateException("This CF Model doesn't support this method.");
  }

}

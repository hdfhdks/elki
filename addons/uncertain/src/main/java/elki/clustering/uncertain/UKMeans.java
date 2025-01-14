/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2022
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
package elki.clustering.uncertain;

import static elki.math.linearalgebra.VMath.sum;
import static elki.math.linearalgebra.VMath.timesEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import elki.clustering.ClusteringAlgorithm;
import elki.clustering.kmeans.KMeans;
import elki.data.Cluster;
import elki.data.Clustering;
import elki.data.DoubleVector;
import elki.data.NumberVector;
import elki.data.model.KMeansModel;
import elki.data.type.TypeInformation;
import elki.data.type.TypeUtil;
import elki.data.uncertain.DiscreteUncertainObject;
import elki.data.uncertain.UncertainObject;
import elki.database.datastore.DataStoreFactory;
import elki.database.datastore.DataStoreUtil;
import elki.database.datastore.WritableIntegerDataStore;
import elki.database.ids.DBIDIter;
import elki.database.ids.DBIDUtil;
import elki.database.ids.DBIDs;
import elki.database.ids.ModifiableDBIDs;
import elki.database.relation.Relation;
import elki.distance.minkowski.SquaredEuclideanDistance;
import elki.logging.Logging;
import elki.logging.progress.IndefiniteProgress;
import elki.logging.statistics.DoubleStatistic;
import elki.logging.statistics.LongStatistic;
import elki.result.Metadata;
import elki.utilities.datastructures.arraylike.ArrayLikeUtil;
import elki.utilities.documentation.Reference;
import elki.utilities.optionhandling.Parameterizer;
import elki.utilities.optionhandling.constraints.CommonConstraints;
import elki.utilities.optionhandling.parameterization.Parameterization;
import elki.utilities.optionhandling.parameters.IntParameter;
import elki.utilities.optionhandling.parameters.RandomParameter;
import elki.utilities.random.RandomFactory;

/**
 * Uncertain K-Means clustering, using the average deviation from the center.
 * <p>
 * Note: this method is, essentially, superficial. It was shown to be equivalent
 * to doing regular K-means on the object centroids instead (see {@link CKMeans}
 * for the reference and an implementation). This is only for completeness.
 * <p>
 * Reference:
 * <p>
 * M. Chau, R. Cheng, B. Kao, J. Ng<br>
 * Uncertain data mining: An example in clustering location data<br>
 * Proc. 10th Pacific-Asia Conf. on Knowledge Discovery and Data Mining (PAKDD)
 *
 * @author Klaus Arthur Schmidt
 * @since 0.7.0
 */
@Reference(authors = "M. Chau, R. Cheng, B. Kao, J. Ng", //
    title = "Uncertain data mining: An example in clustering location data", //
    booktitle = "Proc. 10th Pacific-Asia Conference on Knowledge Discovery and Data Mining (PAKDD 2006)", //
    url = "https://doi.org/10.1007/11731139_24", //
    bibkey = "DBLP:conf/pakdd/ChauCKN06")
public class UKMeans implements ClusteringAlgorithm<Clustering<KMeansModel>> {
  /**
   * Class logger.
   */
  private static final Logging LOG = Logging.getLogger(UKMeans.class);

  /**
   * Key for statistics logging.
   */
  private static final String KEY = UKMeans.class.getName();

  /**
   * Number of cluster centers to initialize.
   */
  protected int k;

  /**
   * Maximum number of iterations
   */
  protected int maxiter;

  /**
   * Our Random factory
   */
  protected RandomFactory rnd;

  /**
   * Constructor.
   *
   * @param k Number of clusters
   * @param maxiter Maximum number of iterations
   * @param rnd Random initialization
   */
  public UKMeans(int k, int maxiter, RandomFactory rnd) {
    this.k = k;
    this.maxiter = maxiter;
    this.rnd = rnd;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(UncertainObject.UNCERTAIN_OBJECT_FIELD);
  }

  /**
   * Run the clustering.
   *
   * @param relation the Relation
   * @return Clustering result
   */
  public Clustering<KMeansModel> run(Relation<DiscreteUncertainObject> relation) {
    // Choose initial means randomly
    DBIDs sampleids = DBIDUtil.randomSample(relation.getDBIDs(), k, rnd);
    List<double[]> means = new ArrayList<>(k);
    for(DBIDIter iter = sampleids.iter(); iter.valid(); iter.advance()) {
      means.add(ArrayLikeUtil.toPrimitiveDoubleArray(relation.get(iter).getCenterOfMass()));
    }

    // Setup cluster assignment store
    List<ModifiableDBIDs> clusters = new ArrayList<>();
    for(int i = 0; i < k; i++) {
      clusters.add(DBIDUtil.newHashSet((int) (relation.size() * 2. / k)));
    }
    WritableIntegerDataStore assignment = DataStoreUtil.makeIntegerStorage(relation.getDBIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, -1);
    double[] varsum = new double[k];

    IndefiniteProgress prog = LOG.isVerbose() ? new IndefiniteProgress("UK-Means iteration", LOG) : null;
    DoubleStatistic varstat = LOG.isStatistics() ? new DoubleStatistic(this.getClass().getName() + ".variance-sum") : null;
    int iteration = 0;
    for(; maxiter <= 0 || iteration < maxiter; iteration++) {
      LOG.incrementProcessed(prog);
      boolean changed = assignToNearestCluster(relation, means, clusters, assignment, varsum);
      logVarstat(varstat, varsum);
      // Stop if no cluster assignment changed.
      if(!changed) {
        break;
      }
      // Recompute means.
      means = means(clusters, means, relation);
    }
    LOG.setCompleted(prog);
    if(LOG.isStatistics()) {
      LOG.statistics(new LongStatistic(KEY + ".iterations", iteration));
    }

    // Wrap result
    Clustering<KMeansModel> result = new Clustering<>();
    Metadata.of(result).setLongName("Uk-Means Clustering");
    for(int i = 0; i < clusters.size(); i++) {
      DBIDs ids = clusters.get(i);
      if(ids.isEmpty()) {
        continue;
      }
      result.addToplevelCluster(new Cluster<>(ids, new KMeansModel(means.get(i), varsum[i])));
    }
    return result;
  }

  /**
   * Returns a list of clusters. The k<sup>th</sup> cluster contains the ids of
   * those FeatureVectors, that are nearest to the k<sup>th</sup> mean.
   *
   * @param relation the database to cluster
   * @param means a list of k means
   * @param clusters cluster assignment
   * @param assignment Current cluster assignment
   * @param varsum Variance sum output
   * @return true when the object was reassigned
   */
  protected boolean assignToNearestCluster(Relation<DiscreteUncertainObject> relation, List<double[]> means, List<? extends ModifiableDBIDs> clusters, WritableIntegerDataStore assignment, double[] varsum) {
    assert (k == means.size());
    boolean changed = false;
    Arrays.fill(varsum, 0.);
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      double mindist = Double.POSITIVE_INFINITY;
      DiscreteUncertainObject fv = relation.get(iditer);
      int minIndex = 0;
      for(int i = 0; i < k; i++) {
        double dist = getExpectedRepDistance(DoubleVector.wrap(means.get(i)), fv);
        if(dist < mindist) {
          minIndex = i;
          mindist = dist;
        }
      }
      varsum[minIndex] += mindist;
      changed |= updateAssignment(iditer, clusters, assignment, minIndex);
    }
    return changed;
  }

  /**
   * Update the cluster assignment.
   *
   * @param iditer Object id
   * @param clusters Cluster list
   * @param assignment Assignment storage
   * @param newA New assignment.
   * @return {@code true} if the assignment has changed.
   */
  protected boolean updateAssignment(DBIDIter iditer, List<? extends ModifiableDBIDs> clusters, WritableIntegerDataStore assignment, int newA) {
    final int oldA = assignment.intValue(iditer);
    if(oldA == newA) {
      return false;
    }
    clusters.get(newA).add(iditer);
    assignment.putInt(iditer, newA);
    if(oldA >= 0) {
      clusters.get(oldA).remove(iditer);
    }
    return true;
  }

  /**
   * Get expected distance between a Vector and an uncertain object
   *
   * @param rep A vector, e.g. a cluster representative
   * @param uo A discrete uncertain object
   * @return The distance
   */
  protected double getExpectedRepDistance(NumberVector rep, DiscreteUncertainObject uo) {
    SquaredEuclideanDistance euclidean = SquaredEuclideanDistance.STATIC;
    int counter = 0;
    double sum = 0.0;
    for(int i = 0; i < uo.getNumberSamples(); i++) {
      sum += euclidean.distance(rep, uo.getSample(i));
      counter++;
    }
    return sum / counter;
  }

  /**
   * Returns the mean vectors of the given clusters in the given database.
   *
   * @param clusters the clusters to compute the means
   * @param means the recent means
   * @param database the database containing the vectors
   * @return the mean vectors of the given clusters in the given database
   */
  protected List<double[]> means(List<? extends ModifiableDBIDs> clusters, List<double[]> means, Relation<DiscreteUncertainObject> database) {
    List<double[]> newMeans = new ArrayList<>(k);
    for(int i = 0; i < k; i++) {
      ModifiableDBIDs list = clusters.get(i);
      double[] mean = null;
      if(list.size() > 0) {
        DBIDIter iter = list.iter();
        // Initialize with first.
        mean = ArrayLikeUtil.toPrimitiveDoubleArray(database.get(iter).getCenterOfMass());
        iter.advance();
        // Update with remaining instances
        for(; iter.valid(); iter.advance()) {
          NumberVector vec = database.get(iter).getCenterOfMass();
          for(int j = 0; j < mean.length; j++) {
            mean[j] += vec.doubleValue(j);
          }
        }
        timesEquals(mean, 1.0 / list.size());
      }
      else {
        // Keep degenerated means as-is for now.
        mean = means.get(i);
      }
      newMeans.add(mean);
    }
    return newMeans;
  }

  /**
   * Log statistics on the variance sum.
   *
   * @param varstat Statistics log instance
   * @param varsum Variance sum per cluster
   */
  protected void logVarstat(DoubleStatistic varstat, double[] varsum) {
    if(varstat != null) {
      LOG.statistics(varstat.setDouble(sum(varsum)));
    }
  }

  /**
   * Parameterization class.
   *
   * @author Alexander Koos
   */
  public static class Par implements Parameterizer {
    /**
     * Number of cluster centers to initialize.
     */
    protected int k;

    /**
     * Maximum number of iterations
     */
    protected int maxiter;

    /**
     * Our Random factory
     */
    protected RandomFactory rnd;

    @Override
    public void configure(Parameterization config) {
      new IntParameter(KMeans.K_ID) //
          .addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT) //
          .grab(config, x -> k = x);
      new IntParameter(KMeans.MAXITER_ID, 0) //
          .addConstraint(CommonConstraints.GREATER_EQUAL_ZERO_INT) //
          .grab(config, x -> maxiter = x);
      new RandomParameter(KMeans.SEED_ID).grab(config, x -> rnd = x);
    }

    @Override
    public UKMeans make() {
      return new UKMeans(k, maxiter, rnd);
    }
  }
}

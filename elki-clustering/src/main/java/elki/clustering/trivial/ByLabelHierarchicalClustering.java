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
package elki.clustering.trivial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import elki.clustering.ClusteringAlgorithm;
import elki.data.Cluster;
import elki.data.Clustering;
import elki.data.model.ClusterModel;
import elki.data.model.Model;
import elki.data.type.NoSupportedDataTypeException;
import elki.data.type.TypeInformation;
import elki.data.type.TypeUtil;
import elki.database.Database;
import elki.database.ids.*;
import elki.database.relation.Relation;
import elki.logging.Logging;
import elki.result.Metadata;
import elki.utilities.Priority;
import elki.utilities.documentation.Description;
import elki.utilities.documentation.Title;

/**
 * Pseudo clustering using labels.
 * <p>
 * This "algorithm" puts elements into the same cluster when they agree in their
 * labels. I.e. it just uses a predefined clustering, and is mostly useful for
 * testing and evaluation (e.g. comparing the result of a real algorithm to a
 * reference result / golden standard).
 * <p>
 * This variant derives a hierarchical result by doing a prefix comparison on
 * labels.
 * <p>
 * TODO: Noise handling (e.g., allow the user to specify a noise label pattern?)
 * <p>
 * TODO: allow specifying a separator character
 * 
 * @author Erich Schubert
 * @since 0.2
 * 
 * @assoc - - - elki.data.ClassLabel
 */
@Title("Hierarchical clustering by label")
@Description("Cluster points by a (pre-assigned!) label. For comparing results with a reference clustering.")
@Priority(Priority.SUPPLEMENTARY - 5)
public class ByLabelHierarchicalClustering implements ClusteringAlgorithm<Clustering<Model>> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(ByLabelHierarchicalClustering.class);

  /**
   * Constructor without parameters
   */
  public ByLabelHierarchicalClustering() {
    super();
  }

  @Override
  public Clustering<Model> autorun(Database database) {
    // Prefer a true class label
    try {
      return run(database.getRelation(TypeUtil.CLASSLABEL));
    }
    catch(NoSupportedDataTypeException e) {
      // Otherwise, try any labellike.
      return run(database.getRelation(getInputTypeRestriction()[0]));
    }
  }

  /**
   * Run the actual clustering algorithm.
   * 
   * @param relation The data input to use
   */
  public Clustering<Model> run(Relation<?> relation) {
    HashMap<String, DBIDs> labelmap = new HashMap<>();
    ModifiableDBIDs noiseids = DBIDUtil.newArray();
    Clustering<Model> clustering = new ReferenceClustering<>();
    Metadata.of(clustering).setLongName("By Label Hierarchical Clustering");

    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      final Object val = relation.get(iditer);
      if(val == null) {
        noiseids.add(iditer);
        continue;
      }
      assign(labelmap, val.toString(), iditer);
    }

    ArrayList<Cluster<Model>> clusters = new ArrayList<>(labelmap.size());
    for(Entry<String, DBIDs> entry : labelmap.entrySet()) {
      DBIDs ids = entry.getValue();
      if(ids instanceof DBID) {
        noiseids.add((DBID) ids);
        continue;
      }
      clusters.add(new Cluster<Model>(entry.getKey(), ids, ClusterModel.CLUSTER));
    }

    for(Cluster<Model> cur : clusters) {
      Cluster<Model> bestparent = null;
      for(Cluster<Model> oth : clusters) {
        if(oth != cur && cur.getName().startsWith(oth.getName()) && //
            (bestparent == null || oth.getName().length() > bestparent.getName().length())) {
          bestparent = oth;
        }
      }
      if(bestparent != null) {
        if(LOG.isDebuggingFiner()) {
          LOG.debugFiner(cur.getName() + " is a child of " + bestparent.getName());
        }
        clustering.addChildCluster(bestparent, cur);
      }
      else {
        clustering.addToplevelCluster(cur);
      }
    }
    // Collected noise IDs.
    if(!noiseids.isEmpty()) {
      Cluster<Model> c = new Cluster<>("Noise", noiseids, ClusterModel.CLUSTER);
      c.setNoise(true);
      clustering.addToplevelCluster(c);
    }
    return clustering;
  }

  /**
   * Assigns the specified id to the labelMap according to its label
   * 
   * @param labelMap the mapping of label to ids
   * @param label the label of the object to be assigned
   * @param id the id of the object to be assigned
   */
  private void assign(HashMap<String, DBIDs> labelMap, String label, DBIDRef id) {
    if(labelMap.containsKey(label)) {
      DBIDs exist = labelMap.get(label);
      if(exist instanceof DBID) {
        ModifiableDBIDs n = DBIDUtil.newHashSet();
        n.add((DBID) exist);
        n.add(id);
        labelMap.put(label, n);
      }
      else {
        assert (exist instanceof HashSetModifiableDBIDs);
        assert (exist.size() > 1);
        ((ModifiableDBIDs) exist).add(id);
      }
    }
    else {
      labelMap.put(label, DBIDUtil.deref(id));
    }
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.GUESSED_LABEL);
  }
}

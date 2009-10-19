package experimentalcode.remigius.Visualizers;

import java.util.List;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.cluster.Cluster;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.logging.LoggingUtil;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClassManager.CSSNamingConflict;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import experimentalcode.lisa.scale.CutOffScale;
import experimentalcode.lisa.scale.GammaFunction;
import experimentalcode.lisa.scale.LinearScale;
import experimentalcode.shared.outlier.scaling.StaticScalingFunction;

/**
 * Generates a SVG-Element containing bubbles. A Bubble is a circle visualizing
 * an outlierness-score, with its center at the position of the visualized
 * object and its radius depending on the objects score.
 * 
 * @author Remigius Wojdanowski
 * 
 * @param <NV>
 */
public class BubbleVisualizer<NV extends NumberVector<NV, ?>> extends Projection2DVisualizer<NV> {

  /**
   * OptionID for {@link #GAMMA_PARAM}.
   */
  public static final OptionID GAMMA_ID = OptionID.getOrCreateOptionID("bubble.gamma", "A gamma-correction.");

  /**
   * Parameter for the gamma-correction.
   * 
   * <p>
   * Key: {@code -bubble.gamma}
   * </p>
   * 
   * <p>
   * Default value: 1.0
   * < /p>
   */
  private final DoubleParameter GAMMA_PARAM = new DoubleParameter(GAMMA_ID, 1.0);

  /**
   * Gamma parameter.
   */
  private Double gamma;

  /**
   * OptionID for {@link #FILL_FLAG}.
   */
  public static final OptionID FILL_ID = OptionID.getOrCreateOptionID("bubble.fill", "Half-transparent filling of bubbles.");

  /**
   * Flag for half-transparent filling of bubbles.
   * 
   * <p>
   * Key: {@code -bubble.fill}
   * </p>
   */
  private final Flag FILL_FLAG = new Flag(FILL_ID);

  /**
   * Fill parameter.
   */
  private Boolean fill;

  /**
   * OptionID for {@link #CUTOFF_PARAM}.
   */
  public static final OptionID CUTOFF_ID = OptionID.getOrCreateOptionID("bubble.cutoff", "Cut-off on normalized data.");

  /**
   * Parameter for the cutoff on normalized data. <br>
   * Negative values will have the same effect as 0 (no cut-off at all), while
   * values &gt; 1.0 will have the same effect as 1.0 (cut off everything).
   * 
   * <p>
   * Key: {@code -bubble.cutoff}
   * </p>
   * 
   * <p>
   * Default value: 0.0
   * < /p>
   */
  private final DoubleParameter CUTOFF_PARAM = new DoubleParameter(CUTOFF_ID, 0.0);

  /**
   * Cut-off parameter.
   */
  private Double cutOff;

  /**
   * Used for normalizing coordinates.
   */
  private StaticScalingFunction normalizationScale;

  /**
   * TODO: Find out & document what this scale was for. I can't remember, but it
   * seems essential.
   */
  private StaticScalingFunction plotScale;

  /**
   * Used for Gamma-Correction.
   * 
   * TODO: Make the gamma-function exchangeable (inc. Parameter etc.).
   */
  private GammaFunction gammaFunction;

  /**
   * Used for cut-off on normalized data.
   * 
   * @see #CUTOFF_ID
   * @see #CUTOFF_PARAM
   * @see #cutOff
   */
  private CutOffScale cutOffScale;

  /**
   * Contains the "outlierness-scores" to be displayed as ToolTips. If this
   * result does not contain <b>all</b> IDs the database contains, behavior is
   * undefined.
   */
  private AnnotationResult<Double> anResult;

  /**
   * The complete Result, as returned by an algorithm.
   * 
   * TODO: We don't need this anymore.
   */
  private Result result;
  
  /**
   * A clustering of the database.
   */
  private Clustering<Model> clustering;

  /**
   * Generic tag to indicate the type of element. Used in IDs, CSS-Classes etc.
   */
  public static final String BUBBLE = "bubble";

  /**
   * A short name characterizing this Visualizer.
   */
  public static final String NAME = "Bubbles";

  /**
   * The default constructor only registers parameters.
   */
  public BubbleVisualizer() {
    addOption(GAMMA_PARAM);
    addOption(FILL_FLAG);
    addOption(CUTOFF_PARAM);
  }

  /**
   * Initializes this Visualizer.
   * 
   * @param database contains all objects to be processed.
   * @param anResult contains "outlierness-scores", corresponding to the
   *        database.
   * @param result complete result for further information.
   * @param normalizationScale normalizes coordinates.
   * 
   * TODO: Refactor from AnnotationResult<Double> to AnnotationResult<Number>
   */
  public void init(String name, VisualizerContext context, AnnotationResult<Double> anResult, StaticScalingFunction normalizationScale, Clustering<Model> clustering) {
    super.init(1000, name, context);
    this.anResult = anResult;
    this.clustering = clustering;
    
    this.normalizationScale = normalizationScale;
    this.plotScale = new LinearScale(0.1);
    this.gammaFunction = new GammaFunction(gamma);
    this.cutOffScale = new CutOffScale(cutOff);
  }

  /**
   * Registers the Bubble-CSS-Class at a SVGPlot. This class depends on the
   * {@link #FILL_FLAG}.
   * 
   * @param svgp the SVGPlot to register the ToolTip-CSS-Class.
   */
  private void setupCSS(SVGPlot svgp) {
    
    // creating IDs manually because cluster often return a null-ID.
    int clusterID = 0;
    
    for (Cluster<Model> cluster : clustering.getAllClusters()){
      
      CSSClass bubble = new CSSClass(svgp, BUBBLE + clusterID);
      bubble.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, "0.005");
      
      String color;
      
      if (clustering.getAllClusters().size() == 1){
        color = "black";
      } else {
        color = context.getColorLibrary().getColor(clusterID);
      }
      
      if(fill) {
        bubble.setStatement(SVGConstants.CSS_FILL_PROPERTY, color);
        bubble.setStatement(SVGConstants.CSS_FILL_OPACITY_PROPERTY, 0.5);
      }
      else {
        // for diamond-shaped strokes, see bugs.sun.com, bug ID 6294396
        bubble.setStatement(SVGConstants.CSS_STROKE_VALUE, color);
        bubble.setStatement(SVGConstants.CSS_FILL_OPACITY_PROPERTY, 0.0);
      }
      
      // TODO: try/catch-structure is equal for almost all Visualizers, maybe
      // put that into a superclass.
      try {
        svgp.getCSSClassManager().addClass(bubble);
        svgp.updateStyleElement();
      }
      catch(CSSNamingConflict e) {
        LoggingUtil.exception("Equally-named CSSClass with different owner already exists", e);
      }
      clusterID += 1;
    }
  }

  @Override
  public List<String> setParameters(List<String> args) throws ParameterException {
    List<String> remainingParameters = super.setParameters(args);
    gamma = GAMMA_PARAM.getValue();
    fill = FILL_FLAG.getValue();
    cutOff = CUTOFF_PARAM.getValue();
    rememberParametersExcept(args, remainingParameters);
    return remainingParameters;
  }

  private Double getValue(int id) {
    return anResult.getValueFor(id);
  }

  /**
   * Convenience method to apply scalings in the right order.
   * 
   * @param d representing a outlierness-score to be scaled.
   * @return a Double representing a outlierness-score, after it has modified by
   *         the given scales.
   */
  private Double getScaled(Double d) {
    return plotScale.getScaled(gammaFunction.getScaled(cutOffScale.getScaled(normalizationScale.getScaled(d))));
  }

  @Override
  public Element visualize(SVGPlot svgp) {
    Element layer = super.visualize(svgp);
    setupCSS(svgp);
    int clusterID = 0;
    
    for (Cluster<Model> cluster : clustering.getAllClusters()){
      for(int id : cluster.getIDs()) {
        Element circle = SVGUtil.svgCircle(svgp.getDocument(), getProjected(id, 0), getProjected(id, 1), getScaled(getValue(id)));
        SVGUtil.addCSSClass(circle, BUBBLE + clusterID);
        layer.appendChild(circle);
      }
      clusterID += 1;
    }
    return layer;
  }
}

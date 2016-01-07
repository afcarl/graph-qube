/*
 * Copyright 2013-2016 MIT Lincoln Laboratory, Massachusetts Institute of Technology
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mitll.xdata;

import mitll.xdata.dataset.bitcoin.binding.BitcoinBinding;
import net.sf.javaml.core.kdtree.KDTree;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * @see mitll.xdata.dataset.bitcoin.binding.BitcoinBinding#getSimilarity(String, String)
 */
public class NodeSimilaritySearch {
  private static Logger logger = Logger.getLogger(NodeSimilaritySearch.class);

  private List<String> ids;
  private Map<String, Integer> idToRow;
  private float[][] features;
  private int numRows;
  private int numFeatures;
  private KDTree kdtree;

  /**
   * Just for testing
   * @param featureFile
   * @throws Exception
   */
/*  private NodeSimilaritySearch(String featureFile) throws Exception {
    load(featureFile);
  }*/

  public NodeSimilaritySearch(InputStream idStream, InputStream features) throws Exception {
    //InputStream userFeatures = this.getClass().getResourceAsStream(idStream);

    loadIds(idStream);
    idStream.close();

   // userFeatures = this.getClass().getResourceAsStream(features);

    load(features);
    features.close();
  }
    /**
     * @paramx idFile
     * @paramx featureFile
     * @throws Exception
     * @seex mitll.xdata.dataset.bitcoin.binding.BitcoinBinding#BitcoinBinding(mitll.xdata.db.DBConnection, boolean)
     * @see mitll.xdata.dataset.kiva.binding.KivaBinding#KivaBinding(mitll.xdata.db.DBConnection)
     */
  public NodeSimilaritySearch(String featureResource) throws Exception {
	  logger.debug("trying to use "+  featureResource);
    //InputStream userFeatures = this.getClass().getResourceAsStream(featureResource);
    InputStream userFeatures = this.getClass().getClassLoader().getResourceAsStream(featureResource);
    if (userFeatures == null) {
    	if (featureResource.startsWith(File.separator)) featureResource = featureResource.substring(1); 
    	//userFeatures = this.getClass().getResourceAsStream(featureResource);
    	userFeatures = this.getClass().getClassLoader().getResourceAsStream(featureResource);
    }
    if (userFeatures == null) {
  	  logger.error("can't find "+  featureResource);

    }
    loadIds(userFeatures);
    userFeatures.close();

    //userFeatures = this.getClass().getResourceAsStream(featureResource);
    userFeatures = this.getClass().getClassLoader().getResourceAsStream(featureResource);

    load(userFeatures);
  }

/*  private void load(String featureFile) throws Exception {
    loadFeatures(featureFile);
    index();
  }*/

  private void load(InputStream featureFile) throws Exception {
    loadFeatures(featureFile);
    index();
  }

/*  private void loadFeatures(String filename) throws Exception {
    InputStream in = new FileInputStream(filename);

    loadIds(in);
     in.close();


    in = new FileInputStream(filename);

    loadFeatures(in);
    in.close();

  }*/

  /**
   * Read tsv features from stream
   *
   * Feature file looks like:
   *
   * user	credit_mean	credit_std	credit_interarr_mean	credit_interarr_std	debit_mean	debit_std	debit_interarr_mean	debit_interarr_std	perp_in	perp_out
   * 1	-0.267196747509	4.71845605314	-1.98532168372	-1.81795608209	-0.62572854628	3.50342868639	-1.97913152956	-1.66367390298	3.98604860909	4.15894036513
   * 2	1.2892458977	-2.0	-2.0	-2.0	0.827426418659	0.452800091031	-2.0	-2.0	-2.0	0.252874980303
   *
   * TODO : try not to go back and forth between float and double
   * @param in
   * @throws IOException
   */
  private void loadFeatures(InputStream in) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
    String line = null;

    // determine number of features from tsv header
    line = br.readLine();
    List<String> fields = split(line, "\t");
    // skip first column (which has an id meant for browsing through data)
    numFeatures = fields.size() - 1;

    // initialize feature array
    features = new float[ids.size()][numFeatures];

    int row = 0;
    while ((line = br.readLine()) != null) {
      if (row >= ids.size()) {
        logger.warn("more feature rows than IDs? row = " + row + ", ids.size() = " + ids.size());
        break;
      }
      fields = split(line, "\t");
      String id = fields.get(0);
      // skip first field (which has an id meant for browsing through data)
      for (int i = 0; i < numFeatures; i++) {
        features[row][i] = (float) Double.parseDouble(fields.get(i + 1));
      }

      //idToRow.put(id, ids.size());
      //ids.add(id);

      row++;

      if (row % 200000 == 0) {
        logger.debug("loading features: " + (100.0 * row / ids.size()) + "% done");
      }
    }
    //numRows = ids.size();

    logger.debug("loading features: " + (100.0 * row / ids.size()) + "% done");

    br.close();
  }

  private void loadIds(InputStream in) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));

    // determine number of features from tsv header
    String line = br.readLine();

    idToRow = new HashMap<String, Integer>();
    ids=new ArrayList<String>();

    int row = 0;
    while ((line = br.readLine()) != null) {
      List<String> fields = split(line, "\t");
      String id = fields.get(0);

      idToRow.put(id, ids.size());
      ids.add(id);

      row++;

      if (row % 200000 == 0) {
        logger.debug("loading ids: " + (100.0 * row / ids.size()) + "% done");
      }
    }
    numRows = ids.size();

    logger.debug("loading ids: " + (100.0 * row / ids.size()) + "% done");

    br.close();
  }

  /**
   * Split string into fields.
   * <p/>
   * Note: Assumes at least one field.
   */
  private List<String> split(String s, String separator) {
    List<String> fields = new ArrayList<String>();
    int i = 0;
    // add fields up to last separator
    while (i < s.length()) {
      int index = s.indexOf(separator, i);
      if (index < 0) {
        break;
      }
      fields.add(s.substring(i, index));
      i = index + 1;
    }
    // add field after last separator
    fields.add(s.substring(i, s.length()));
    return fields;
  }

  /**
   * Build the KDTree
   * @see net.sf.javaml.core.kdtree.KDTree
   * @see #load(java.io.InputStream)
   */
  private void index() {
    kdtree = new KDTree(numFeatures);
    // note: this is just temporary to make sure there are no duplicate keys (feature vectors)
    Set<double[]> unique = new HashSet<double[]>();
    for (int i = 0; i < numRows; i++) {
      String id = ids.get(i);
      double[] key = floatToDouble(features[i]);
      if (unique.contains(key)) {
        logger.warn("key not unique for id = " + id);
        continue;
      }
      kdtree.insert(key, id);
      unique.add(key);
    }
  }

  private double[] floatToDouble(float[] f) {
    double[] d = new double[f.length];
    for (int i = 0; i < f.length; i++) {
      d[i] = f[i];
    }
    return d;
  }

  /**
   * @param id
   * @param k
   * @return
   * @see BitcoinBinding#getNearestNeighbors(String, int, boolean)
   */
  public List<String> neighbors(String id, int k) {
    List<String> results = new ArrayList<String>();
    if (!idToRow.containsKey(id)) {
      return results;
    }
    k = Math.min(k, ids.size());
    int row = idToRow.get(id);
    double[] key = floatToDouble(features[row]);
    Object[] objects = kdtree.nearest(key, k);
    for (Object object : objects) {
      results.add((String) object);
    }
    return results;
  }

  /**
   * @param id0
   * @param id1
   * @return
   * @see BitcoinBinding#getSimilarity(String, String)
   * @see mitll.xdata.dataset.kiva.binding.KivaBinding#getSimilarity(String, String)
   */
  public double similarity(String id0, String id1) {
    return 1.0 / (1.0 + distance(id0, id1));
  }

  /**
   * @param id0
   * @param id1
   * @return
   * @see #similarity(String, String)
   */
  private double distance(String id0, String id1) {
    if (!idToRow.containsKey(id0) || !idToRow.containsKey(id1)) {
      return Double.MAX_VALUE;
    }
    double d = 0.0;
    int row0 = idToRow.get(id0);
    int row1 = idToRow.get(id1);
    for (int i = 0; i < numFeatures; i++) {
      double diff = features[row0][i] - features[row1][i];
      d += diff * diff;
    }
    d = Math.sqrt(d);
    return d;
  }

  public static void main(String[] args) throws Exception {
    // String featureFile = "c:/temp/kiva/kiva_similarity/partner_features_standardized.tsv";
    // String id = "p137";

    String featureFile = "c:/temp/kiva/kiva_similarity/kiva_feats_tsv/lender_features_standardized.tsv";
    String id = "l0376099";

    System.out.println("building search index...");
    NodeSimilaritySearch search = new NodeSimilaritySearch(featureFile);
    System.out.println("searching...");
    List<String> results = search.neighbors(id, 20);
    System.out.println(results);
  }
}

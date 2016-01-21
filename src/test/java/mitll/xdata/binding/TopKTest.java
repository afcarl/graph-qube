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

package mitll.xdata.binding;

import influent.idl.FL_Entity;
import influent.idl.FL_EntityMatchResult;
import influent.idl.FL_PatternSearchResult;
import mitll.xdata.GraphQuBEServer;
import mitll.xdata.ServerProperties;
import mitll.xdata.SimplePatternSearch;
import mitll.xdata.dataset.bitcoin.binding.BitcoinBinding;
import mitll.xdata.dataset.bitcoin.features.BitcoinFeaturesBase;
import mitll.xdata.db.DBConnection;
import mitll.xdata.db.H2Connection;
import mitll.xdata.db.MysqlConnection;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import uiuc.topksubgraph.Graph;
import uiuc.topksubgraph.MultipleIndexConstructor;
import uiuc.topksubgraph.QueryExecutor;

import java.io.IOException;
import java.util.*;

/**
 * Created by go22670 on 1/8/16.
 */
@RunWith(JUnit4.class)
public class TopKTest {
  private static Logger logger = Logger.getLogger(TopKTest.class);

  @Test
  public void testSimpleSearchFast() {
    int n = 10;

    Map<Long, Integer> edgeToWeight = getGraph(n, 2);
    String outdir = "data/bitcoin/fast/";

    Graph graph = ingestFast(n, edgeToWeight, outdir);

    Map<Integer, Integer> idToType = getUniformTypes(graph);
    //String outdir = "data/bitcoin/fast/";

    QueryExecutor executor = new QueryExecutor(graph,
        "bitcoin_small",
        getResourcePath(outdir), idToType);

    doTests(graph, idToType, executor);
  }

  @Test
  public void testSimpleSearchFast2() {
    int n = 10;

    Map<Long, Integer> edgeToWeight = getGraph(n, 2);
    String outdir = "data/bitcoin/fast/";

    Graph graph = ingestFast2(n, edgeToWeight, outdir);

    Map<Integer, Integer> idToType = getOddEvenTypes(graph);
    //String outdir = "data/bitcoin/fast/";

    QueryExecutor executor = new QueryExecutor(graph,
        "bitcoin_small",
        getResourcePath(outdir), idToType);

    doTests(graph, idToType, executor);
  }

  @Test
  public void testSimpleSearch() {
    int n = 10;

    Map<Long, Integer> edgeToWeight = getGraph(n, 2);
    String outdir = "data/bitcoin/indices/";
    Graph graph = ingest(n, edgeToWeight, outdir);

    Map<Integer, Integer> idToType = getUniformTypes(graph);

    QueryExecutor executor = new QueryExecutor(graph,
        "bitcoin_small",
        getResourcePath(outdir), idToType);


    doTests(graph, idToType, executor);
  }

  private void doTests(Graph graph, Map<Integer, Integer> idToType, QueryExecutor executor) {
    List<String> exemplarIDs = Arrays.asList("1", "2", "3");
    List<String> exemplarIDs2 = Arrays.asList("2", "4", "7");
    List<String> exemplarIDs3 = Arrays.asList("1", "2");
    List<String> exemplarIDs4 = Arrays.asList("2", "7", "8");

    executor.testQuery(exemplarIDs, graph, idToType);
    executor.testQuery(exemplarIDs2, graph, idToType);
    executor.testQuery(exemplarIDs3, graph, idToType);
    executor.testQuery(exemplarIDs4, graph, idToType);
  }

  private Map<Integer, Integer> getUniformTypes(Graph graph) {
    Map<Integer, Integer> idToType = new HashMap<>();
    for (Integer rawID : graph.getRawIDs()) {
      idToType.put(rawID, 1);
    }
    return idToType;
  }


  @Test
  public void testSimpleSearchFastOddEven2() {
    int n = 10;

    Map<Long, Integer> edgeToWeight = getGraph(n, 5);

    String outdir = "data/bitcoin/fast/";

    Graph graph = ingestFast2(n, edgeToWeight, outdir);

    Map<Integer, Integer> idToType = getOddEvenTypes(graph);

    QueryExecutor executor = new QueryExecutor(graph,
        "bitcoin_small",
        getResourcePath(outdir), idToType);


    doOddEvenTests(graph, idToType, executor);
  }

  private String getResourcePath(String outdir) {
    return "/Users/go22670/graph-qube/" + outdir;
  }

  @Test
  public void testSimpleSearch2() {
    int n = 10;

    Map<Long, Integer> edgeToWeight = getGraph(n, 5);

    String outdir = "data/bitcoin/indices/";

    Graph graph = ingest2(n, edgeToWeight, outdir);

    Map<Integer, Integer> idToType = getOddEvenTypes(graph);

    QueryExecutor executor = new QueryExecutor(graph,
        "bitcoin_small",
        getResourcePath(outdir), idToType);


    doOddEvenTests(graph, idToType, executor);
  }

  private void doOddEvenTests(Graph graph, Map<Integer, Integer> idToType, QueryExecutor executor) {
    List<String> exemplarIDs = Arrays.asList("3", "5", "7");
    List<String> exemplarIDs2 = Arrays.asList("2", "4", "6");
    List<String> exemplarIDs3 = Arrays.asList("2", "4", "6", "8");
    List<String> exemplarIDs5 = Arrays.asList("1", "4", "5", "8");

    List<String> exemplarIDs4 = Arrays.asList("1", "7", "9");


    executor.testQuery(exemplarIDs, graph, idToType);
    executor.testQuery(exemplarIDs2, graph, idToType);
    executor.testQuery(exemplarIDs3, graph, idToType);
    executor.testQuery(exemplarIDs4, graph, idToType);
    executor.testQuery(exemplarIDs5, graph, idToType);
  }

  private Map<Integer, Integer> getOddEvenTypes(Graph graph) {
    Map<Integer, Integer> idToType = new HashMap<>();
    for (Integer rawID : graph.getRawIDs()) {
      idToType.put(rawID, rawID % 2 == 0 ? 1 : 2);
    }
    return idToType;
  }


  @Test
  public void testSearch() {
    logger.debug("ENTER testSearch()");
    ServerProperties props = new ServerProperties();

    String bitcoinDirectory = ".";
    String bitcoinFeatureDirectory = GraphQuBEServer.DEFAULT_BITCOIN_FEATURE_DIR;

    try {
      DBConnection dbConnection = props.useMysql() ? new MysqlConnection(props.mysqlBitcoinJDBC()) : new H2Connection(bitcoinDirectory, "bitcoin");
      final SimplePatternSearch patternSearch;
      patternSearch = new SimplePatternSearch();

      BitcoinBinding bitcoinBinding = new BitcoinBinding(dbConnection, bitcoinFeatureDirectory);
      patternSearch.setBitcoinBinding(bitcoinBinding);
      Shortlist shortlist = bitcoinBinding.getShortlist();
      int max = 20;

      long then = System.currentTimeMillis();
      List<FL_PatternSearchResult> shortlist1 = shortlist.getShortlist(null, Arrays.asList("555261", "400046", "689982", "251593"), max);
      long now = System.currentTimeMillis();
      logger.info("time to do a search " + (now - then) + " millis ");

      if (shortlist1.size() > max) {
        shortlist1 = shortlist1.subList(0, max);
      }

      for (FL_PatternSearchResult result : shortlist1) {
        // logger.info("got " + result);
        Collection<String> matches = new ArrayList<>();
        for (FL_EntityMatchResult entity : result.getEntities()) {
          FL_Entity entity1 = entity.getEntity();
          matches.add(entity1.getUid());
          //logger.info("got " + entity1.getUid()+ " " + entity.getScore());
        }
        logger.info("got match " + matches);
      }

    } catch (Exception e) {
      logger.error("got " + e, e);

    }

//    Assert.assertEquals(sequence.getStates(), makeStates(1, 1, 1, 2, 2, 2));

    logger.debug("EXIT testSearch()");
  }

  @Test
  public void testGraph1() {
    logger.debug("ENTER testGraph1()");
    ServerProperties props = new ServerProperties();
    int n = 100000;
    int neighbors = 10;
    BitcoinFeaturesBase.rlogMemory();

    try {
      int max = 64;//128;
      for (int i = 32; i < max; i *= 2) {
        long time1 = System.currentTimeMillis();
        BitcoinFeaturesBase.rlogMemory();
        logger.info(n + " and " + i + " -------------------- ");

        Map<Long, Integer> edgeToWeight = getGraph(n, i);

        Graph graph = new Graph(edgeToWeight);

        Runtime.getRuntime().gc();

        BitcoinFeaturesBase.rlogMemory();

        long time2 = new Date().getTime();
        logger.info("Time:" + (time2 - time1));
      }
    } catch (Exception e) {
      logger.error("got " + e, e);
    }

    logger.debug("EXIT testSearch()");
    sleep();
  }

  private void sleep() {
    try {
      Thread.sleep(1000000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testIngest() {
    logger.debug("ENTER testIngest()");
    int n = 5;
    //int neighbors = 100;

    Map<Long, Integer> edgeToWeight = getGraph(n, 1);
    String outdir = "data/bitcoin/indices/";

    ingest(n, edgeToWeight, outdir);

    //sleep();

    logger.debug("EXIT testIngest()");
  }

  @Test
  public void testIngest2() {
    logger.debug("ENTER testIngest()");
    int n = 10;
    //int neighbors = 100;
    String outdir = "data/bitcoin/indices/";

    Map<Long, Integer> edgeToWeight = getGraph(n, 2);
    ingest2(n, edgeToWeight, outdir);

    String outdirFast = "data/bitcoin/fast/";

    ingestFast2(n, edgeToWeight, outdirFast);

    //sleep();

    logger.debug("EXIT testIngest()");
  }

  @Test
  public void testIngestFast() {
    logger.debug("ENTER testIngestFast()");
    int n = 5;
    //int neighbors = 100;

    Map<Long, Integer> edgeToWeight = getGraph(n, 1);
    String outdir = "data/bitcoin/fast/";
    ingestFast(n, edgeToWeight, outdir);

    //sleep();

    logger.debug("EXIT testIngestFast()");
  }

  @Test
  public void testIngestTwo() {
    logger.debug("ENTER testIngestTwo()");
    int n = 5;
    //int neighbors = 100;

    Map<Long, Integer> edgeToWeight = getGraph(n, 2);
    String outdir = "data/bitcoin/indices/";

    ingest(n, edgeToWeight, outdir);
    ingestFast(n, edgeToWeight, outdir.replaceAll("indices", "fast"));

    //sleep();

    logger.debug("EXIT testIngestTwo()");
  }


  @Test
  public void testIngestTwoTypes() {
    logger.debug("ENTER testIngestTwo()");
    int n = 5;
    //int neighbors = 100;

    Map<Long, Integer> edgeToWeight = getGraph(n, 2);
    String outdir = "data/bitcoin/indices/";
    ingest(n, edgeToWeight, outdir);

    String outFast = outdir.replaceAll("indices", "fast");
    ingestFast(n, edgeToWeight, outFast);

    //sleep();

    logger.debug("EXIT testIngestTwo()");
  }

  @Test
  public void testIngestLarger() {
    logger.debug("ENTER testIngestTwo()");
    int n = 400000;
    //int neighbors = 100;

    Map<Long, Integer> edgeToWeight = getGraph(n, 8);

    long then = System.currentTimeMillis();

    String outdir = "data/bitcoin/indices/";

    ingest(n, edgeToWeight, outdir);

    long then2 = System.currentTimeMillis();

    logger.info("old " + (then2 - then));

    String outFast = outdir.replaceAll("indices", "fast");

    ingestFast(n, edgeToWeight, outFast);
    long now = System.currentTimeMillis();
    logger.info("new " + (now - then2));

    //sleep();

    logger.debug("EXIT testIngestTwo()");
  }


  @Test
  public void testGraph() {
    logger.debug("ENTER testSearch()");
    int n = 400000;
    //int neighbors = 100;

    String outdir = "data/bitcoin/indices/";
    for (int i = 10; i < 50; i += 10) {
      Map<Long, Integer> edgeToWeight = getGraph(n, i);
      ingest(n, edgeToWeight, outdir);
    }

    sleep();

    logger.debug("EXIT testSearch()");
  }

  private Graph ingest2(int n, Map<Long, Integer> edgeToWeight, String outdir) {
    try {
      long time1 = System.currentTimeMillis();

      Graph graph = beforeComputeIndices2(n, edgeToWeight, outdir);

      computeIndices(time1, graph);

      return graph;
    } catch (Exception e) {
      logger.error("got " + e, e);
    }
    return null;
  }

  private Graph ingest(int n, Map<Long, Integer> edgeToWeight, String outdir) {
    try {
      long time1 = System.currentTimeMillis();

      Graph graph = beforeComputeIndices(n, edgeToWeight, outdir);

      computeIndices(time1, graph);

      return graph;
    } catch (Exception e) {
      logger.error("got " + e, e);
    }
    return null;
  }

  private void computeIndices(long time1, Graph graph) throws IOException {
    long then = System.currentTimeMillis();
    BitcoinFeaturesBase.rlogMemory();
    MultipleIndexConstructor.computeIndices(graph);
    BitcoinFeaturesBase.rlogMemory();

    long time2 = new Date().getTime();
    logger.info("Time:" + (time2 - time1));
    logger.info("Time to do computeIndices :" + (time2 - then));
  }

  private Graph ingestFast(int n, Map<Long, Integer> edgeToWeight, String outdir) {
    try {
      long time1 = System.currentTimeMillis();

      Graph graph = beforeComputeIndices(n, edgeToWeight, outdir);
      computeIndicesFast(time1, graph);

      return graph;
    } catch (Exception e) {
      logger.error("got " + e, e);
    }
    return null;
  }

  private Graph ingestFast2(int n, Map<Long, Integer> edgeToWeight, String outdir) {
    try {
      long time1 = System.currentTimeMillis();

      Graph graph = beforeComputeIndices2(n, edgeToWeight, outdir);
      computeIndicesFast(time1, graph);

      return graph;
    } catch (Exception e) {
      logger.error("got " + e, e);
    }
    return null;
  }

  private void computeIndicesFast(long time1, Graph graph) throws IOException {
    long then = System.currentTimeMillis();
    BitcoinFeaturesBase.rlogMemory();
    MultipleIndexConstructor.outDir = MultipleIndexConstructor.outDir.replaceAll("indices", "fast");
    MultipleIndexConstructor.computeIndicesFast(graph);
    BitcoinFeaturesBase.rlogMemory();

    long time2 = new Date().getTime();
    logger.info("Time:" + (time2 - time1));
    logger.info("Time to do computeIndices :" + (time2 - then));
  }

  private Graph beforeComputeIndices2(int n, Map<Long, Integer> edgeToWeight, String outDir) throws IOException {
    Collection<Integer> integers = MultipleIndexConstructor.loadTypes2(n);
    return getGraphBeforeComputeIndices(edgeToWeight, integers, outDir);
  }

  private Graph beforeComputeIndices(int n, Map<Long, Integer> edgeToWeight, String outDir) throws IOException {
    Collection<Integer> integers = MultipleIndexConstructor.loadTypes(n);
    return getGraphBeforeComputeIndices(edgeToWeight, integers, outDir);
  }

  private Graph getGraphBeforeComputeIndices(Map<Long, Integer> edgeToWeight, Collection<Integer> types,
                                             String outDir) throws IOException {
    MultipleIndexConstructor.createTypedEdges();

    BitcoinFeaturesBase.logMemory();

    Graph graph = new Graph(edgeToWeight);

    BitcoinFeaturesBase.logMemory();

    // Create Typed Edges

    // Load and Sort Edges from Graph
    MultipleIndexConstructor.populateSortedEdgeLists(graph);
    BitcoinFeaturesBase.logMemory();

    //save the sorted edge lists
    //String outDir = MultipleIndexConstructor.outDir;
    MultipleIndexConstructor.saveSortedEdgeList(outDir);
    BitcoinFeaturesBase.logMemory();

    //test method that computes totalTypes
    MultipleIndexConstructor.computeTotalTypes();
    BitcoinFeaturesBase.logMemory();
    // logger.debug("Computed number of types: " + totalTypes);

    /**
     * Functionality of SPDAndTopologyAndSPathIndexConstructor
     */
    //hash map for all possible "edge-type" paths: i.e. doubles,triples,...D-tuples
    //this gets you the "official" ordering
    logger.info("Computing Edge-Type Path Ordering...");
    MultipleIndexConstructor.computeEdgeTypePathOrdering();


    logger.info("Computing SPD, Topology and SPath Indices...");

//    Set<Integer> types = new HashSet<>(2);
//    types.add(1);
    MultipleIndexConstructor.makeTypeIDs(types);
    return graph;
  }

  private Map<Long, Integer> getGraph(int n, int neighbors) {
    Map<Long, Integer> edgeToWeight = new HashMap<>();

    Random random = new Random(123456789l);

    for (int from = 0; from < n; from++) {
      Set<Long> current = new HashSet<>();

      for (int j = 0; j < neighbors; j++) {
        long to = random.nextInt(n);
        while (to == from || current.contains(to)) {
          to = random.nextInt(n);
        }

        long l = BitcoinFeaturesBase.storeTwo(from, to);
        current.add(to);
    /*    int low = BitcoinFeaturesBase.getLow(l);
        int high = BitcoinFeaturesBase.getHigh(l);
        if (low != from) logger.error("huh?");
        if (high != to) logger.error("huh?");
    */
        int w = 1 + random.nextInt(9);
        //    logger.info(from + "->" + to + " : " + w);
        edgeToWeight.put(l, w);
      }
    }

    logger.info("made " + edgeToWeight.size() + " with " + edgeToWeight.keySet().size() + " and " + edgeToWeight.values().size());
    return edgeToWeight;
  }
}


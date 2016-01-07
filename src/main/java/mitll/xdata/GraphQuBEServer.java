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

import influent.idl.FL_EntityMatchDescriptor;
import influent.idl.FL_Future;
import influent.idl.FL_PatternDescriptor;
import influent.idl.FL_PatternSearchResults;
import mitll.xdata.binding.Binding;
import mitll.xdata.dataset.bitcoin.binding.BitcoinBinding;
import mitll.xdata.dataset.kiva.binding.KivaBinding;
import mitll.xdata.db.DBConnection;
import mitll.xdata.db.H2Connection;
import mitll.xdata.db.MysqlConnection;
import mitll.xdata.viz.SVGGraph;
import org.apache.avro.AvroRemoteException;
import org.apache.log4j.Logger;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import static spark.Spark.*;

public class GraphQuBEServer {
  private static Logger logger = Logger.getLogger(GraphQuBEServer.class);

  private static final int DEFAULT_MAX = 5;
  //private static final boolean USE_KIVA = false;
  private static final String DEFAULT_BITCOIN_FEATURE_DIR = "bitcoin_small_feats_tsv";
  private static final boolean USE_IN_MEMORY_ADJACENCY_DEFAULT = true;
  private static final int PORT = 8085;

  //private static final String MYSQL_H2_DATABASE = "";
  //private static final String MYSQL_BITCOIN_DATABASE = "";
 // private static final boolean USE_MYSQL = false;

 // private ServerProperties props = new ServerProperties();

  /**
   * arg 0 - port to run the server on
   * arg 1 - kiva directory - "" is OK
   * arg 2 - bitcoin h2 directory (so you can place it anywhere)
   * arg 3 - bitcoin feature directory (e.g. bitcoin_small_feats_tsv)
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    final SimplePatternSearch patternSearch;
    int port = PORT;
    if (args.length >= 1) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        System.err.println("Usage : port kivaDirectory bitcoinH2Directory bitcoinFeatureDirectory");
        return;
      }
    }

    String kivaDirectory = ".";
    String bitcoinDirectory = ".";
    String bitcoinFeatureDirectory = DEFAULT_BITCOIN_FEATURE_DIR;

    if (args.length >= 2) {
      kivaDirectory = args[1];
    }

    if (args.length >= 3) {
      bitcoinDirectory = args[2];
    }
    if (args.length >= 4) {
      bitcoinFeatureDirectory = args[3];
    }

    boolean useFastBitcoinConnectedTest = USE_IN_MEMORY_ADJACENCY_DEFAULT;
    if (args.length >= 4) {
      useFastBitcoinConnectedTest = "true".equalsIgnoreCase(args[3]);
    }

    logger.debug("using port = " + port);
    logger.debug("using kivaDirectory = " + kivaDirectory);
    logger.debug("using bitcoinDirectory = " + bitcoinDirectory);
    
    spark.Spark.setPort(port);
    // patternSearch = SimplePatternSearch.getDemoPatternSearch(kivaDirectory, bitcoinDirectory,
    //        useFastBitcoinConnectedTest);
    patternSearch = new SimplePatternSearch();

    ServerProperties props = new ServerProperties();

    if (props.useKiva()) {
      DBConnection dbConnection = props.useMysql() ? new MysqlConnection(props.mysqlKivaJDBC()) : new H2Connection(kivaDirectory,"kiva");
      patternSearch.setKivaBinding(new KivaBinding(dbConnection));
    }
    DBConnection dbConnection = props.useMysql() ? new MysqlConnection(props.mysqlBitcoinJDBC()) : new H2Connection(bitcoinDirectory,"bitcoin");
    patternSearch.setBitcoinBinding(new BitcoinBinding(dbConnection, bitcoinFeatureDirectory));

    // RPC calls from PatternSearch_v1.4.avdl

    staticFileLocation("/sankey"); // Static files

    Route route = getRoute(patternSearch);
    get(route);
    post(route);

    Route entitySearchRoute = getEntitySearchRoute(patternSearch);
    get(entitySearchRoute);
    post(entitySearchRoute);

    get(new Route("/searchByTemplate") {
      @Override
      public Object handle(Request request, Response response) {
        return "searchByTemplate";
      }
    });

    get(getSankeyRoute(patternSearch));
  }

  private static Route getSankeyRoute(final SimplePatternSearch patternSearch) {
    return new Route("/sankey") {
      @Override
      public Object handle(Request request, Response response) {
        Binding testBinding = patternSearch.getBinding(null);
        List<String> ids = Arrays.asList("b", "a", "c", "d");

        String idsParameter = request.queryParams("ids");
        if (idsParameter != null && idsParameter.length() > 0) ids = Arrays.asList(idsParameter.trim().split(","));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat sdfShort = new SimpleDateFormat("yyyy-MM-dd");

        long startTime = 0;
        long endTime = 0;
        try {
          startTime = sdf.parse("2013-10-01 00:00").getTime();
          endTime = sdf.parse("2013-11-01 00:00").getTime();
        } catch (ParseException e) {
          e.printStackTrace();
        }

        String start = request.queryParams("start");
        try {
          if (start != null && start.length() > 0) {
            start = start.replaceAll("\"", "");
            if (start.length() == "2013-10-01 00:00".length()) {
              startTime = sdf.parse(start).getTime();
              logger.debug("start now " + start + " " + startTime);
            } else if (start.length() == "2013-10-01".length()) {
              startTime = sdfShort.parse(start).getTime();
              logger.debug("start now " + start + " " + startTime);
            }
          }
        } catch (ParseException e) {
          response.status(400);
          return "Bad parameter: start = [" + start + "]";
        }

        String end = request.queryParams("end");
        try {

          if (end != null && end.length() > 0) {
            end = end.replaceAll("\"", "");
            if (end.length() == "2013-10-01 00:00".length()) {
              endTime = sdf.parse(end).getTime();
              logger.debug("start now " + end + " " + endTime);
            } else if (end.length() == "2013-10-01".length()) {
              endTime = sdfShort.parse(end).getTime();
              logger.debug("start now " + endTime + " " + endTime);
            }
          }
        } catch (ParseException e) {
          response.status(400);
          return "Bad parameter: end = [" + end + "]";
        }

        String maxParameter = request.queryParams("max");

        long max = DEFAULT_MAX;
        if (maxParameter != null && maxParameter.trim().length() > 0) {
          try {
            max = Long.parseLong(maxParameter, 10);
          } catch (NumberFormatException e) {
            response.status(400);
            return "Bad parameter: max = [" + maxParameter + "]";
          }
        }

        try {
          String json = testBinding.searchByExampleJson(ids, 0, max, startTime, endTime);
          response.type("application/json");

          return json;
        } catch (Exception e) {
          logger.error("got " + e, e);
          response.status(400);
          return "Request ids " + ids + " are not connected.";
        }
      }
    };
  }

  public static Route getRoute(final SimplePatternSearch patternSearch) {
    return new Route("/pattern/search/example") {
      @Override
      public Object handle(Request request, Response response) {
        logger.debug("/pattern/search/example");

        String exampleParameter = request.queryParams("example");
        String service = request.queryParams("service");
        String startParameter = request.queryParams("start");
        String maxParameter = request.queryParams("max");
        String svg = request.queryParams("svg");
        String hmm = request.queryParams("hmm");
        String startTimeParameter = request.queryParams("startTime");
        String endTimeParameter = request.queryParams("endTime");

        FL_PatternDescriptor example;

        if (exampleParameter != null && exampleParameter.trim().length() > 0) {
          try {
            example = (FL_PatternDescriptor) AvroUtils.decodeJSON(
                FL_PatternDescriptor.getClassSchema(), exampleParameter);
          } catch (Exception e) {
            logger.error("got " +e,e);
            response.status(400);
            return getBadParamResponse(exampleParameter, e);
          }
        } else {
          response.status(400);
          return "Bad parameter: example = [" + exampleParameter + "]";
        }

        long start = 0;
        if (startParameter != null && startParameter.trim().length() > 0) {
          try {
            start = Long.parseLong(startParameter, 10);
          } catch (NumberFormatException e) {
            response.status(400);
            return "Bad parameter: start = [" + startParameter + "]";
          }
        }

        long max = 10;
        if (maxParameter != null && maxParameter.trim().length() > 0) {
          try {
            max = Long.parseLong(maxParameter, 10);
          } catch (NumberFormatException e) {
            response.status(400);
            return "Bad parameter: max = [" + maxParameter + "]";
          }
        }


        long startTime = Long.MIN_VALUE;
        if (startTimeParameter != null && startTimeParameter.trim().length() > 0) {
          try {
            startTime = Long.parseLong(startTimeParameter, 10);
          } catch (NumberFormatException e) {
            response.status(400);
            return "Bad parameter: startTime = [" + startTimeParameter + "]";
          }
        }

        long endTime = Long.MAX_VALUE;
        if (endTimeParameter != null && endTimeParameter.trim().length() > 0) {
          try {
            endTime = Long.parseLong(endTimeParameter, 10);
          } catch (NumberFormatException e) {
            response.status(400);
            return "Bad parameter: endTime = [" + endTimeParameter + "]";
          }
        }

        try {
          Object result = patternSearch.searchByExample(example, service, start, max,
              hmm == null || (hmm != null && !hmm.equalsIgnoreCase("false")), startTime, endTime);
          String json = null;
          if (result instanceof FL_PatternSearchResults) {
            try {
              FL_PatternSearchResults results = (FL_PatternSearchResults) result;
              if (svg != null) {
                Binding binding = patternSearch.getBinding(example);
                //List<Binding.ResultInfo> entities = binding.getEntities(example);
                List<FL_EntityMatchDescriptor> entities = example.getEntities();
                response.type("text/html");
                
                logger.info("some unnecessary logging...");
                logger.info("results: "+results);
                logger.info("binding: "+binding);
                logger.info("example: "+example);
                logger.info("entities: "+entities);
                return new SVGGraph().toSVG(entities, results, binding);
              } else {
                json = AvroUtils.encodeJSON((FL_PatternSearchResults) result);
              }
            } catch (Exception e) {
              logger.error("got " +e,e);
            }
          } else if (result instanceof FL_Future) {
            try {
              json = AvroUtils.encodeJSON((FL_Future) result);
            } catch (Exception e) {
              logger.error("got " +e,e);
            }
          }
          // Note: can install JSONView firefox add-on to handle this content type
          response.type("application/json");
          // response.type("text/html");
          return json;
        } catch (AvroRemoteException e) {
          logger.error("got " +e,e);
        }

        return "searchByExample";
      }
    };
  }

  public static String getBadParamResponse(String exampleParameter, Exception e) {
    String message = "";
    message += "Bad parameter: example = [" + exampleParameter + "]";
    message += "<br/><br/>";
    message += e.getMessage();
    message += "<br/><br/>";
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    message += sw;
    return message;
  }

  public static Route getEntitySearchRoute(final SimplePatternSearch patternSearch) {
    return new Route("/entity/search/example") {
      @Override
      public Object handle(Request request, Response response) {
        logger.debug("/entity/search/example");
        String id = request.queryParams("id");
        String maxParameter = request.queryParams("max");

        if (id == null) {
          response.status(400);
          return "Bad parameter: id = [" + id + "]";
        }

        long max = 10;
        if (maxParameter != null && maxParameter.trim().length() > 0) {
          try {
            max = Long.parseLong(maxParameter, 10);
          } catch (NumberFormatException e) {
            response.status(400);
            return "Bad parameter: max = [" + maxParameter + "]";
          }
        }

        try {
          FL_PatternDescriptor example = AvroUtils.createExemplarQuery(Arrays.asList(id));
          Object result = patternSearch.searchByExample(example, "", 0, max, null,false);
          if (result instanceof FL_PatternSearchResults) {
            String table = AvroUtils.entityListAsTable((FL_PatternSearchResults) result);
            String html = "";
            html += "<!DOCTYPE html>";
            html += "\n"
                + "<style type='text/css'>table, th, td {border: 1px solid black;}; th, td {padding: 4px;}; tr:nth-child(even) {background: #CCCCCC;};</style>";
            html += "\n" + table;
            response.type("text/html");
            return html;
          }
        } catch (AvroRemoteException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

        return "/entity/search/example";
      }
    };
  }
}

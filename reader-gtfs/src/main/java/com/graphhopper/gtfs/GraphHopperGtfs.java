/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.gtfs;

import com.conveyal.gtfs.model.DBConnection;
import com.conveyal.gtfs.model.Transfer;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.InMemConstructionIndex;
import com.graphhopper.storage.index.IndexStructureInfo;
import com.graphhopper.storage.index.LineIntIndex;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphHopperGtfs extends GraphHopper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphHopperGtfs.class);

    private final GraphHopperConfig ghConfig;
    private GtfsStorage gtfsStorage;
    private PtGraph ptGraph;

    public GraphHopperGtfs(GraphHopperConfig ghConfig) {
        this.ghConfig = ghConfig;
    }

    @Override
    protected void importOSM() {
        if (ghConfig.has("datareader.file")) {
            super.importOSM();
        } else {
            createBaseGraphAndProperties();
        }
    }

    @Override
    protected void importPublicTransit() {
        ptGraph = new PtGraph(getBaseGraph().getDirectory(), 100);
        gtfsStorage = new GtfsStorage(getBaseGraph().getDirectory());
        LineIntIndex stopIndex = new LineIntIndex(new BBox(-180.0, 180.0, -90.0, 90.0), getBaseGraph().getDirectory(), "stop_index");
        if (getGtfsStorage().loadExisting()) {
            ptGraph.loadExisting();
            stopIndex.loadExisting();
        } else {
            ensureWriteAccess();
            getGtfsStorage().create();
            ptGraph.create(100);
            InMemConstructionIndex indexBuilder = new InMemConstructionIndex(IndexStructureInfo.create(
                    new BBox(-180.0, 180.0, -90.0, 90.0), 300));
            try {
                if (ghConfig.getBool("load.from.db.validated",false)) {

                    String[] company_array = get_validated_companies();
                    for (String company_id : company_array) {
                        getGtfsStorage().loadGtfsFromDB("gtfs_" + company_id, company_id);

                    }
                }

                else if (ghConfig.getBool("load.from.db",false)) {
                    if (ghConfig.has("company.id")) {
                        String companies = ghConfig.getString("company.id", "");
                        String[] company_array = companies.split(",", -1);
                        for (String company_id : company_array) {
                            getGtfsStorage().loadGtfsFromDB("gtfs_" + company_id, company_id);
                        }
                    }
                    else {
                        throw new RuntimeException("No company id, please provide one");
                    }
                }
                else {
                    int idx = 0;
                    List<String> gtfsFiles = ghConfig.has("gtfs.file") ? Arrays.asList(ghConfig.getString("gtfs.file", "").split(",")) : Collections.emptyList();
                    for (String gtfsFile : gtfsFiles) {
                            getGtfsStorage().loadGtfsFromZipFileOrDirectory("gtfs_" + idx++, new File(gtfsFile));
                        }
                }
                getGtfsStorage().postInit();
                Map<String, Transfers> allTransfers = new HashMap<>();
                HashMap<String, GtfsReader> allReaders = new HashMap<>();
                getGtfsStorage().getGtfsFeeds().forEach((id, gtfsFeed) -> {
                    Transfers transfers = new Transfers(gtfsFeed);
                    allTransfers.put(id, transfers);
                    GtfsReader gtfsReader = new GtfsReader(id, ptGraph, ptGraph, getGtfsStorage(), getLocationIndex(), transfers, indexBuilder);
                    // Stops must be connected to the networks of all the modes
                    List<DefaultSnapFilter> snapFilters = getProfiles().stream().map(p ->
                            new DefaultSnapFilter(createWeighting(p, new PMap()), getEncodingManager().getBooleanEncodedValue(Subnetwork.key(p.getName())))).collect(Collectors.toList());
                    gtfsReader.connectStopsToStreetNetwork(e -> {
                        for (DefaultSnapFilter snapFilter : snapFilters) {
                            if (!snapFilter.accept(e))
                                return false;
                        }
                        return true;
                    });
                    LOGGER.info("Building transit graph for feed {}", gtfsFeed.feedId);
                    gtfsReader.buildPtNetwork();
                    allReaders.put(id, gtfsReader);
                });
                interpolateTransfers(allReaders, allTransfers);
            } catch (Exception e) {
                throw new RuntimeException("Error while constructing transit network. Is your GTFS file valid? Please check log for possible causes.", e);
            }
            ptGraph.flush();
            getGtfsStorage().flush();
            stopIndex.store(indexBuilder);
            stopIndex.flush();
        }
        gtfsStorage.setStopIndex(stopIndex);
        gtfsStorage.setPtGraph(ptGraph);
    }

    private String[] get_validated_companies() throws SQLException {
        DBConnection db = new DBConnection("user_data");
        String sql = "SELECT DISTINCT info.company_id " +
                "FROM working_data.info AS info " +
                "JOIN user_data.api_tokens AS t ON info.company_id = t.company_id " +
                "WHERE t.api_name = 'get_directions' " +
                "AND t.revoked = 0 " +
                "AND t.published = 1 " +
                "AND info.share_gtfs = 1 " +
                "AND info.validated = 1 " +
                "AND info.published = 1 " +
                "AND NOT EXISTS (" +
                "    SELECT 1 " +
                "    FROM working_data.routes r " +
                "    WHERE r.company_id = info.company_id AND r.route_type = 1100" +
                ")";
        ResultSet rs = db.ExecuteQuery(sql);
        List<String> candidate_ids = new ArrayList<>();
        while (rs.next()) {
            candidate_ids.add(rs.getString("company_id"));
        }
        rs.close();

        // Freshness guard.
        // A company can be flagged validated=1/published=1 in working_data.info while the GTFS
        // snapshot actually served from operational_data has long since expired: operational_data
        // is only ever (re)written by an explicit publish, and is keyed by company_id alone - there
        // is no feed version or effective-date concept, so the info flags say nothing about whether
        // the loaded calendars still cover today. Without this guard an expired feed is loaded
        // silently and the planner answers "no public transport" for current dates while /info still
        // reports a fresh import_date (which is only the graph *build* date). Below we log every
        // feed's calendar window and drop feeds with no service on or after today.
        boolean skipExpired = ghConfig.getBool("gtfs.skip_expired_feeds", true);

        List<String> kept_ids = new ArrayList<>();
        for (String company_id : candidate_ids) {
            boolean current;
            try {
                current = logAndCheckFeedFreshness(db, company_id);
            } catch (Exception e) {
                // Fail open: a probe error (bad id, missing grant, transient DB issue) must never
                // be able to blank the transit graph, so keep the feed and surface the problem.
                LOGGER.warn("Could not evaluate GTFS freshness for company {} ({}). Keeping it.", company_id, e.toString());
                kept_ids.add(company_id);
                continue;
            }
            if (current || !skipExpired) {
                kept_ids.add(company_id);
            } else {
                LOGGER.error("EXPIRED GTFS: company {} has no operational_data service on or after today - " +
                        "excluding it from the transit graph. Re-import/validate/publish a current feed.", company_id);
            }
        }

        // Safety net: never let the guard itself produce an empty feed set. That would both hide the
        // problem and crash GtfsStorage.postInit (it calls max()/min() over the loaded feeds). If every
        // candidate looks expired there is nothing fresh to fall back to, so keep them all and shout -
        // serving stale data beats taking the whole journey planner down.
        if (kept_ids.isEmpty() && !candidate_ids.isEmpty()) {
            LOGGER.error("ALL {} candidate GTFS feeds appear expired/empty in operational_data. Keeping them " +
                    "to avoid an empty transit graph - TRANSIT DATA IS STALE and must be re-published.", candidate_ids.size());
            closeQuietly(db);
            return candidate_ids.toArray(new String[0]);
        }

        LOGGER.info("GTFS freshness guard: kept {} of {} validated companies for the transit graph: {}",
                kept_ids.size(), candidate_ids.size(), kept_ids);
        closeQuietly(db);
        return kept_ids.toArray(new String[0]);
    }

    /**
     * Logs the operational_data calendar window for a company and returns whether the feed still has
     * service on or after today. A feed is considered "current" if it has either a calendar row whose
     * end_date is today or later, or a future calendar_dates "added service" (exception_type = 1) row
     * (covering feeds that rely on calendar_dates only). The day-of-week "active today" figure is a
     * best-effort signal that ignores calendar_dates exceptions; it only enriches the log line and does
     * not influence the keep/drop decision.
     */
    private boolean logAndCheckFeedFreshness(DBConnection db, String company_id) throws SQLException {
        // company_id originates from an int column in working_data.info; validate before inlining it.
        long cid = Long.parseLong(company_id.trim());

        String probe =
                "SELECT " +
                "  (SELECT COUNT(*)        FROM operational_data.calendar       WHERE company_id = " + cid + ") AS n_cal, " +
                "  (SELECT MIN(start_date) FROM operational_data.calendar       WHERE company_id = " + cid + ") AS min_start, " +
                "  (SELECT MAX(end_date)   FROM operational_data.calendar       WHERE company_id = " + cid + ") AS max_end, " +
                "  (SELECT COUNT(*)        FROM operational_data.calendar       WHERE company_id = " + cid + " AND end_date >= CURDATE()) AS n_cal_current, " +
                "  (SELECT COUNT(*)        FROM operational_data.calendar_dates WHERE company_id = " + cid + " AND exception_type = '1' AND date >= CURDATE()) AS n_cd_future, " +
                "  (SELECT COUNT(*)        FROM operational_data.calendar       WHERE company_id = " + cid +
                "        AND start_date <= CURDATE() AND end_date >= CURDATE() " +
                "        AND CASE DAYOFWEEK(CURDATE()) " +
                "              WHEN 1 THEN sunday WHEN 2 THEN monday WHEN 3 THEN tuesday WHEN 4 THEN wednesday " +
                "              WHEN 5 THEN thursday WHEN 6 THEN friday WHEN 7 THEN saturday END = '1') AS n_active_today";

        ResultSet rs = db.ExecuteQuery(probe);
        boolean current = false;
        if (rs.next()) {
            long nCal         = rs.getLong("n_cal");
            String minStart   = rs.getString("min_start");
            String maxEnd     = rs.getString("max_end");
            long nCalCurrent  = rs.getLong("n_cal_current");
            long nCdFuture    = rs.getLong("n_cd_future");
            long nActiveToday = rs.getLong("n_active_today");

            current = (nCalCurrent > 0) || (nCdFuture > 0);

            if (nCal == 0 && nCdFuture == 0) {
                LOGGER.warn("GTFS feed for company {}: operational_data.calendar is EMPTY (no services). Treated as expired.", company_id);
            } else {
                LOGGER.info("GTFS feed for company {}: calendar window {}..{}, {} services ({} valid today-or-later), " +
                        "{} future calendar_dates adds, {} services active today (approx). current={}",
                        company_id, minStart, maxEnd, nCal, nCalCurrent, nCdFuture, nActiveToday, current);
                if (current && nActiveToday == 0) {
                    LOGGER.warn("GTFS feed for company {} is not expired but has NO service active today - " +
                            "possible sparse/holiday calendar or a service window that only starts in the future.", company_id);
                }
            }
        }
        rs.close();
        return current;
    }

    private static void closeQuietly(DBConnection db) {
        try {
            if (db != null && db.getConn() != null) db.getConn().close();
        } catch (SQLException ignored) {
        }
    }

    private void interpolateTransfers(HashMap<String, GtfsReader> readers, Map<String, Transfers> allTransfers) {
        LOGGER.info("Looking for transfers");
        final int maxTransferWalkTimeSeconds = ghConfig.getInt("gtfs.max_transfer_interpolation_walk_time_seconds", 120);
        QueryGraph queryGraph = QueryGraph.create(getBaseGraph(), Collections.emptyList());
        Weighting transferWeighting = createWeighting(getProfile("foot"), new PMap());
        final GraphExplorer graphExplorer = new GraphExplorer(queryGraph, ptGraph, transferWeighting, getGtfsStorage(), RealtimeFeed.empty(), true, true, false, 5.0, false, 0);
        getGtfsStorage().getStationNodes().values().stream().distinct().map(n -> new Label.NodeId(gtfsStorage.getPtToStreet().getOrDefault(n, -1), n)).forEach(stationNode -> {
            MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(graphExplorer, true, false, false, 0, new ArrayList<>());
            router.setLimitStreetTime(Duration.ofSeconds(maxTransferWalkTimeSeconds).toMillis());
            for (Label label : router.calcLabels(stationNode, Instant.ofEpochMilli(0))) {
                if (label.parent != null) {
                    if (label.edge.getType() == GtfsStorage.EdgeType.EXIT_PT) {
                        GtfsStorage.PlatformDescriptor fromPlatformDescriptor = label.edge.getPlatformDescriptor();
                        Transfers transfers = allTransfers.get(fromPlatformDescriptor.feed_id);
                        for (PtGraph.PtEdge ptEdge : ptGraph.edgesAround(stationNode.ptNode)) {
                            if (ptEdge.getType() == GtfsStorage.EdgeType.ENTER_PT) {
                                GtfsStorage.PlatformDescriptor toPlatformDescriptor = ptEdge.getAttrs().platformDescriptor;
                                LOGGER.debug(fromPlatformDescriptor + " -> " + toPlatformDescriptor);
                                if (!toPlatformDescriptor.feed_id.equals(fromPlatformDescriptor.feed_id)) {
                                    LOGGER.debug(" Different feed. Inserting transfer with " + (int) (label.streetTime / 1000L) + " s.");
                                    insertInterpolatedTransfer(label, toPlatformDescriptor, readers);
                                } else {
                                    List<Transfer> transfersToStop = transfers.getTransfersToStop(toPlatformDescriptor.stop_id, routeIdOrNull(toPlatformDescriptor));
                                    if (transfersToStop.stream().noneMatch(t -> t.from_stop_id.equals(fromPlatformDescriptor.stop_id))) {
                                        LOGGER.debug("  Inserting transfer with " + (int) (label.streetTime / 1000L) + " s.");
                                        insertInterpolatedTransfer(label, toPlatformDescriptor, readers);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void insertInterpolatedTransfer(Label label, GtfsStorage.PlatformDescriptor toPlatformDescriptor, HashMap<String, GtfsReader> readers) {
        GtfsReader toFeedReader = readers.get(toPlatformDescriptor.feed_id);
        List<Integer> transferEdgeIds = toFeedReader.insertTransferEdges(label.node.ptNode, (int) (label.streetTime / 1000L), toPlatformDescriptor);
        List<Label.Transition> transitions = Label.getTransitions(label.parent, true);
        int[] skippedEdgesForTransfer = transitions.stream().filter(t -> t.edge != null).mapToInt(t -> {
            Label.NodeId adjNode = t.label.node;
            EdgeIteratorState edgeIteratorState = getBaseGraph().getEdgeIteratorState(t.edge.getId(), adjNode.streetNode);
            return edgeIteratorState.getEdgeKey();
        }).toArray();
        if (skippedEdgesForTransfer.length > 0) { // TODO: Elsewhere, we distinguish empty path ("at" a node) from no path
            assert isValidPath(skippedEdgesForTransfer);
            for (Integer transferEdgeId : transferEdgeIds) {
                gtfsStorage.getSkippedEdgesForTransfer().put(transferEdgeId, skippedEdgesForTransfer);
            }
        }
    }

    private boolean isValidPath(int[] edgeKeys) {
        List<EdgeIteratorState> edges = Arrays.stream(edgeKeys).mapToObj(i -> getBaseGraph().getEdgeIteratorStateForKey(i)).collect(Collectors.toList());
        for (int i = 1; i < edges.size(); i++) {
            if (edges.get(i).getBaseNode() != edges.get(i - 1).getAdjNode())
                return false;
        }
        TripFromLabel tripFromLabel = new TripFromLabel(getBaseGraph(), getEncodingManager(), gtfsStorage, RealtimeFeed.empty(), getPathDetailsBuilderFactory(), 6.0);
        tripFromLabel.transferPath(edgeKeys, createWeighting(getProfile("foot"), new PMap()), 0L);
        return true;
    }

    private String routeIdOrNull(GtfsStorage.PlatformDescriptor platformDescriptor) {
        if (platformDescriptor instanceof GtfsStorage.RouteTypePlatform) {
            return null;
        } else {
            return ((GtfsStorage.RoutePlatform) platformDescriptor).route_id;
        }
    }

    @Override
    public void close() {
        getGtfsStorage().close();
        super.close();
    }

    public GtfsStorage getGtfsStorage() {
        return gtfsStorage;
    }

    public PtGraph getPtGraph() {
        return ptGraph;
    }
}

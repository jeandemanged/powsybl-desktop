/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.search;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A fuzzy-searchable, in-memory Lucene index over a {@link Network}'s equipment (id, name, {@link NetworkSearch.Kind}).
 * Indexing is a flat pass per {@link NetworkSearch.Kind} over the network's own stream accessors, so unlike a
 * hierarchy walk it never needs to dedupe multi-terminal equipment (lines, transformers, tie lines) by terminal/leg.
 * <p>
 * Everything except {@link NetworkSearch.Kind#BUS} is indexed once and never changes: no other equipment is ever
 * added/removed once a network is loaded - {@link NetworkSearch.Kind#CONFIGURED_BUS} buses included, since a
 * BUS_BREAKER voltage level's configured buses are real, directly-modeled objects unaffected by switch state,
 * unlike {@link NetworkSearch.Kind#BUS}'s bus-view merged buses ({@link VoltageLevel#getBusView()}), which are
 * recalculated on every topology change (switch open/close, terminal connect/disconnect);
 * {@link #refreshBuses(VoltageLevel)}/{@link #refreshBuses(Network)} re-index just that one kind in place, see
 * their caller in {@code MainController} for the trigger. {@link #search} and {@code refreshBuses} may be called
 * concurrently from different threads.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkSearchIndex implements Closeable {

    /** Lifecycle of a per-network index, tracked by whoever owns the (async) build. */
    public enum State { NOT_BUILT, BUILDING, READY, FAILED }

    private static final String FIELD_ID = "id";
    private static final String FIELD_ID_TEXT = "idText";
    private static final String FIELD_ID_EXACT = "idExact";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_KIND = "kind";

    // Comfortably above any FuzzyQuery score (similarity-based, effectively capped around 1 before IDF/length
    // weighting) so a token that's an outright substring of a field always outranks a same-field typo match.
    // PREFIX_BOOST outranks SUBSTRING_BOOST in turn, since a "starts with" match is a stronger signal than a
    // match buried in the middle of a field - DisjunctionMaxQuery takes the max, so a prefix match (which is
    // also a substring match) scores at PREFIX_BOOST, not the sum of the two.
    private static final float SUBSTRING_BOOST = 10f;
    private static final float PREFIX_BOOST = 20f;

    // Resolves a match back to its live object ourselves, rather than via Network.getIdentifiable(id): calculated
    // bus-view buses in particular aren't registered there and would silently fail to resolve. A concurrent map
    // since refreshBuses() mutates it while search() (on another thread) may be reading it.
    private final Map<String, Identifiable<?>> byId;
    // ids of the Kind.BUS entries currently in byId, grouped by voltage level - tracked separately since
    // Kind.CONFIGURED_BUS entries are also plain Bus instances, so a refresh can't tell the two apart by type
    // alone when it needs to drop only the former's now-stale ids; grouping lets a voltage-level-scoped
    // refreshBuses() drop and re-add just one voltage level's ids instead of every bus in the network. Plain
    // (non-concurrent) since only refreshBuses()/build() ever touch it, both effectively single-threaded
    // (refreshBuses() is synchronized, build() runs before the index is exposed to any other thread).
    private final Map<String, Set<String>> busIdsByVoltageLevel;
    private final Analyzer analyzer;
    private final Directory directory;
    // Kept open for the life of the index (not just during build()) so refreshBuses() can write to it later.
    private final IndexWriter writer;
    // Reassigned by refreshBuses() after a commit, so a concurrent search() always sees a consistent, fully-open
    // reader/searcher pair - never a separate reader field that could be swapped out of step with it.
    private volatile IndexSearcher searcher;

    private NetworkSearchIndex(Map<String, Identifiable<?>> byId, Map<String, Set<String>> busIdsByVoltageLevel,
                                Analyzer analyzer, Directory directory, IndexWriter writer, DirectoryReader reader) {
        this.byId = byId;
        this.busIdsByVoltageLevel = busIdsByVoltageLevel;
        this.analyzer = analyzer;
        this.directory = directory;
        this.writer = writer;
        this.searcher = new IndexSearcher(reader);
    }

    /**
     * Builds the index for the given network. Blocking (proportional to network size) — callers must run this off
     * the FX thread.
     */
    public static NetworkSearchIndex build(Network network) {
        Analyzer analyzer = new StandardAnalyzer();
        Directory directory = new ByteBuffersDirectory();
        Map<String, Identifiable<?>> byId = new ConcurrentHashMap<>();
        Map<String, Set<String>> busIdsByVoltageLevel = new HashMap<>();
        IndexWriter writer;
        try {
            writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
            for (NetworkSearch.Kind kind : NetworkSearch.ALL_KINDS) {
                for (Identifiable<?> identifiable : streamOf(network, kind).toList()) {
                    writer.addDocument(toDocument(identifiable, kind));
                    byId.put(identifiable.getId(), identifiable);
                    if (kind == NetworkSearch.Kind.BUS) {
                        Bus bus = (Bus) identifiable;
                        busIdsByVoltageLevel.computeIfAbsent(bus.getVoltageLevel().getId(), v -> new HashSet<>()).add(bus.getId());
                    }
                }
            }
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return new NetworkSearchIndex(byId, busIdsByVoltageLevel, analyzer, directory, writer, DirectoryReader.open(writer));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Re-indexes only {@link NetworkSearch.Kind#BUS} against the network's current bus-view, in place - call after
     * a topology change that isn't confined to one voltage level. Blocking, but proportional only to the bus
     * count, not the whole network, so unlike {@link #build} this is cheap enough to call synchronously; prefer
     * {@link #refreshBuses(VoltageLevel)} when the change is confined to one, cheaper still. Synchronized so two
     * rapid, overlapping topology changes can't both delete-then-recreate the same documents.
     */
    public synchronized void refreshBuses(Network network) {
        List<Bus> buses = streamOf(network, NetworkSearch.Kind.BUS).map(Bus.class::cast).toList();
        try {
            writer.deleteDocuments(new Term(FIELD_KIND, NetworkSearch.Kind.BUS.name()));
            for (Bus bus : buses) {
                writer.addDocument(toDocument(bus, NetworkSearch.Kind.BUS));
            }
            commitAndRefreshSearcher();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        busIdsByVoltageLevel.values().forEach(ids -> ids.forEach(byId::remove));
        busIdsByVoltageLevel.clear();
        buses.forEach(bus -> {
            byId.put(bus.getId(), bus);
            busIdsByVoltageLevel.computeIfAbsent(bus.getVoltageLevel().getId(), v -> new HashSet<>()).add(bus.getId());
        });
    }

    /**
     * Re-indexes only {@code voltageLevel}'s own {@link NetworkSearch.Kind#BUS} buses, in place - call after a
     * topology change confined to one voltage level (switch open/close, terminal connect/disconnect). Proportional
     * only to that voltage level's bus count, so cheaper than {@link #refreshBuses(Network)} on a large network.
     * Synchronized so two rapid, overlapping topology changes can't both delete-then-recreate the same documents.
     */
    public synchronized void refreshBuses(VoltageLevel voltageLevel) {
        List<Bus> buses = voltageLevel.getBusView().getBusStream().toList();
        String voltageLevelId = voltageLevel.getId();
        Set<String> staleIds = busIdsByVoltageLevel.getOrDefault(voltageLevelId, Set.of());
        try {
            if (!staleIds.isEmpty()) {
                writer.deleteDocuments(staleIds.stream().map(id -> new Term(FIELD_ID_EXACT, id)).toArray(Term[]::new));
            }
            for (Bus bus : buses) {
                writer.addDocument(toDocument(bus, NetworkSearch.Kind.BUS));
            }
            commitAndRefreshSearcher();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        staleIds.forEach(byId::remove);
        Set<String> newIds = new HashSet<>();
        buses.forEach(bus -> {
            byId.put(bus.getId(), bus);
            newIds.add(bus.getId());
        });
        busIdsByVoltageLevel.put(voltageLevelId, newIds);
    }

    private void commitAndRefreshSearcher() throws IOException {
        writer.commit();
        IndexSearcher oldSearcher = searcher;
        searcher = new IndexSearcher(DirectoryReader.open(writer));
        oldSearcher.getIndexReader().close();
    }

    private static Stream<Identifiable<?>> streamOf(Network network, NetworkSearch.Kind kind) {
        return switch (kind) {
            case SUBSTATION -> network.getSubstationStream().map(i -> i);
            case VOLTAGE_LEVEL -> network.getVoltageLevelStream().map(i -> i);
            case BUS -> network.getBusView().getBusStream().map(i -> i);
            // configured (bus-breaker-view) buses only exist as a set distinct from the bus view for
            // BUS_BREAKER-topology voltage levels - a node-breaker VL's bus-breaker view is itself just
            // another calculated view, not worth indexing separately from Kind.BUS
            case CONFIGURED_BUS -> network.getVoltageLevelStream()
                    .filter(vl -> vl.getTopologyKind() == TopologyKind.BUS_BREAKER)
                    .flatMap(vl -> vl.getBusBreakerView().getBusStream()).map(i -> i);
            case GENERATOR -> network.getGeneratorStream().map(i -> i);
            case SHUNT_COMPENSATOR -> network.getShuntCompensatorStream().map(i -> i);
            case STATIC_VAR_COMPENSATOR -> network.getStaticVarCompensatorStream().map(i -> i);
            case LOAD -> network.getLoadStream().map(i -> i);
            case LINE -> network.getLineStream().map(i -> i);
            case TRANSFORMER -> Stream.<Identifiable<?>>concat(network.getTwoWindingsTransformerStream(), network.getThreeWindingsTransformerStream());
            case TIE_LINE -> network.getTieLineStream().map(i -> i);
            case BOUNDARY_LINE -> network.getBoundaryLineStream().map(i -> i);
            case BUSBAR_SECTION -> network.getBusbarSectionStream().map(i -> i);
        };
    }

    private static Document toDocument(Identifiable<?> identifiable, NetworkSearch.Kind kind) {
        Document document = new Document();
        document.add(new StoredField(FIELD_ID, identifiable.getId()));
        document.add(new TextField(FIELD_ID_TEXT, identifiable.getId(), Field.Store.NO));
        identifiable.getOptionalName().ifPresent(name -> document.add(new TextField(FIELD_NAME, name, Field.Store.NO)));
        document.add(new StringField(FIELD_KIND, kind.name(), Field.Store.NO));
        if (kind == NetworkSearch.Kind.BUS) {
            // an exact (unanalyzed) copy of the id, indexed so refreshBuses(VoltageLevel) can delete precisely
            // the stale ids it already knows about, unlike FIELD_ID_TEXT's analyzed tokens or FIELD_ID's
            // stored-only (unindexed) value, neither of which supports an exact-match term delete
            document.add(new StringField(FIELD_ID_EXACT, identifiable.getId(), Field.Store.NO));
        }
        return document;
    }

    /**
     * @param matches     the matches, ranked best first
     * @param approximate true if {@code matches} came from the fuzzy/typo-tolerant fallback (see {@link #search})
     */
    public record Result(List<Identifiable<?>> matches, boolean approximate) {
    }

    /**
     * Searches this index, restricted to {@code kinds}, ranked best match first: a query token that a field starts
     * with outranks one it merely contains (see {@link #PREFIX_BOOST}/{@link #SUBSTRING_BOOST}). Fuzzy/typo-tolerant
     * matching is only attempted as a fallback, once this precise pass finds nothing at all — mixing the two would
     * bury exact/prefix/substring hits under a flood of merely-similar ones — and the result is flagged
     * {@link Result#approximate()} so callers can tell the user these are approximate. A substation/voltage-level
     * hit is only kept if it also has equipment of a requested kind, unless every kind is requested (same rule as
     * {@link NetworkSearch}).
     */
    public Result search(String query, Set<NetworkSearch.Kind> kinds) {
        if (query == null || query.isBlank()) {
            return new Result(List.of(), false);
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return new Result(List.of(), false);
        }
        // read the volatile field once so a concurrent refreshBuses() swap mid-call can't mix an old searcher
        // with a doc count from the new one (or vice versa)
        IndexSearcher searcher = this.searcher;
        List<Identifiable<?>> preciseMatches = runQuery(searcher, buildQuery(tokens, kinds, false), kinds);
        if (!preciseMatches.isEmpty()) {
            return new Result(preciseMatches, false);
        }
        List<Identifiable<?>> fuzzyMatches = runQuery(searcher, buildQuery(tokens, kinds, true), kinds);
        return new Result(fuzzyMatches, !fuzzyMatches.isEmpty());
    }

    private static Query buildQuery(List<String> tokens, Set<NetworkSearch.Kind> kinds, boolean fuzzy) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String token : tokens) {
            builder.add(fuzzy ? perTokenFuzzyQuery(token) : perTokenPreciseQuery(token), Occur.MUST);
        }
        builder.add(new TermInSetQuery(FIELD_KIND, kinds.stream().map(kind -> new BytesRef(kind.name())).toList()), Occur.FILTER);
        return builder.build();
    }

    private static Query perTokenPreciseQuery(String token) {
        return new DisjunctionMaxQuery(List.of(
                new BoostQuery(new PrefixQuery(new Term(FIELD_ID_TEXT, token)), PREFIX_BOOST),
                new BoostQuery(new PrefixQuery(new Term(FIELD_NAME, token)), PREFIX_BOOST),
                new BoostQuery(new WildcardQuery(new Term(FIELD_ID_TEXT, "*" + token + "*")), SUBSTRING_BOOST),
                new BoostQuery(new WildcardQuery(new Term(FIELD_NAME, "*" + token + "*")), SUBSTRING_BOOST)), 0f);
    }

    private static Query perTokenFuzzyQuery(String token) {
        int maxEdits = maxEditsFor(token);
        return new DisjunctionMaxQuery(List.of(
                new FuzzyQuery(new Term(FIELD_ID_TEXT, token), maxEdits),
                new FuzzyQuery(new Term(FIELD_NAME, token), maxEdits)), 0f);
    }

    private List<Identifiable<?>> runQuery(IndexSearcher searcher, Query query, Set<NetworkSearch.Kind> kinds) {
        try {
            TopDocs topDocs = searcher.search(query, Math.max(1, searcher.getIndexReader().numDocs()));
            StoredFields storedFields = searcher.storedFields();
            List<Identifiable<?>> result = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                String id = storedFields.document(scoreDoc.doc).get(FIELD_ID);
                Identifiable<?> identifiable = byId.get(id);
                if (identifiable != null && isUsefulMatch(identifiable, kinds)) {
                    result.add(identifiable);
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Same tiers as Elasticsearch's "AUTO" fuzziness: an unscaled maxEdits (Lucene's default is 2 regardless of
    // term length) lets a couple of edits turn a short token like "g" or "l" into nearly any other short token,
    // matching far too broadly.
    private static int maxEditsFor(String token) {
        int length = token.codePointCount(0, token.length());
        if (length <= 2) {
            return 0;
        }
        if (length <= 5) {
            return 1;
        }
        return 2;
    }

    private static boolean isUsefulMatch(Identifiable<?> identifiable, Set<NetworkSearch.Kind> kinds) {
        if (identifiable instanceof Substation substation) {
            return NetworkSearch.hasMatchingEquipment(substation, kinds);
        }
        if (identifiable instanceof VoltageLevel voltageLevel) {
            return NetworkSearch.hasMatchingEquipment(voltageLevel, kinds);
        }
        return true;
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        try (TokenStream tokenStream = analyzer.tokenStream(FIELD_ID_TEXT, text)) {
            CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                tokens.add(termAttribute.toString());
            }
            tokenStream.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return tokens;
    }

    @Override
    public void close() {
        try {
            searcher.getIndexReader().close();
            writer.close();
            directory.close();
            analyzer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

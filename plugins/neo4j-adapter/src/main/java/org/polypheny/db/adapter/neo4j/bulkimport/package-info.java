/*
 * Copyright 2019-2025 The Polypheny Project
 *
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

/**
 * Pre-filling a Neo4j store from an LDBC SNB Interactive dataset.
 *
 * <h2>Why not just write the data</h2>
 *
 * Writing a dataset through Cypher means one statement per node and per edge. Measured on the BI
 * variant at SF 1, the adapter wrote about 136 thousand nodes in four minutes and then stopped
 * making progress on Comment, never reaching the edges. Neo4j ships an offline loader for exactly
 * this case: the Interactive variant at the same scale factor goes through it in about a minute.
 *
 * <h2>What happens on a load</h2>
 *
 * {@link org.polypheny.db.adapter.neo4j.bulkimport.LdbcCsvTransformer} rewrites the dataset into
 * the shape {@code neo4j-admin import} expects, one output file per entity, in parallel.
 * {@link org.polypheny.db.adapter.neo4j.bulkimport.BulkImporter} then stops the serving container
 * and starts a second one on the same volume to run the import. Bringing the store back up is the
 * store's own job, since the port it comes up on is assigned dynamically and everything hanging
 * off the driver has to be rebuilt.
 *
 * <h2>Things that are not obvious</h2>
 *
 * <b>The store has to go down.</b> {@code neo4j-admin import} refuses to touch a database that is
 * in use, so there is no way to import while Neo4j is serving. The container is stopped for the
 * duration and restarted afterwards, which means a load is not something to run casually.
 *
 * <b>Polypheny does not read Neo4j's identifiers.</b> A node carries its id in a {@code _id}
 * property and an edge carries {@code _id}, {@code __sourceId__} and {@code __targetId__},
 * because that is what the Cypher path writes and what {@code NeoUtil} reads back. The
 * {@code :ID} and {@code :START_ID} columns only tell neo4j-admin how to link things up; they are
 * not stored. Every id is therefore written twice, and leaving that out produces a store that
 * looks right in Neo4j and fails on the first query through Polypheny.
 *
 * <b>The CSVs have to be world-readable.</b> The Neo4j entrypoint verifies that it can read every
 * mounted folder as the {@code neo4j} user, even while still running as root, and unlike
 * {@code /data} it does not correct the ownership of {@code /import}.
 *
 * <b>Only a local Docker host works.</b> The generated files are bind-mounted from Polypheny's
 * filesystem, and the Docker protocol has no way to transfer files, so the daemon has to be able
 * to see those paths. This is checked before anything is written.
 *
 * <h2>Scope</h2>
 *
 * A load replaces whatever is in the store, so this is a pre-fill rather than an incremental
 * load, and it covers the initial snapshot only — the LDBC update streams are not handled. The
 * adapter has to hold exactly one graph, since there is no way to tell which of several the
 * dataset belongs in.
 *
 * <h2>What it costs</h2>
 *
 * Roughly linear in the size of the dataset, measured up to SF 10: about eleven minutes for 30
 * million nodes and 177 million relationships, of which one minute is the transformation. Disk is
 * the tighter constraint, since a run needs the extracted dataset, the generated CSVs and the
 * store at the same time, around three times the dataset.
 */
package org.polypheny.db.adapter.neo4j.bulkimport;

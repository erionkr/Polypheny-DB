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

package org.polypheny.db.adapter.neo4j.bulkimport;

import java.util.List;

/**
 * Describes one LDBC SNB entity and how it maps onto the CSV format expected by
 * {@code neo4j-admin import}.
 */
public final class EntitySpec {

    /**
     * A node entity.
     *
     * @param sourceFile relative path within the dataset, e.g. {@code static/organisation_0_0.csv}
     * @param label the primary label, e.g. {@code Organisation}
     * @param typeColumn column holding a subtype that becomes an additional label, or null
     * @param extraLabels further labels every node of this entity carries, e.g. {@code Message}
     */
    public record Node(
            String sourceFile,
            String label,
            String typeColumn,
            List<String> extraLabels ) {

        public Node( String sourceFile, String label ) {
            this( sourceFile, label, null, List.of() );
        }


        public Node( String sourceFile, String label, String typeColumn ) {
            this( sourceFile, label, typeColumn, List.of() );
        }


        public String outputFile() {
            return label.toLowerCase() + ".csv";
        }
    }


    /**
     * A relationship entity.
     *
     * @param sourceFile relative path within the dataset, e.g. {@code dynamic/person_knows_person_0_0.csv}
     * @param type the relationship type, e.g. {@code KNOWS}
     * @param startSpace id space of the start node, e.g. {@code Person}
     * @param endSpace id space of the end node, e.g. {@code Person}
     */
    public record Edge(
            String sourceFile,
            String type,
            String startSpace,
            String endSpace ) {

        public String outputFile() {
            String name = sourceFile.substring( sourceFile.lastIndexOf( '/' ) + 1 );
            return name;
        }
    }


    private EntitySpec() {
        // utility holder
    }

}

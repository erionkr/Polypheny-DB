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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts LDBC SNB Interactive CSV files into the format {@code neo4j-admin import}
 * expects.
 * <p>
 * The two formats differ in three ways. First, neo4j-admin needs an explicit id column
 * and, because LDBC ids are only unique within an entity, that column has to name an id
 * space: {@code :ID(Place)} rather than plain {@code :ID}. Second, labels are carried in
 * a dedicated {@code :LABEL} column so that a single file can hold several subtypes.
 * Third, property columns need type annotations, otherwise everything is imported as a
 * string.
 * <p>
 * Every node additionally receives the namespace mapping label that Polypheny attaches to
 * graph data, so that bulk-imported data is indistinguishable from data written through
 * the regular Cypher path.
 */
public class LdbcCsvTransformer {

    private static final char DELIMITER = '|';

    /** LDBC stores multi-valued attributes as semicolon-separated lists. */
    private static final Set<String> ARRAY_COLUMNS = Set.of( "language", "email", "speaks" );

    /** Columns that should become numbers rather than strings. */
    private static final Set<String> LONG_COLUMNS = Set.of(
            "creationDate", "joinDate", "birthday", "length", "classYear", "workFrom" );

    private final String namespaceLabel;


    /**
     * @param namespaceLabel the mapping label Polypheny uses for the target graph,
     *         e.g. {@code ___n_4___}
     */
    public LdbcCsvTransformer( String namespaceLabel ) {
        this.namespaceLabel = namespaceLabel;
    }


    /**
     * Converts one node entity.
     *
     * @return the number of rows written
     */
    public long transformNodes( Path source, Path target, EntitySpec.Node spec ) throws IOException {
        try (BufferedReader in = Files.newBufferedReader( source, StandardCharsets.UTF_8 );
                BufferedWriter out = Files.newBufferedWriter( target, StandardCharsets.UTF_8 )) {

            List<String> header = split( requireHeader( in, source ) );

            int idIndex = header.indexOf( "id" );
            if ( idIndex < 0 ) {
                throw new IOException( "No 'id' column in " + source + ", found " + header );
            }
            int typeIndex = spec.typeColumn() == null ? -1 : header.indexOf( spec.typeColumn() );
            if ( spec.typeColumn() != null && typeIndex < 0 ) {
                throw new IOException( "No '" + spec.typeColumn() + "' column in " + source );
            }

            List<Integer> propertyIndices = new ArrayList<>();
            for ( int i = 0; i < header.size(); i++ ) {
                if ( i != idIndex && i != typeIndex ) {
                    propertyIndices.add( i );
                }
            }

            List<String> outHeader = new ArrayList<>();
            outHeader.add( ":ID(" + spec.label() + ")" );
            for ( int i : propertyIndices ) {
                outHeader.add( annotate( header.get( i ) ) );
            }
            outHeader.add( ":LABEL" );
            writeRow( out, outHeader );

            long rows = 0;
            String line;
            while ( (line = in.readLine()) != null ) {
                List<String> fields = split( line );

                List<String> row = new ArrayList<>();
                row.add( fields.get( idIndex ) );
                for ( int i : propertyIndices ) {
                    row.add( fields.get( i ) );
                }
                row.add( labelsFor( spec, typeIndex < 0 ? null : fields.get( typeIndex ) ) );

                writeRow( out, row );
                rows++;
            }
            return rows;
        }
    }


    /**
     * Converts one relationship entity.
     *
     * @return the number of rows written
     */
    public long transformEdges( Path source, Path target, EntitySpec.Edge spec ) throws IOException {
        try (BufferedReader in = Files.newBufferedReader( source, StandardCharsets.UTF_8 );
                BufferedWriter out = Files.newBufferedWriter( target, StandardCharsets.UTF_8 )) {

            List<String> header = split( requireHeader( in, source ) );

            // LDBC names the endpoint columns after the referenced entity, e.g.
            // "Person.id" and "Organisation.id". Self-referencing edges repeat the same
            // name, so the first two id columns are taken positionally.
            List<Integer> idIndices = new ArrayList<>();
            for ( int i = 0; i < header.size(); i++ ) {
                if ( header.get( i ).toLowerCase().endsWith( "id" ) ) {
                    idIndices.add( i );
                }
            }
            if ( idIndices.size() < 2 ) {
                throw new IOException( "Expected two id columns in " + source + ", found " + header );
            }
            int startIndex = idIndices.get( 0 );
            int endIndex = idIndices.get( 1 );

            List<Integer> propertyIndices = new ArrayList<>();
            for ( int i = 0; i < header.size(); i++ ) {
                if ( i != startIndex && i != endIndex ) {
                    propertyIndices.add( i );
                }
            }

            List<String> outHeader = new ArrayList<>();
            outHeader.add( ":START_ID(" + spec.startSpace() + ")" );
            outHeader.add( ":END_ID(" + spec.endSpace() + ")" );
            for ( int i : propertyIndices ) {
                outHeader.add( annotate( header.get( i ) ) );
            }
            writeRow( out, outHeader );

            long rows = 0;
            String line;
            while ( (line = in.readLine()) != null ) {
                List<String> fields = split( line );

                List<String> row = new ArrayList<>();
                row.add( fields.get( startIndex ) );
                row.add( fields.get( endIndex ) );
                for ( int i : propertyIndices ) {
                    row.add( fields.get( i ) );
                }

                writeRow( out, row );
                rows++;
            }
            return rows;
        }
    }


    private String labelsFor( EntitySpec.Node spec, String subtype ) {
        StringBuilder labels = new StringBuilder( namespaceLabel );
        labels.append( ';' ).append( spec.label() );
        for ( String extra : spec.extraLabels() ) {
            labels.append( ';' ).append( extra );
        }
        if ( subtype != null && !subtype.isBlank() ) {
            // The Interactive serializer lowercases subtypes (company, country, ...)
            labels.append( ';' )
                    .append( Character.toUpperCase( subtype.charAt( 0 ) ) )
                    .append( subtype.substring( 1 ) );
        }
        return labels.toString();
    }


    private static String annotate( String column ) {
        if ( ARRAY_COLUMNS.contains( column ) ) {
            return column + ":string[]";
        }
        if ( LONG_COLUMNS.contains( column ) ) {
            return column + ":long";
        }
        return column;
    }


    private static String requireHeader( BufferedReader in, Path source ) throws IOException {
        String header = in.readLine();
        if ( header == null ) {
            throw new IOException( "Empty input file: " + source );
        }
        return header;
    }


    private static List<String> split( String line ) {
        return List.of( line.split( "\\|", -1 ) );
    }


    private static void writeRow( BufferedWriter out, List<String> fields ) throws IOException {
        out.write( String.join( String.valueOf( DELIMITER ), fields ) );
        out.newLine();
    }

}

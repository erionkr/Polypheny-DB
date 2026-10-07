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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LdbcCsvTransformerTest {

    private static final String NAMESPACE_LABEL = "___n_4___";


    @Test
    public void nodeWithSubtypeGetsThreeLabels( @TempDir Path tmp ) throws IOException {
        Path source = tmp.resolve( "organisation_0_0.csv" );
        Files.writeString( source, """
                id|type|name|url
                0|company|Kam_Air|http://dbpedia.org/resource/Kam_Air
                1|university|Balkh_University|http://dbpedia.org/resource/Balkh_University
                """, StandardCharsets.UTF_8 );

        Path target = tmp.resolve( "organisation.csv" );
        EntitySpec.Node spec = new EntitySpec.Node( "static/organisation_0_0.csv", "Organisation", "type" );

        long rows = new LdbcCsvTransformer( NAMESPACE_LABEL ).transformNodes( source, target, spec );

        assertEquals( 2, rows );
        List<String> lines = Files.readAllLines( target );
        assertEquals( ":ID(Organisation)|name|url|:LABEL", lines.get( 0 ) );
        assertEquals( "0|Kam_Air|http://dbpedia.org/resource/Kam_Air|___n_4___;Organisation;Company", lines.get( 1 ) );
        assertEquals( "1|Balkh_University|http://dbpedia.org/resource/Balkh_University|___n_4___;Organisation;University", lines.get( 2 ) );
    }


    @Test
    public void nodeWithExtraLabelAndTypedColumns( @TempDir Path tmp ) throws IOException {
        Path source = tmp.resolve( "comment_0_0.csv" );
        Files.writeString( source, """
                id|creationDate|locationIP|browserUsed|content|length
                618475290625|1313591219961|46.16.217.105|Chrome|yes|3
                """, StandardCharsets.UTF_8 );

        Path target = tmp.resolve( "comment.csv" );
        EntitySpec.Node spec = new EntitySpec.Node(
                "dynamic/comment_0_0.csv", "Comment", null, List.of( "Message" ) );

        long rows = new LdbcCsvTransformer( NAMESPACE_LABEL ).transformNodes( source, target, spec );

        assertEquals( 1, rows );
        List<String> lines = Files.readAllLines( target );
        assertEquals(
                ":ID(Comment)|creationDate:long|locationIP|browserUsed|content|length:long|:LABEL",
                lines.get( 0 ) );
        assertTrue( lines.get( 1 ).endsWith( "___n_4___;Comment;Message" ) );
    }


    @Test
    public void arrayColumnsAreAnnotated( @TempDir Path tmp ) throws IOException {
        Path source = tmp.resolve( "person_0_0.csv" );
        Files.writeString( source, """
                id|firstName|lastName|gender|birthday|creationDate|locationIP|browserUsed|language|email
                933|Mahinda|Perera|male|628646400000|1266161530447|119.235.7.103|Firefox|si;en|a@b.com;c@d.com
                """, StandardCharsets.UTF_8 );

        Path target = tmp.resolve( "person.csv" );
        EntitySpec.Node spec = new EntitySpec.Node( "dynamic/person_0_0.csv", "Person" );

        new LdbcCsvTransformer( NAMESPACE_LABEL ).transformNodes( source, target, spec );

        String header = Files.readAllLines( target ).get( 0 );
        assertTrue( header.contains( "language:string[]" ), header );
        assertTrue( header.contains( "email:string[]" ), header );
        assertTrue( header.contains( "birthday:long" ), header );
    }


    @Test
    public void selfReferencingEdgeUsesPositionalIdColumns( @TempDir Path tmp ) throws IOException {
        Path source = tmp.resolve( "person_knows_person_0_0.csv" );
        Files.writeString( source, """
                Person.id|Person.id|creationDate
                933|2199023256077|1271939457947
                """, StandardCharsets.UTF_8 );

        Path target = tmp.resolve( "knows.csv" );
        EntitySpec.Edge spec = new EntitySpec.Edge(
                "dynamic/person_knows_person_0_0.csv", "KNOWS", "Person", "Person" );

        long rows = new LdbcCsvTransformer( NAMESPACE_LABEL ).transformEdges( source, target, spec );

        assertEquals( 1, rows );
        List<String> lines = Files.readAllLines( target );
        assertEquals( ":START_ID(Person)|:END_ID(Person)|creationDate:long", lines.get( 0 ) );
        assertEquals( "933|2199023256077|1271939457947", lines.get( 1 ) );
    }


    @Test
    public void edgeWithoutPropertiesWorks( @TempDir Path tmp ) throws IOException {
        Path source = tmp.resolve( "place_isPartOf_place_0_0.csv" );
        Files.writeString( source, """
                Place.id|Place.id
                0|1454
                """, StandardCharsets.UTF_8 );

        Path target = tmp.resolve( "isPartOf.csv" );
        EntitySpec.Edge spec = new EntitySpec.Edge(
                "static/place_isPartOf_place_0_0.csv", "IS_PART_OF", "Place", "Place" );

        long rows = new LdbcCsvTransformer( NAMESPACE_LABEL ).transformEdges( source, target, spec );

        assertEquals( 1, rows );
        List<String> lines = Files.readAllLines( target );
        assertEquals( ":START_ID(Place)|:END_ID(Place)", lines.get( 0 ) );
    }

}

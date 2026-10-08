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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.docker.DockerContainer;
import org.polypheny.db.docker.DockerInstance;

/**
 * Pre-fills a Neo4j store from an LDBC SNB dataset using Neo4j's offline bulk loader.
 * <p>
 * {@code neo4j-admin import} refuses to run against a database that is in use, so the import
 * happens in a throwaway container whose command is overridden to keep Neo4j from starting.
 * That container and the Neo4j instance serving queries afterwards mount the same data volume.
 * <p>
 * <b>Limitation:</b> the transformed CSVs are written to Polypheny's filesystem and bind-mounted
 * into the import container, which only works when Polypheny and the Docker daemon share a
 * filesystem. Supporting remote Docker hosts would require a file transfer in the Polypheny
 * Docker protocol, which currently offers only container lifecycle, command execution and
 * volume management.
 */
@Slf4j
public class BulkImporter {

    private static final String LOCAL_HOSTNAME = "localhost";
    private static final String IMPORT_CONTAINER_SUFFIX = "_bulk_importer";
    private static final String DATA_MOUNT = "/data";
    private static final String CSV_MOUNT = "/import";
    private static final String IMPORT_HEAP_SIZE = "2G";

    private final DockerInstance dockerInstance;
    private final String imageName;
    private final String dataVolume;
    private final String namespaceLabel;


    /**
     * @param dockerInstance the Docker instance hosting the Neo4j containers
     * @param imageName the Neo4j image to use, e.g. {@code polypheny/neo:latest}
     * @param dataVolume name of the volume holding the Neo4j store files
     * @param namespaceLabel the mapping label Polypheny uses for the target graph
     */
    public BulkImporter(
            DockerInstance dockerInstance,
            String imageName,
            String dataVolume,
            String namespaceLabel ) {
        this.dockerInstance = dockerInstance;
        this.imageName = imageName;
        this.dataVolume = dataVolume;
        this.namespaceLabel = namespaceLabel;
    }


    /**
     * Transforms an LDBC dataset and loads it into the data volume.
     *
     * @param datasetRoot directory holding the {@code static/} and {@code dynamic/} subdirectories
     * @return a summary of what was imported
     * @throws IOException if the Docker host is remote, or if transformation or import fails
     */
    public ImportResult importDataset( Path datasetRoot ) throws IOException {
        requireLocalDockerHost();

        Path csvDirectory = Files.createTempDirectory( "polypheny-neo4j-bulk-" );
        // The Neo4j image runs as its own user and refuses to start when it cannot read a
        // mounted folder, so the transformed CSVs have to be world-readable.
        makeWorldReadable( csvDirectory );
        try {
            long transformStarted = System.nanoTime();
            TransformResult transformed = transform( datasetRoot, csvDirectory );
            long transformMillis = (System.nanoTime() - transformStarted) / 1_000_000;

            makeFilesWorldReadable( csvDirectory );

            long importStarted = System.nanoTime();
            runImport( csvDirectory );
            long importMillis = (System.nanoTime() - importStarted) / 1_000_000;

            log.info( "Bulk imported {} nodes and {} relationships ({} ms transform, {} ms import)",
                    transformed.nodeRows(), transformed.edgeRows(), transformMillis, importMillis );

            return new ImportResult(
                    transformed.nodeRows(), transformed.edgeRows(), transformMillis, importMillis );
        } finally {
            deleteRecursively( csvDirectory );
        }
    }


    /**
     * The CSVs are bind-mounted from Polypheny's filesystem, so the Docker daemon has to be
     * able to see those paths. Failing early beats an import that silently finds no input.
     */
    private void requireLocalDockerHost() throws IOException {
        String hostname = dockerInstance.getHost().hostname();
        if ( !LOCAL_HOSTNAME.equals( hostname ) ) {
            throw new IOException( String.format(
                    "Bulk import requires a local Docker host because the generated CSVs are "
                            + "bind-mounted from Polypheny's filesystem, but the configured host is '%s'.",
                    hostname ) );
        }
    }


    private TransformResult transform( Path datasetRoot, Path csvDirectory ) throws IOException {
        LdbcCsvTransformer transformer = new LdbcCsvTransformer( namespaceLabel );

        long nodeRows = 0;
        for ( EntitySpec.Node spec : LdbcSchema.nodes() ) {
            Path source = datasetRoot.resolve( spec.sourceFile() );
            Path target = csvDirectory.resolve( spec.outputFile() );
            long rows = transformer.transformNodes( source, target, spec );
            log.debug( "Transformed {} rows for {}", rows, spec.label() );
            nodeRows += rows;
        }

        long edgeRows = 0;
        for ( EntitySpec.Edge spec : LdbcSchema.edges() ) {
            Path source = datasetRoot.resolve( spec.sourceFile() );
            Path target = csvDirectory.resolve( spec.outputFile() );
            long rows = transformer.transformEdges( source, target, spec );
            log.debug( "Transformed {} rows for {}", rows, spec.type() );
            edgeRows += rows;
        }

        return new TransformResult( nodeRows, edgeRows );
    }


    private void runImport( Path csvDirectory ) throws IOException {
        DockerContainer importer = dockerInstance
                .newBuilder( imageName, uniqueImporterName() )
                // Overriding the command keeps the entrypoint from starting Neo4j, which would
                // make the database unavailable to neo4j-admin import.
                .withCommand( List.of( "sleep", "infinity" ) )
                // neo4j-admin import is memory hungry; the default heap gets the process killed
                .withEnvironmentVariable( "HEAP_SIZE", IMPORT_HEAP_SIZE )
                .withVolume( dataVolume, DATA_MOUNT )
                .withVolume( csvDirectory.toAbsolutePath().toString(), CSV_MOUNT, true )
                .createAndStart();

        try {
            int exitCode = importer.execute( buildImportCommand() );
            if ( exitCode != 0 ) {
                throw new IOException( "neo4j-admin import failed with exit code " + exitCode );
            }
        } finally {
            importer.destroy();
        }
    }


    private List<String> buildImportCommand() {
        List<String> command = new ArrayList<>();
        command.add( "neo4j-admin" );
        command.add( "import" );
        command.add( "--database=neo4j" );

        for ( EntitySpec.Node spec : LdbcSchema.nodes() ) {
            command.add( "--nodes=" + CSV_MOUNT + "/" + spec.outputFile() );
        }
        for ( EntitySpec.Edge spec : LdbcSchema.edges() ) {
            command.add( "--relationships=" + spec.type() + "=" + CSV_MOUNT + "/" + spec.outputFile() );
        }

        command.add( "--delimiter=|" );
        command.add( "--array-delimiter=;" );
        command.add( "--force" );
        return command;
    }


    private String uniqueImporterName() {
        // dataVolume already carries the physical prefix the builder adds again, so only
        // the adapter-specific part is kept to avoid a doubled container name.
        int lastSeparator = dataVolume.lastIndexOf( "_" + "data" );
        String adapterPart = lastSeparator < 0 ? dataVolume : dataVolume.substring( 0, lastSeparator );
        int prefixEnd = adapterPart.lastIndexOf( "_" );
        return (prefixEnd < 0 ? adapterPart : adapterPart.substring( prefixEnd + 1 )) + IMPORT_CONTAINER_SUFFIX;
    }


    private static void deleteRecursively( Path directory ) {
        try (var paths = Files.walk( directory )) {
            paths.sorted( Comparator.reverseOrder() ).forEach( path -> {
                try {
                    Files.deleteIfExists( path );
                } catch ( IOException e ) {
                    log.warn( "Could not delete {}", path, e );
                }
            } );
        } catch ( IOException e ) {
            log.warn( "Could not clean up {}", directory, e );
        }
    }


    private record TransformResult( long nodeRows, long edgeRows ) {
    }


    /**
     * Summary of a completed bulk import.
     */
    public record ImportResult(
            long nodes,
            long relationships,
            long transformMillis,
            long importMillis ) {
    }



    private static void makeWorldReadable( Path path ) throws IOException {
        Files.setPosixFilePermissions( path, PosixFilePermissions.fromString( "rwxr-xr-x" ) );
    }



    private static void makeFilesWorldReadable( Path directory ) throws IOException {
        try (var files = Files.list( directory )) {
            for ( Path file : files.toList() ) {
                Files.setPosixFilePermissions( file, PosixFilePermissions.fromString( "rw-r--r--" ) );
            }
        }
    }

}

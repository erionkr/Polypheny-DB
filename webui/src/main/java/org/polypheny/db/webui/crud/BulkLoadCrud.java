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

package org.polypheny.db.webui.crud;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.adapter.BulkLoadProgress;
import org.polypheny.db.adapter.BulkLoadable;
import org.polypheny.db.webui.models.BulkLoadJobModel;

/**
 * Runs bulk loads in the background and remembers how far they have got.
 * <p>
 * A load takes minutes, which is too long to hold an HTTP request open: the browser sits on a
 * connection it cannot do anything with, and the UI has nothing to show but a spinner. Loads run
 * on their own thread instead, and the caller asks for the state when it wants to.
 * <p>
 * Jobs live in memory and are gone after a restart. A load that was interrupted by one is gone
 * too, so there is nothing to resume and nothing to clean up.
 */
@Slf4j
public class BulkLoadCrud {

    private static final BulkLoadCrud INSTANCE = new BulkLoadCrud();

    private final Map<String, AtomicReference<BulkLoadJobModel>> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool( runnable -> {
        Thread thread = new Thread( runnable, "bulk-load" );
        thread.setDaemon( true );
        return thread;
    } );


    private BulkLoadCrud() {
    }


    public static BulkLoadCrud getInstance() {
        return INSTANCE;
    }


    /**
     * Starts a load and returns immediately.
     *
     * @return the job, in its initial state, so the caller has the id to ask about later
     */
    public BulkLoadJobModel start( String adapterName, BulkLoadable loadable, Path datasetRoot ) {
        String id = UUID.randomUUID().toString();
        AtomicReference<BulkLoadJobModel> job = new AtomicReference<>( new BulkLoadJobModel(
                id, adapterName, BulkLoadJobModel.RUNNING, "starting", null, null, null, null ) );
        jobs.put( id, job );

        executor.submit( () -> {
            try {
                String summary = loadable.bulkLoad( datasetRoot, progress -> update( job, progress ) );
                log.info( "Bulk load into {}: {}", adapterName, summary );
                job.set( new BulkLoadJobModel(
                        id, adapterName, BulkLoadJobModel.DONE, "done", null, null, summary, null ) );
            } catch ( Exception e ) {
                log.error( "Bulk load into adapter {} failed", adapterName, e );
                job.set( new BulkLoadJobModel(
                        id, adapterName, BulkLoadJobModel.FAILED, "failed", null, null, null,
                        e.getMessage() ) );
            }
        } );

        return job.get();
    }


    /**
     * @return the job with this id, or null if there never was one
     */
    public BulkLoadJobModel get( String id ) {
        AtomicReference<BulkLoadJobModel> job = jobs.get( id );
        return job == null ? null : job.get();
    }


    private void update( AtomicReference<BulkLoadJobModel> job, BulkLoadProgress progress ) {
        BulkLoadJobModel current = job.get();
        job.set( new BulkLoadJobModel(
                current.id(), current.adapterName(), BulkLoadJobModel.RUNNING,
                progress.phase(), progress.step(), progress.total(), null, null ) );
    }

}

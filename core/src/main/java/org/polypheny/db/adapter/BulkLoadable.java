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

package org.polypheny.db.adapter;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Implemented by adapters that can load a dataset through the underlying store's own bulk
 * loader instead of going through Polypheny's query path.
 * <p>
 * Loading record by record does not scale to benchmark-sized datasets, so adapters whose store
 * offers an offline loader can expose it here. Implementations pre-fill the store, which means
 * any data already present is replaced.
 */
public interface BulkLoadable {

    /**
     * Loads a dataset into this adapter's store, replacing whatever it currently holds.
     *
     * @param datasetRoot directory holding the dataset in whatever layout the adapter expects
     * @return a short, human-readable summary of what was loaded
     * @throws RuntimeException if the dataset cannot be read or the load fails
     */
    default String bulkLoad( Path datasetRoot ) {
        return bulkLoad( datasetRoot, progress -> {
        } );
    }


    /**
     * Loads a dataset and reports where it has got to as it goes.
     * <p>
     * A load runs for minutes on a dataset of any size, so whoever started it needs to see
     * something other than a spinner. The callback is invoked from the loading thread.
     *
     * @param datasetRoot directory holding the dataset in whatever layout the adapter expects
     * @param onProgress called as the load moves from one phase to the next
     * @return a short, human-readable summary of what was loaded
     * @throws RuntimeException if the dataset cannot be read or the load fails
     */
    String bulkLoad( Path datasetRoot, Consumer<BulkLoadProgress> onProgress );
}

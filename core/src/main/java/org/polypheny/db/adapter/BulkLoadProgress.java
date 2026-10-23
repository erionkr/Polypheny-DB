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

import org.jetbrains.annotations.Nullable;

/**
 * Where a bulk load has got to.
 * <p>
 * A load of a benchmark-sized dataset runs for minutes, and most of that time is spent inside a
 * tool the adapter only waits on, so the steps it can actually count are few. Phases without a
 * count still say which one is running, which is the difference between a progress bar and a
 * spinner that might mean anything.
 *
 * @param phase what is happening, in words meant for whoever started the load
 * @param step how far along, or null where there is nothing to count
 * @param total how many steps the phase has, or null for the same reason
 */
public record BulkLoadProgress( String phase, @Nullable Integer step, @Nullable Integer total ) {

    public static BulkLoadProgress of( String phase ) {
        return new BulkLoadProgress( phase, null, null );
    }


    public static BulkLoadProgress of( String phase, int step, int total ) {
        return new BulkLoadProgress( phase, step, total );
    }

}

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

package org.polypheny.db.webui.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A bulk load, as it looks to whoever started it.
 * <p>
 * The load itself runs on its own thread; this is what the caller gets back when asking how far
 * it has got.
 *
 * @param id identifies this load until Polypheny restarts
 * @param adapterName the adapter being loaded into
 * @param state one of running, done or failed
 * @param phase what the load is doing, or what it last did
 * @param step how far through the current phase, where it can be counted
 * @param total how many steps that phase has, for the same reason
 * @param summary what was loaded, once it is done
 * @param error why it stopped, if it did
 */
public record BulkLoadJobModel(
        @JsonProperty String id,
        @JsonProperty String adapterName,
        @JsonProperty String state,
        @JsonProperty String phase,
        @JsonProperty @Nullable Integer step,
        @JsonProperty @Nullable Integer total,
        @JsonProperty @Nullable String summary,
        @JsonProperty @Nullable String error ) {

    public static final String RUNNING = "running";
    public static final String DONE = "done";
    public static final String FAILED = "failed";

}

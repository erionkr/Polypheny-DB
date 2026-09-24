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

/**
 * Implemented by adapters that can drop all of their data without being redeployed.
 * <p>
 * This is primarily intended for development and testing, where re-creating an adapter
 * just to get a clean state is unnecessarily expensive. Implementations are expected to
 * remove all data managed by the adapter, but to leave the adapter itself operational.
 */
public interface Resettable {

    /**
     * Removes all data managed by this adapter.
     *
     * @throws RuntimeException if the underlying store could not be reset
     */
    void resetData();

}

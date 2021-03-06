/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.matrix.android.internal.session.filter

import com.zhuinden.monarchy.Monarchy
import im.vector.matrix.android.internal.database.model.FilterEntity
import im.vector.matrix.android.internal.database.model.FilterEntityFields
import im.vector.matrix.android.internal.database.query.get
import im.vector.matrix.android.internal.database.query.getOrCreate
import im.vector.matrix.android.internal.di.SessionDatabase
import im.vector.matrix.android.internal.util.awaitTransaction
import io.realm.Realm
import io.realm.kotlin.where
import javax.inject.Inject

internal class DefaultFilterRepository @Inject constructor(@SessionDatabase private val monarchy: Monarchy) : FilterRepository {

    override suspend fun storeFilter(filter: Filter, roomEventFilter: RoomEventFilter): Boolean {
        return Realm.getInstance(monarchy.realmConfiguration).use { realm ->
            val filterEntity = FilterEntity.get(realm)
            // Filter has changed, or no filter Id yet
            filterEntity == null
                    || filterEntity.filterBodyJson != filter.toJSONString()
                    || filterEntity.filterId.isBlank()
        }.also { hasChanged ->
            if (hasChanged) {
                // Filter is new or has changed, store it and reset the filter Id.
                // This has to be done outside of the Realm.use(), because awaitTransaction change the current thread
                monarchy.awaitTransaction { realm ->
                    // We manage only one filter for now
                    val filterJson = filter.toJSONString()
                    val roomEventFilterJson = roomEventFilter.toJSONString()

                    val filterEntity = FilterEntity.getOrCreate(realm)

                    filterEntity.filterBodyJson = filterJson
                    filterEntity.roomEventFilterJson = roomEventFilterJson
                    // Reset filterId
                    filterEntity.filterId = ""
                }
            }
        }
    }

    override suspend fun storeFilterId(filter: Filter, filterId: String) {
        monarchy.awaitTransaction {
            // We manage only one filter for now
            val filterJson = filter.toJSONString()

            // Update the filter id, only if the filter body matches
            it.where<FilterEntity>()
                    .equalTo(FilterEntityFields.FILTER_BODY_JSON, filterJson)
                    ?.findFirst()
                    ?.filterId = filterId
        }
    }

    override suspend fun getFilter(): String {
        return monarchy.awaitTransaction {
            val filter = FilterEntity.getOrCreate(it)
            if (filter.filterId.isBlank()) {
                // Use the Json format
                filter.filterBodyJson
            } else {
                // Use FilterId
                filter.filterId
            }
        }
    }

    override suspend fun getRoomFilter(): String {
        return monarchy.awaitTransaction {
            FilterEntity.getOrCreate(it).roomEventFilterJson
        }
    }
}

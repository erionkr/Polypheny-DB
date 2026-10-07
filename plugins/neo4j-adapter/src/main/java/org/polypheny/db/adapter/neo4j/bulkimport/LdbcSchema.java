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
 * The LDBC SNB Interactive schema, as produced by the {@code CsvComposite} serializer.
 * <p>
 * Nodes are listed before edges because {@code neo4j-admin import} resolves relationship
 * endpoints against the node id spaces.
 */
public final class LdbcSchema {

    private static final List<EntitySpec.Node> NODES = List.of(
            new EntitySpec.Node( "static/place_0_0.csv", "Place", "type" ),
            new EntitySpec.Node( "static/organisation_0_0.csv", "Organisation", "type" ),
            new EntitySpec.Node( "static/tagclass_0_0.csv", "TagClass" ),
            new EntitySpec.Node( "static/tag_0_0.csv", "Tag" ),
            new EntitySpec.Node( "dynamic/person_0_0.csv", "Person" ),
            new EntitySpec.Node( "dynamic/forum_0_0.csv", "Forum" ),
            new EntitySpec.Node( "dynamic/post_0_0.csv", "Post", null, List.of( "Message" ) ),
            new EntitySpec.Node( "dynamic/comment_0_0.csv", "Comment", null, List.of( "Message" ) )
    );

    private static final List<EntitySpec.Edge> EDGES = List.of(
            new EntitySpec.Edge( "static/place_isPartOf_place_0_0.csv", "IS_PART_OF", "Place", "Place" ),
            new EntitySpec.Edge( "static/tagclass_isSubclassOf_tagclass_0_0.csv", "IS_SUBCLASS_OF", "TagClass", "TagClass" ),
            new EntitySpec.Edge( "static/organisation_isLocatedIn_place_0_0.csv", "IS_LOCATED_IN", "Organisation", "Place" ),
            new EntitySpec.Edge( "static/tag_hasType_tagclass_0_0.csv", "HAS_TYPE", "Tag", "TagClass" ),
            new EntitySpec.Edge( "dynamic/comment_hasCreator_person_0_0.csv", "HAS_CREATOR", "Comment", "Person" ),
            new EntitySpec.Edge( "dynamic/comment_hasTag_tag_0_0.csv", "HAS_TAG", "Comment", "Tag" ),
            new EntitySpec.Edge( "dynamic/comment_isLocatedIn_place_0_0.csv", "IS_LOCATED_IN", "Comment", "Place" ),
            new EntitySpec.Edge( "dynamic/comment_replyOf_comment_0_0.csv", "REPLY_OF", "Comment", "Comment" ),
            new EntitySpec.Edge( "dynamic/comment_replyOf_post_0_0.csv", "REPLY_OF", "Comment", "Post" ),
            new EntitySpec.Edge( "dynamic/forum_containerOf_post_0_0.csv", "CONTAINER_OF", "Forum", "Post" ),
            new EntitySpec.Edge( "dynamic/forum_hasMember_person_0_0.csv", "HAS_MEMBER", "Forum", "Person" ),
            new EntitySpec.Edge( "dynamic/forum_hasModerator_person_0_0.csv", "HAS_MODERATOR", "Forum", "Person" ),
            new EntitySpec.Edge( "dynamic/forum_hasTag_tag_0_0.csv", "HAS_TAG", "Forum", "Tag" ),
            new EntitySpec.Edge( "dynamic/person_hasInterest_tag_0_0.csv", "HAS_INTEREST", "Person", "Tag" ),
            new EntitySpec.Edge( "dynamic/person_isLocatedIn_place_0_0.csv", "IS_LOCATED_IN", "Person", "Place" ),
            new EntitySpec.Edge( "dynamic/person_knows_person_0_0.csv", "KNOWS", "Person", "Person" ),
            new EntitySpec.Edge( "dynamic/person_likes_comment_0_0.csv", "LIKES", "Person", "Comment" ),
            new EntitySpec.Edge( "dynamic/person_likes_post_0_0.csv", "LIKES", "Person", "Post" ),
            new EntitySpec.Edge( "dynamic/person_studyAt_organisation_0_0.csv", "STUDY_AT", "Person", "Organisation" ),
            new EntitySpec.Edge( "dynamic/person_workAt_organisation_0_0.csv", "WORK_AT", "Person", "Organisation" ),
            new EntitySpec.Edge( "dynamic/post_hasCreator_person_0_0.csv", "HAS_CREATOR", "Post", "Person" ),
            new EntitySpec.Edge( "dynamic/post_hasTag_tag_0_0.csv", "HAS_TAG", "Post", "Tag" ),
            new EntitySpec.Edge( "dynamic/post_isLocatedIn_place_0_0.csv", "IS_LOCATED_IN", "Post", "Place" )
    );


    public static List<EntitySpec.Node> nodes() {
        return NODES;
    }


    public static List<EntitySpec.Edge> edges() {
        return EDGES;
    }


    private LdbcSchema() {
        // utility holder
    }

}

#!/usr/bin/env bash
#
# Times neo4j-admin import on its own, with the files the adapter produced, so the
# difference to the adapter's import phase is the overhead the adapter adds.
#
# Run on the server. Expects the transformed CSV files in ~/import-sf3.

set -euo pipefail

CSV_DIR="${1:-$HOME/import-sf3}"
IMAGE="polypheny/neo:latest"
CONTAINER="native-import-benchmark"
VOLUME="native-import-benchmark-data"

if [ ! -d "$CSV_DIR" ]; then
    echo "No such directory: $CSV_DIR" >&2
    exit 1
fi

echo "CSV files:   $CSV_DIR"
echo "Files found: $(ls "$CSV_DIR" | wc -l)"
echo

# A fresh volume, so the import starts from an empty store as the adapter's does.
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker volume rm "$VOLUME" >/dev/null 2>&1 || true
docker volume create "$VOLUME" >/dev/null

docker run -d --name "$CONTAINER" \
    -e HEAP_SIZE=2G \
    -v "$VOLUME":/data \
    -v "$CSV_DIR":/import:ro \
    "$IMAGE" sleep infinity >/dev/null

# The same eight node files and 23 relationship files the adapter passes, in the same order.
NODES=(
    "/import/comment.csv"
    "/import/forum.csv"
    "/import/organisation.csv"
    "/import/person.csv"
    "/import/place.csv"
    "/import/post.csv"
    "/import/tag.csv"
    "/import/tagclass.csv"
)

RELS=(
    "HAS_CREATOR=/import/comment_hasCreator_person_0_0.csv"
    "HAS_TAG=/import/comment_hasTag_tag_0_0.csv"
    "IS_LOCATED_IN=/import/comment_isLocatedIn_place_0_0.csv"
    "REPLY_OF=/import/comment_replyOf_comment_0_0.csv"
    "REPLY_OF=/import/comment_replyOf_post_0_0.csv"
    "CONTAINER_OF=/import/forum_containerOf_post_0_0.csv"
    "HAS_MEMBER=/import/forum_hasMember_person_0_0.csv"
    "HAS_MODERATOR=/import/forum_hasModerator_person_0_0.csv"
    "HAS_TAG=/import/forum_hasTag_tag_0_0.csv"
    "IS_LOCATED_IN=/import/organisation_isLocatedIn_place_0_0.csv"
    "HAS_INTEREST=/import/person_hasInterest_tag_0_0.csv"
    "IS_LOCATED_IN=/import/person_isLocatedIn_place_0_0.csv"
    "KNOWS=/import/person_knows_person_0_0.csv"
    "LIKES=/import/person_likes_comment_0_0.csv"
    "LIKES=/import/person_likes_post_0_0.csv"
    "STUDY_AT=/import/person_studyAt_organisation_0_0.csv"
    "WORK_AT=/import/person_workAt_organisation_0_0.csv"
    "IS_PART_OF=/import/place_isPartOf_place_0_0.csv"
    "HAS_CREATOR=/import/post_hasCreator_person_0_0.csv"
    "HAS_TAG=/import/post_hasTag_tag_0_0.csv"
    "IS_LOCATED_IN=/import/post_isLocatedIn_place_0_0.csv"
    "HAS_TYPE=/import/tag_hasType_tagclass_0_0.csv"
    "IS_SUBCLASS_OF=/import/tagclass_isSubclassOf_tagclass_0_0.csv"
)

CMD=(neo4j-admin import --database=neo4j)
for n in "${NODES[@]}"; do CMD+=("--nodes=$n"); done
for r in "${RELS[@]}"; do CMD+=("--relationships=$r"); done
CMD+=(--delimiter='|' --array-delimiter=';')

echo "Running neo4j-admin import..."
START=$(date +%s)
docker exec "$CONTAINER" "${CMD[@]}" > /tmp/native-import.log 2>&1 || {
    echo "Import failed. Last lines:" >&2
    tail -20 /tmp/native-import.log >&2
    docker rm -f "$CONTAINER" >/dev/null
    exit 1
}
END=$(date +%s)

ELAPSED=$((END - START))
echo
echo "=========================================="
echo "neo4j-admin import on its own: ${ELAPSED}s  ($((ELAPSED / 60)) min $((ELAPSED % 60)) s)"
echo "=========================================="
echo
grep -iE "nodes|relationships|properties" /tmp/native-import.log | tail -5 || true

docker rm -f "$CONTAINER" >/dev/null
docker volume rm "$VOLUME" >/dev/null
echo
echo "Cleaned up."

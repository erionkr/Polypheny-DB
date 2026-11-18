#!/usr/bin/env bash
#
# Sends every LDBC query that Polypheny can answer to Polypheny and to Neo4j directly, and
# compares the rows. The survey establishes that a query translates and executes; this
# establishes that it answers the same thing the store would answer on its own.
#
# Uses the same parameter substitution as survey-ldbc-queries.sh, so the queries are
# identical to the ones the survey reports on.
#
# Run on the server, with a loaded store. Needs jq and a running neo4j0 container.

set -uo pipefail

QUERIES="$HOME/Polypheny-Simple-Client-ldbcsnb/build/resources/main/org/polypheny/simpleclient/scenario/ldbcsnb"
POLYPHENY="http://localhost:13137/cypher"
NAMESPACE="ldbcsnb"

command -v jq >/dev/null || { echo "jq is required but not installed." >&2; exit 1; }

# The queries that pass, by file name.
PASSING=(
    interactive-short-1
    interactive-short-3
    interactive-short-4
    interactive-short-5
    interactive-short-6
    interactive-complex-2
    interactive-complex-4
    interactive-complex-8
    interactive-complex-9
    interactive-complex-11
)

# Values that exist in the SF 0.1 dataset. Kept identical to survey-ldbc-queries.sh.
PERSON_ID=26388279067534
PERSON_ID_2=933
MESSAGE_ID=1030792408013
TAG_NAME="Hamid_Karzai"
TAG_CLASS="Person"
COUNTRY_X="India"
COUNTRY_Y="China"
FIRST_NAME="Mahinda"
DATE=1271939457947
DURATION=30
MONTH=5
LANGUAGES='["en"]'
MAX_DATE=1330473600000
MIN_DATE=1270000000000
START_DATE=1270000000000
END_DATE=1330473600000
CLASS_YEAR=2010
WORK_FROM_YEAR=2010

substitute() {
    sed \
        -e "s/\$personId/$PERSON_ID/g" \
        -e "s/\$person1Id/$PERSON_ID/g" \
        -e "s/\$person2Id/$PERSON_ID_2/g" \
        -e "s/\$messageId/$MESSAGE_ID/g" \
        -e "s/\$commentId/$MESSAGE_ID/g" \
        -e "s/\$postId/$MESSAGE_ID/g" \
        -e "s/\$tagName/\"$TAG_NAME\"/g" \
        -e "s/\$tagClassName/\"$TAG_CLASS\"/g" \
        -e "s/\$countryXName/\"$COUNTRY_X\"/g" \
        -e "s/\$countryYName/\"$COUNTRY_Y\"/g" \
        -e "s/\$countryName/\"$COUNTRY_X\"/g" \
        -e "s/\$firstName/\"$FIRST_NAME\"/g" \
        -e "s/\$date/$DATE/g" \
        -e "s/\$durationDays/$DURATION/g" \
        -e "s/\$month/$MONTH/g" \
        -e "s/\$languages/$LANGUAGES/g" \
        -e "s/\$maxDate/$MAX_DATE/g" \
        -e "s/\$minDate/$MIN_DATE/g" \
        -e "s/\$startDate/$START_DATE/g" \
        -e "s/\$endDate/$END_DATE/g" \
        -e "s/\$classYear/$CLASS_YEAR/g" \
        -e "s/\$workFromYear/$WORK_FROM_YEAR/g"
}

read_query() {
    sed -e '/^\/\//d' -e '/^\/\*/,/^\*\//d' "$1" | substitute | tr '\n' ' '
}

# Builds the request body. jq -Rs reads the whole input as one raw string, which is also what
# does the escaping: a query contains quotes and braces that must not break the JSON.
request_body() {
    jq -Rs --arg ns "$NAMESPACE" '{query: (. | sub("^\\s+";"") | sub("\\s+$";"")), namespace: $ns}'
}

# One line per row, cells joined by a pipe. A response without a data field is an error from
# Polypheny rather than an empty result, and is reported as such.
response_rows() {
    jq -r 'if (.[0].data? // null) == null then "ERROR"
           else (.[0].data[] | map(tostring) | join("|")) end'
}

CONTAINER=$(docker ps -q --filter 'name=neo4j0')
if [ -z "$CONTAINER" ]; then
    echo "No neo4j0 container running." >&2
    exit 1
fi
NEO_PASS=$(docker exec "$CONTAINER" printenv NEO4J_AUTH | cut -d/ -f2)

mkdir -p /tmp/verify-diff
rm -f /tmp/verify-diff/*

printf "%-24s %-10s %-10s %s\n" "QUERY" "POLYPHENY" "NEO4J" "RESULT"
printf -- "---------------------------------------------------------------------\n"

SAME=0
DIFF=0

for name in "${PASSING[@]}"; do
    FILE="$QUERIES/$name.cypher"
    if [ ! -f "$FILE" ]; then
        printf "%-24s %-10s %-10s %s\n" "$name" "-" "-" "file not found"
        continue
    fi

    QUERY=$(read_query "$FILE")

    BODY=$(printf '%s' "$QUERY" | request_body) || BODY=""
    if [ -z "$BODY" ]; then
        printf "%-24s %-10s %-10s %s\n" "$name" "-" "-" "could not build request"
        DIFF=$((DIFF + 1))
        continue
    fi

    POLY_ROWS=$(curl -s -X POST -H "Content-Type: application/json" \
        --data-binary "$BODY" "$POLYPHENY" | response_rows 2>/dev/null) || POLY_ROWS="ERROR"

    NEO_ROWS=$(docker exec "$CONTAINER" cypher-shell -u neo4j -p "$NEO_PASS" \
        --format plain "$QUERY" 2>/dev/null | tail -n +2 | sed 's/, /|/g; s/"//g')

    POLY_COUNT=$(printf '%s' "$POLY_ROWS" | grep -c . || true)
    NEO_COUNT=$(printf '%s' "$NEO_ROWS" | grep -c . || true)

    if [ "$POLY_ROWS" = "ERROR" ]; then
        VERDICT="polypheny failed"
        DIFF=$((DIFF + 1))
    elif [ "$POLY_COUNT" != "$NEO_COUNT" ]; then
        VERDICT="row count differs"
        DIFF=$((DIFF + 1))
        printf '%s\n' "$POLY_ROWS" > "/tmp/verify-diff/$name.polypheny"
        printf '%s\n' "$NEO_ROWS"  > "/tmp/verify-diff/$name.neo4j"
    else
        # cypher-shell's plain format separates columns with ", ", which cannot be told apart
        # from a comma inside a string: a property containing prose is split into several
        # columns on the Neo4j side and stays one on Polypheny's. Comparing with separators
        # and quotes removed from both sides avoids that false mismatch. It also means a
        # difference that consists only of punctuation would go unnoticed, which is the price.
        P=$(printf '%s' "$POLY_ROWS" | tr -d ' "|,')
        N=$(printf '%s' "$NEO_ROWS"  | tr -d ' "|,')
        if [ "$P" = "$N" ]; then
            VERDICT="identical"
            SAME=$((SAME + 1))
        else
            VERDICT="same count, values differ"
            DIFF=$((DIFF + 1))
            printf '%s\n' "$POLY_ROWS" > "/tmp/verify-diff/$name.polypheny"
            printf '%s\n' "$NEO_ROWS"  > "/tmp/verify-diff/$name.neo4j"
        fi
    fi

    printf "%-24s %-10s %-10s %s\n" "$name" "$POLY_COUNT" "$NEO_COUNT" "$VERDICT"
done

printf -- "---------------------------------------------------------------------\n"
echo "$SAME identical, $DIFF differing"
if [ -n "$(ls -A /tmp/verify-diff 2>/dev/null)" ]; then
    echo "Differences written to /tmp/verify-diff/"
fi

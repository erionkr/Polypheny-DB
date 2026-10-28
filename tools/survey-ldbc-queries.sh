#!/bin/bash
#
# Runs every LDBC SNB Interactive query through Polypheny and records what happens.
#
# The point is not whether a query returns the right rows — the parameters here are picked from
# whatever is in the store, not from the official substitution parameters. The point is which
# ones Polypheny can translate at all, and what it says about the ones it cannot.
#
# Usage: ./survey-ldbc-queries.sh [> report.txt]

set -u

ENDPOINT="http://localhost:13137/cypher"
NAMESPACE="ldbcsnb"
QUERIES="$HOME/Developer/Polypheny-Simple-Client-ldbcsnb/build/resources/main/org/polypheny/simpleclient/scenario/ldbcsnb"

# Values that exist in the SF 0.1 dataset.
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
        -e "s/\$startDate/$START_DATE/g" \
        -e "s/\$endDate/$END_DATE/g" \
        -e "s/\$maxDate/$MAX_DATE/g" \
        -e "s/\$minDate/$MIN_DATE/g" \
        -e "s/\$durationDays/$DURATION/g" \
        -e "s/\$month/$MONTH/g" \
        -e "s/\$classYear/$CLASS_YEAR/g" \
        -e "s/\$workFromYear/$WORK_FROM_YEAR/g" \
        -e "s/\$languages/$LANGUAGES/g" \
        -e "s/\$limit/10/g"
}

# Strips the comment header and the :params block, leaving the query itself.
extract() {
    sed -e '/^\/\//d' -e '/^\/\*/,/^\*\//d' "$1" | substitute
}

run_one() {
    local file="$1"
    local name
    name=$(basename "$file" .cypher)

    local query
    query=$(extract "$file")

    local payload
    payload=$(/usr/bin/python3 -c "
import json, sys
print(json.dumps({'query': sys.stdin.read(), 'namespace': '$NAMESPACE'}))
" <<< "$query")

    local response
    response=$(curl -s -X POST -H "Content-Type: application/json" -d "$payload" "$ENDPOINT")

    /usr/bin/python3 -c "
import json, sys

name = '$name'
try:
    data = json.loads(sys.stdin.read())[0]
except Exception as e:
    print(f'{name}\tUNPARSEABLE\t{e}')
    sys.exit()

error = data.get('error')
if not error:
    rows = len(data.get('data', []))
    print(f'{name}\tOK\t{rows} rows')
else:
    print(f'{name}\tFAILED\t' + ' '.join(error.split())[:200])
" <<< "$response"
}

echo "LDBC SNB Interactive queries through Polypheny"
echo "Dataset: SF 0.1, parameters picked from the store rather than the official ones"
echo "Date: $(date '+%Y-%m-%d %H:%M')"
echo ""
printf '%-28s %-12s %s\n' "QUERY" "RESULT" "DETAIL"
printf '%s\n' "--------------------------------------------------------------------------------"

for n in 1 2 3 4 5 6 7; do
    f="$QUERIES/interactive-short-$n.cypher"
    [ -f "$f" ] && run_one "$f" | awk -F'\t' '{printf "%-28s %-12s %s\n", $1, $2, $3}'
done

echo ""

for n in 1 2 3 4 5 6 7 8 9 10 11 12 13 14; do
    f="$QUERIES/interactive-complex-$n.cypher"
    [ -f "$f" ] && run_one "$f" | awk -F'\t' '{printf "%-28s %-12s %s\n", $1, $2, $3}'
done

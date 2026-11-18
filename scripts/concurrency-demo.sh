#!/usr/bin/env bash
# several clients book the same new slot at the same moment through the gateway: exactly one should get 201 and the
# rest 409, also with more than one booking-service instance running. Works in Git Bash, needs only curl.
#
#   GATEWAY_URL       default http://localhost:8080
#   ADVISOR_EMAIL     advisor who creates the slot
#   ADVISOR_PASSWORD
#   CLIENT_EMAILS     space separated client emails, one booking each
#   CLIENT_PASSWORD   the same password for every client
#   SERVICE_TYPE_ID   a service type the advisor offers. If it requires KYC, every client needs an approved document
set -u

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL%/}"

missing=""
for name in ADVISOR_EMAIL ADVISOR_PASSWORD CLIENT_EMAILS CLIENT_PASSWORD SERVICE_TYPE_ID; do
    [ -z "${!name:-}" ] && missing="$missing $name"
done
if [ -n "$missing" ]; then
    echo "Missing environment variables:$missing" >&2
    exit 2
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# prints the token, or nothing. /auth/login allows 5 requests a minute, so a 429 waits and tries again
login() {
    local email="$1" password="$2" status attempt
    for attempt in 1 2 3 4 5 6; do
        status=$(curl -s -o "$work/login.json" -w '%{http_code}' -X POST "$GATEWAY_URL/auth/login" \
            -H 'Content-Type: application/json' \
            -d "{\"email\":\"$email\",\"password\":\"$password\"}")
        if [ "$status" = "429" ]; then
            echo "  login rate limit reached, waiting 15s ($email)" >&2
            sleep 15
            continue
        fi
        if [ "$status" != "200" ]; then
            echo "  login failed for $email: HTTP $status" >&2
            return 1
        fi
        grep -o '"token":"[^"]*"' "$work/login.json" | head -1 | sed 's/"token":"//; s/"$//'
        return 0
    done
    echo "  login for $email still rate limited, giving up" >&2
    return 1
}

echo "Gateway: $GATEWAY_URL"

echo "Logging in the advisor"
advisor_token=$(login "$ADVISOR_EMAIL" "$ADVISOR_PASSWORD") || exit 1
[ -n "$advisor_token" ] || { echo "No token in the advisor's login response" >&2; exit 1; }

# a fresh one-hour slot 30 to 330 days ahead, so it does not overlap an earlier run
slot_id=""
for attempt in 1 2 3 4 5; do
    offset=$(( (RANDOM % 300 + 30) * 1440 + (RANDOM % 20) * 60 ))
    start=$(date -d "+$offset minutes" +%Y-%m-%dT%H:00:00)
    end=$(date -d "+$((offset + 60)) minutes" +%Y-%m-%dT%H:00:00)
    status=$(curl -s -o "$work/slot.json" -w '%{http_code}' -X POST "$GATEWAY_URL/slots" \
        -H "Authorization: Bearer $advisor_token" -H 'Content-Type: application/json' \
        -d "{\"startingTime\":\"$start\",\"finishingTime\":\"$end\"}")
    if [ "$status" = "201" ]; then
        slot_id=$(grep -o '"id":[0-9]*' "$work/slot.json" | head -1 | sed 's/"id"://')
        break
    fi
    echo "  could not create the slot (HTTP $status): $(cat "$work/slot.json")" >&2
done
[ -n "$slot_id" ] || { echo "No slot was created" >&2; exit 1; }
echo "Created slot $slot_id ($start to $end)"

echo "Logging in the clients"
clients=()
tokens=()
for email in $CLIENT_EMAILS; do
    token=$(login "$email" "$CLIENT_PASSWORD") || exit 1
    [ -n "$token" ] || { echo "No token in the login response for $email" >&2; exit 1; }
    clients+=("$email")
    tokens+=("$token")
done
echo "${#clients[@]} clients logged in"

# every request waits for the go file, so they all leave at the same moment
go="$work/go"
for i in "${!clients[@]}"; do
    (
        while [ ! -f "$go" ]; do sleep 0.01; done
        curl -s -o "$work/book-$i.json" -w '%{http_code}' -X POST "$GATEWAY_URL/appointments/book/$slot_id" \
            -H "Authorization: Bearer ${tokens[$i]}" -H 'Content-Type: application/json' \
            -d "{\"serviceTypeId\":$SERVICE_TYPE_ID}" > "$work/status-$i"
    ) &
done
sleep 1
echo "Booking slot $slot_id with ${#clients[@]} clients at once"
touch "$go"
wait

created=0; conflict=0; other=0
for i in "${!clients[@]}"; do
    status=$(cat "$work/status-$i" 2>/dev/null)
    echo "  ${clients[$i]}: ${status:-no response}"
    case "$status" in
        201) created=$((created + 1)) ;;
        409) conflict=$((conflict + 1)) ;;
        *)   other=$((other + 1)); echo "    $(cat "$work/book-$i.json" 2>/dev/null)" ;;
    esac
done

echo "201: $created, 409: $conflict, other: $other"
if [ "$created" -eq 1 ] && [ "$other" -eq 0 ]; then
    echo "PASS"
    exit 0
fi
echo "FAIL"
exit 1

#!/bin/sh
# runs first (00-) when postgres starts with an empty data volume. It creates finconsult_test, the database the
# services' tests use, and loads the same schema and seed into it as 01-schema.sql and 02-seed.sql then load into
# the app's own database
set -e

if psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -tAc \
        "SELECT 1 FROM pg_database WHERE datname = 'finconsult_test'" | grep -q 1; then
    echo "finconsult_test already exists, leaving it as it is"
    exit 0
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE finconsult_test"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname finconsult_test -f /docker-entrypoint-initdb.d/01-schema.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname finconsult_test -f /docker-entrypoint-initdb.d/02-seed.sql

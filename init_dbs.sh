#!/bin/bash

_psql () { psql --set ON_ERROR_STOP=0 "$@" ; }

_psql --set=adminpass="$POSTGRESQL_ADMIN_PASSWORD" \
<<<"ALTER USER \"postgres\" WITH ENCRYPTED PASSWORD :'adminpass';"

for db in rhsm-subscriptions insights; do
  _psql --set=adminpass="$POSTGRESQL_ADMIN_PASSWORD" <<-EOSQL
      CREATE USER "$db" WITH PASSWORD '$db';
      CREATE DATABASE "$db";
      GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$db";
      ALTER USER "$db" WITH SUPERUSER;
EOSQL
done

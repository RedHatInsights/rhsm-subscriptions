#!/bin/bash

if [[ -v POSTGRESQL_USER || -v POSTGRESQL_PASSWORD || -v POSTGRESQL_DATABASE ]]; then
  cat >&2 <<EOF
  You have mounted the RHSM init_dbs.sh script.

  This script is *incompatible* with setting the POSTGRESL_USER/POSTGRESQL_DATABASE environment
  variables.  This script runs very early and later, the container scripts attempt to run
  createuser $POSTGRESQL_USER and if the user already exists, createuser fails and the container
  stops.

  Either do not mount init_dbs.sh or do not set the POSTGRESQL_USER and POSTGRESQL_DATABASE
  environment variables.
EOF
  exit 1
fi

for db in rhsm-subscriptions insights unleash; do
  psql --set ON_ERROR_STOP=1 <<-EOSQL
      CREATE USER "$db" WITH PASSWORD '$db';
      CREATE DATABASE "$db";
      GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$db";
      ALTER USER "$db" WITH SUPERUSER;
EOSQL
done

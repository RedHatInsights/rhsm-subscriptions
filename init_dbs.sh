#!/bin/sh
set -e

for db in rhsm-subscriptions insights; do
  psql <<-EOSQL
      CREATE USER "$db";
      CREATE DATABASE "$db";
      GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$db";
      ALTER USER "$db" WITH SUPERUSER;
EOSQL
done

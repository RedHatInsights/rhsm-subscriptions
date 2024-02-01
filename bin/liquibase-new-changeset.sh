#!/bin/bash
# Usage: liquibase-new-changeset.sh <text to append to the generated changeset>
# For example: running `liquibase-new-changeset.sh remove-account` would create a change set `202312050855-remove-account.xml` where `202312050855` is auto-populated from the current date.
if [ -d src/main/resources/liquibase ]; then
  LIQUIBASE_DIR=src/main/resources/liquibase
  CHANGELOG=changelog.xml
  SEPARATOR=-
else
  LIQUIBASE_DIR=src/main/resources/db
  CHANGELOG=changeLog.xml
  SEPARATOR=_
fi
desc=$(echo "$@" | sed "s/ /$SEPARATOR/g" | tr A-Z a-z)
date=$(date +%Y%m%d%H%M)
filename=$(date +%Y%m%d%H%M)$SEPARATOR$desc.xml
cat > $LIQUIBASE_DIR/$filename <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">
  <changeSet id="$date-01" author="$(whoami)">
    <!-- TODO -->
    <rollback>
      <!-- TODO -->
    </rollback>
  </changeSet>
</databaseChangeLog>
EOF

sed -i "/databaseChangeLog>/i\\    <include file=\"/$(basename $LIQUIBASE_DIR)/$filename\"/>" $LIQUIBASE_DIR/$CHANGELOG

echo Generated $LIQUIBASE_DIR/$filename

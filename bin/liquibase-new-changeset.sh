#!/bin/bash
# Usage: liquibase-new-changeset.sh <text to append to the generated changeset>
# For example: running `liquibase-new-changeset.sh remove-account` would create a change set `202312050855-remove-account.xml` where `202312050855` is auto-populated from the current date.
desc=$(echo "$@" | sed 's/ /-/g' | tr A-Z a-z)
date=$(date +%Y%m%d%H%M)
filename=$(date +%Y%m%d%H%M)-$desc.xml
cat > src/main/resources/liquibase/$filename <<EOF
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

sed -i "/databaseChangeLog>/i\\    <include file=\"/liquibase/$filename\"/>" src/main/resources/liquibase/changelog.xml

echo Generated src/main/resources/liquibase/$filename

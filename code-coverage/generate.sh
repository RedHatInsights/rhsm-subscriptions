#!/bin/bash

if ! [ -f code-coverage/jacoco.jar ]; then
  echo "Downloading Jacoco CLI..."
  curl -s -o code-coverage/jacoco.jar https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar
fi

services=('swatch-tally-service;build/classes/java/main;src/main/java')

finalJacocoReport="code-coverage/jacoco-all.exec"
rm -f ${finalJacocoReport}
rm -rf code-coverage/output
finalClasses=""

for service in ${services[@]}
do
  split=(${service//;/ })
  name=${split[0]}
  classes=${split[1]}
  sources=${split[2]}

  serviceJacocoReport="code-coverage/jacoco-${name}.exec"
  rm -f ${serviceJacocoReport}
  echo "Dump jacoco report for ${name}"
  oc port-forward $(oc get pods | grep ${name} | cut -d' ' -f1) 6300:6300 > /dev/null 2>&1 &
  pid=$!
  # sleep 5 seconds to wait for port handling
  sleep 5

  # kill the port-forward regardless of how this script exits
  trap '{
      if ps -p $pid > /dev/null
      then
         kill -9 $pid
      fi
  }' EXIT

  # generate report
  java -jar code-coverage/jacoco.jar dump --address localhost --port 6300 --destfile ${serviceJacocoReport}

  kill $pid

  # merge report
  if ! [ -f ${finalJacocoReport} ]; then
    cp ${serviceJacocoReport} ${finalJacocoReport}
  else
    java -jar code-coverage/jacoco.jar merge ${serviceJacocoReport} ${finalJacocoReport} --destfile ${finalJacocoReport}
  fi
  finalClasses=" --classfiles ${classes} ${finalClasses}"
  finalSources=" --sourcefiles ${sources} ${finalSources}"
done

java -jar code-coverage/jacoco.jar report ${finalJacocoReport} ${finalClasses} ${finalSources} --html code-coverage/output
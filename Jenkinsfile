pipeline {
    options { buildDiscarder(logRotator(numToKeepStr: '50')) }
    environment {
        SPRING_ACTIVE_PROFILE = "test"
    }
    agent {
        kubernetes {
            label 'swatch-17-kubedock' // this value + unique identifier becomes the pod name
            idleMinutes 5  // how long the pod will live after no jobs have run on it
            defaultContainer 'openjdk17'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: kubedock
      image: quay.io/jcarvaja/kubedock:0.13.0
      tty: true
      args:
       - server
       - --port-forward
       # Verbosity level which is helpful to troubleshot issues when starting up containers
       - -v
       - 10
    - name: openjdk17
      image: registry.access.redhat.com/ubi9/openjdk-17-runtime
      command:
      - sleep
      tty: true
      args:
      - 99d
      resources:
        requests:
          memory: "2Gi"
          cpu: "2"
        limits:
          memory: "6Gi"
          cpu: "6"
      env:
      - name: DOCKER_HOST
        value: tcp://127.0.0.1:2475
"""
        }
    }
    stages {
        stage('Verify PR ok to test') {
            when {
                beforeInput true
                expression { env.CHANGE_FORK }
                not {
                  anyOf {
                    // Kevin Howell
                    changeRequest author: "kahowell"
                    // Lindsey Burnett
                    changeRequest author: "lindseyburnett"
                    // Alex Wood
                    changeRequest author: "awood"
                    // Michael Stead
                    changeRequest author: "mstead"
                    // Kevin Flaherty
                    changeRequest author: "kflahert"
                    // Barnaby Court
                    changeRequest author: "barnabycourt"
                    // Nikhil Kathole
                    changeRequest author: "ntkathole"
                    // Jose Carvajal
                    changeRequest author: "Sgitario"
                    // Kenny Synvrit
                    changeRequest author: "ksynvrit"
                    // Kartik Shah
                    changeRequest author: "kartikshahc"
                    // Vanessa Busch
                    changeRequest author: "vbusch"
                  }
               }
            }
            steps {
                input 'ok to test?'
            }
        }
        stage('Build/Test/Lint') {
            steps {
                // The build task includes check, test, and assemble.  Linting happens during the check
                // task and uses the spotless gradle plugin.
                sh "./gradlew --no-daemon build testCodeCoverageReport"
            }
        }

        stage('Upload PR to SonarQube') {
            when {
                changeRequest()
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonar -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.token=${SONAR_AUTH_TOKEN} -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
                }
            }
        }
        stage('Upload Branch to SonarQube') {
            when {
                not {
                    changeRequest()
                }
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonar -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.token=${SONAR_AUTH_TOKEN} -Dsonar.branch.name=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
                }
            }
        }
        stage('SonarQube Quality Gate') {
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    echo "SonarQube scan results will be visible at: ${SONAR_HOST_URL}/summary/new_code?id=rhsm-subscriptions${env.CHANGE_ID != null ? '&pullRequest=' + env.CHANGE_ID : ''}"
                }
                retry(4) {
                    script {
                        try {
                            timeout(time: 5, unit: 'MINUTES') {
                                waitForQualityGate abortPipeline: true
                            }
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            // "rethrow" as something retry will actually retry, see https://issues.jenkins-ci.org/browse/JENKINS-51454
                            if (e.causes.find { it instanceof org.jenkinsci.plugins.workflow.steps.TimeoutStepExecution$ExceededTimeout } != null) {
                                error("Timeout waiting for SonarQube results")
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            containerLog "kubedock"
            junit '**/build/test-results/test/*.xml'
        }
    }
}

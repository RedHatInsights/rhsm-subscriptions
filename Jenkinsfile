pipeline {
    options { buildDiscarder(logRotator(numToKeepStr: '50')) }
    environment {
        SPRING_ACTIVE_PROFILE = "test"
    }
    agent {
        kubernetes {
            label 'swatch-17' // this value + unique identifier becomes the pod name
            idleMinutes 5  // how long the pod will live after no jobs have run on it
            containerTemplate {
                name 'openjdk17'
                image 'registry.access.redhat.com/ubi9/openjdk-17-runtime'
                command 'sleep'
                args '99d'
                resourceRequestCpu '2'
                resourceLimitCpu '6'
                resourceRequestMemory '2Gi'
                resourceLimitMemory '6Gi'
            }

            defaultContainer 'openjdk17'
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
                echo "The ci value is ${env.CI}"
                // Integration tests require Docker environment which is not supported by our Jenkins environment yet
                // The actual problem is that the podman socket is not listening.
                // We need to run "systemctl --user enable podman.socket --now" when spawning the node.
                sh "./gradlew --no-daemon build -DexcludeTags=integration"
            }
        }

        stage('Upload PR to SonarQube') {
            when {
                changeRequest()
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonar -x test -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
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
                    sh "./gradlew --no-daemon sonarqube -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.branch.name=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
                }
            }
        }
        stage('SonarQube Quality Gate') {
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    echo "SonarQube scan results will be visible at: ${SONAR_HOST_URL}/summary/new_code?id=rhsm-subscriptions${CHANGE_ID ? '&pullRequest=' + CHANGE_ID : ''}"
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
            junit '**/build/test-results/test/*.xml'
        }
    }
}

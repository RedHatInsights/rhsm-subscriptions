pipeline {
    options { buildDiscarder(logRotator(numToKeepStr: '50')) }
    environment {
        SPRING_ACTIVE_PROFILE = "test"
    }
    agent {
        kubernetes {
            podRetention never()
            defaultContainer 'openjdk17'
            yaml '''
              apiVersion: v1
              kind: Pod
              spec:
                containers:
                - name: openjdk17
                  image: registry.access.redhat.com/ubi9/openjdk-17
                  command: [sleep]
                  args: [99d]
                  resources:
                          limits:
                                  cpu: 6
                                  memory: 6Gi
                          requests:
                                  cpu: 6
                                  memory: 6Gi
                - name: database
                  image: quay.io/cloudservices/postgresql-rds:12-1
                  env:
                    - name: POSTGRESQL_USER
                      value: rhsm-subscriptions
                    - name: POSTGRESQL_PASSWORD
                      value: rhsm-subscriptions
                    - name: POSTGRESQL_DATABASE
                      value: rhsm-subscriptions
                  resources:
                          limits:
                                  cpu: 2
                                  memory: 2Gi
                          requests:
                                  cpu: 1
                                  memory: 1Gi
              '''
        }
    }
    stages {
        stage('Verify PR ok to test') {
            when {
                beforeInput true
                expression { env.CHANGE_FORK }
            }
            steps {
                input 'ok to test?'
            }
        }
        stage('Build/Test/Lint') {
            steps {
                // The build task includes check, test, and assemble.  Linting happens during the check
                // task and uses the spotless gradle plugin.
                sh "./gradlew --no-daemon build"
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
                    echo "SonarQube scan results will be visible at: ${SONAR_HOST_URL}/dashboard?id=rhsm-subscriptions"
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

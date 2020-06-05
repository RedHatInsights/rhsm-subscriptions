pipeline {
    options { buildDiscarder(logRotator(numToKeepStr: '50')) }
    agent {
        label 'rhsm'
    }
    stages {
        stage('Clean') {
            steps {
                sh './gradlew --no-daemon clean'
            }
        }
        stage('Build') {
            steps {
                sh './gradlew --no-daemon assemble'
            }
        }
        stage('Unit tests') {
            steps {
                sh './gradlew --no-daemon test'
            }
            post {
                always {
                    junit 'build/test-results/**/*.xml'
                }
            }
        }
        stage('Checkstyle') {
            steps {
                sh './gradlew --no-daemon checkstyleMain checkstyleTest'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/reports/checkstyle/*.html'
                }
            }
        }
        stage('Upload PR to SonarQube') {
            when {
                changeRequest()
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
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
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.branch.name=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
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
                        }
                        catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
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
}

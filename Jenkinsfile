pipeline {
    agent {
        kubernetes {
            label 'bananas'
            // all your pods will be named with this prefix, followed by a unique id
            idleMinutes 5  // how long the pod will live after no jobs have run on it
            containerTemplate {
                name 'openjdk11'
                image 'registry.access.redhat.com/ubi8/openjdk-11'
                command 'sleep'
                args '99d'
                resourceRequestCpu '4'
                resourceLimitCpu '6'
                resourceRequestMemory '4Gi'
                resourceLimitMemory '6Gi'
            }

            defaultContainer 'openjdk11'
            // define a default container if more than a few stages use it, will default to jnlp container
        }
    }
    stages {
        stage('Test Java Version') {
            steps {  // no container directive is needed as the maven container is the default
                sh "java -version"
            }
        }
        stage('Clean') {
            steps {
                sh "./gradlew --no-daemon clean"
            }
        }
        stage('Build') {
            steps {
                sh "./gradlew --no-daemon build"
            }
        }

        stage('Spotless') {
            steps {
                sh "./gradlew --no-daemon spotlessCheck"
            }
        }

        stage('Upload PR to SonarQube') {
            when {
                changeRequest()
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonarqube -Duser.home=/tmp -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.organization=rhsm -Dsonar.projectKey=rhsm-subscriptions"
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

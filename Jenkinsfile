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
        }
        stage('Checkstyle') {
            steps {
                sh './gradlew --no-daemon checkstyleMain checkstyleTest'
            }
        }
        stage('Upload PR to SonarQube') {
            when {
                changeRequest()
            }
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.organization=rhsm -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME}"
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
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.organization=rhsm -Dsonar.branch.name=${BRANCH_NAME}"
                }
            }
        }
        stage('SonarQube Quality Gate') {
            steps {
                withSonarQubeEnv('sonarcloud.io') {
                    echo "SonarQube scan results will be visible at: ${SONAR_HOST_URL}/dashboard?id=org.candlepin%3Arhsm-conduit"
                }
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/reports/checkstyle/*.html'
            junit 'build/test-results/**/*.xml'
        }
    }
}

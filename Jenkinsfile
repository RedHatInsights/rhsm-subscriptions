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
        stage('Upload to SonarQube') {
            steps {
                withSonarQubeEnv('Sonar') {
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.projectVersion=${env.GIT_BRANCH}"
                }
            }
        }
        stage('SonarQube Quality Gate') {
            steps {
                withSonarQubeEnv('Sonar') {
                    echo "SonarQube scan results will be visible at: ${SONAR_HOST_URL}/dashboard?id=org.candlepin%3Arhsm-subscriptions"
                    echo 'NOTE: only latest scan results are available. If you need to see previous results again, rerun the "Upload to SonarQube" stage.'
                }
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }
}

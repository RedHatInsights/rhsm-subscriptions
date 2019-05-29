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
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.organization=rhsm -Dsonar.pullrequest.key=${CHANGE_ID} -Dsonar.pullrequest.base=${CHANGE_TARGET} -Dsonar.pullrequest.branch=${BRANCH_NAME} -Dsonar.pullrequest.provider=github -Dsonar.pullrequest.github.repository=RedHatInsights/rhsm-conduit"
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
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/reports/checkstyle/*.html'
            junit 'build/test-results/**/*.xml'
        }
    }
}

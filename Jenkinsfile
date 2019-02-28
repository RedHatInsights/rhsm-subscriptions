pipeline {
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
        stage('Upload to SonarQube') {
            steps {
                withSonarQubeEnv('Sonar') {
                    sh "./gradlew --no-daemon sonarqube -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN} -Dsonar.projectVersion=${env.GIT_BRANCH}"
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

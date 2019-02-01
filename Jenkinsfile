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
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/reports/checkstyle/*.html'
            junit 'build/test-results/**/*.xml'
        }
    }
}

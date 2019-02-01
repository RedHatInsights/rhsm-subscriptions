pipeline {
    agent {
        label 'rhsm'
    }
    stages {
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
    }

    post {
        always {
            junit 'build/test-results/**/*.xml'
        }
    }
}

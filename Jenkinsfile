pipeline {
    agent {
        label 'rhsm'
    }
    stages {
        stage('Build') {
            steps {
                sh './gradlew --no-daemon assemble -PgradleLint.alwaysRun=false'
            }
        }
        stage('Unit tests') {
            steps {
                sh './gradlew --no-daemon test -PgradleLint.alwaysRun=false'
            }
        }
        stage('Checkstyle') {
            steps {
                sh './gradlew --no-daemon generateGradleLintReport'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/reports/gradleLint/*.html'
            junit 'build/test-results/**/*.xml'
        }
    }
}

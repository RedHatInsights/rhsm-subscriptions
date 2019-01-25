pipeline {
    agent {
        label 'rhsm'
    }
    stages {
        stage('Build') {
            steps {
                sh './gradlew assemble -PgradleLint.alwaysRun=false'
            }
        }
        stage('Unit tests') {
            steps {
                sh './gradlew test -PgradleLint.alwaysRun=false'
            }
        }
        stage('Checkstyle') {
            steps {
                sh './gradlew generateGradleLintReport'
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

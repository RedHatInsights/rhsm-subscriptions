def buildNumber = env.BUILD_NUMBER as int
if (buildNumber > 1) milestone(buildNumber - 1)
milestone(buildNumber)

pipeline {
    options { buildDiscarder(logRotator(numToKeepStr: '50')) }
    environment {
        SPRING_ACTIVE_PROFILE = "test"
    }
    agent {
        kubernetes {
            label 'swatch-17-kubedock-2023-12-06' // this value + unique identifier becomes the pod name
            idleMinutes 5  // how long the pod will live after no jobs have run on it
            defaultContainer 'openjdk17'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: jnlp
      env:
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx2048m"
        - name: JENKINS_JAVA_OPTIONS
          value: "-Xms512m -Xmx2048m"
    - name: kubedock
      image: quay.io/cloudservices/kubedock:latest
      imagePullPolicy: Always
      tty: true
      args:
       - server
       - --port-forward
       # Verbosity level which is helpful to troubleshot issues when starting up containers
       - -v
       - 10
    - name: openjdk17
      image: registry.access.redhat.com/ubi9/openjdk-17-runtime
      command:
      - sleep
      tty: true
      args:
      - 99d
      resources:
        requests:
          memory: "2Gi"
          cpu: "2"
        limits:
          memory: "6Gi"
          cpu: "6"
      env:
      - name: DOCKER_HOST
        value: tcp://127.0.0.1:2475
"""
        }
    }
    stages {
        stage('Verify PR ok to test') {
            when {
                beforeInput true
                expression { env.CHANGE_FORK }
                not {
                  anyOf {
                    // Lindsey Burnett
                    changeRequest author: "lindseyburnett"
                    // Alex Wood
                    changeRequest author: "awood"
                    // Michael Stead
                    changeRequest author: "mstead"
                    // Kevin Flaherty
                    changeRequest author: "kflahert"
                    // Barnaby Court
                    changeRequest author: "barnabycourt"
                    // Jose Carvajal
                    changeRequest author: "Sgitario"
                    // Kartik Shah
                    changeRequest author: "kartikshahc"
                    // Vanessa Busch
                    changeRequest author: "vbusch"
                    // William Poteat
                    changeRequest author: "wottop"
                    // Ryan Himmelwright
                    changeRequest author: "himmAllRight"
                    // Trayvon McKnight
                    changeRequest author: "TrayvonMcKnight"
                    // Lisa Walker
                    changeRequest author: "liwalker-rh"
                    //Diego Maranhão 
                    changeRequest author: "diegomaranhao"
                    //Aurobinda Nayak
                    changeRequest author: "Aurobinda55"
                    //Marek Musil
                    changeRequest author: "mmusil"
                  }
               }
            }
            steps {
                input 'ok to test?'
            }
        }
        stage("I'm a happy little green checkmark") {
            steps {
                sh "echo 'hello, world!'"
            }
        }
    }
}

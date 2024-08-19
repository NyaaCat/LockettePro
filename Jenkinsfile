pipeline {
    agent any
    stages {
        stage('Build') {
            tools {
                jdk "jdk21"
            }
            steps {
                sh './gradlew publishMavenJavaPublicationToNyaaCatCILocalRepository'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            cleanWs()
        }
    }
}
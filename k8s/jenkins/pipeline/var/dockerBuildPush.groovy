def call(Map params = [:]) {
    pipeline {
        agent {
            kubernetes {
                yaml """
apiVersion: v1
kind: Pod
metadata:
  namespace: jenkins
  labels:
    role: dockerBuildPush
spec:
  containers:
  - name: docker
    image: docker:dind
    command:
    - cat
    tty: true
  securityContext:
    privileged: true
"""
            }
        }
        environment {
            DOCKERHUB_USER = params.dockerHubUser ?: 'dodo133'
            DOCKERHUB_PASS = params.dockerHubPass ?: 'kay24125@'
        }
        stages {
            stage('Build and Push') {
                steps {
                    script {
                        def dockerImage = docker.build("${DOCKERHUB_USER}/your-image-name")
                        docker.withRegistry('https://registry.hub.docker.com', 'dockerhub-credentials-id') {
                            dockerImage.push('latest')
                        }
                    }
                }
            }
        }
    }
}

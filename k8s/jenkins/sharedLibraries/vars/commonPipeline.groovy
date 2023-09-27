def call(Map config = [:]) {
    pipeline {
        agent {
            kubernetes {
                yaml '''
                    apiVersion: v1
                    kind: Pod
                    metadata:
                      labels:
                        role: kaniko
                    spec:
                      containers:
                      - name: kaniko
                        image: gcr.io/kaniko-project/executor:debug
                        command:
                        - /busybox/cat
                        imagePullPolicy: Always
                        tty: true
                        volumeMounts:
                        - name: jenkins-docker-cfg
                          mountPath: /kaniko/.docker
                      volumes:
                      - name: jenkins-docker-cfg
                        projected:
                          sources:
                          - secret:
                              name: dockerhub-secret
                              items:
                                - key: config.json
                                  path: config.json
                    '''
            }
        }
        environment {
            DOCKERHUB_USERNAME = "'${config.dockerhubUsername ?: 'dodo133'}'" // 주의: 추가적인 작은따옴표
            IMAGE_NAME = "'${config.imageName ?: 'tws-ai'}'" // 주의: 추가적인 작은따옴표
            GIT_COMMIT_SHORT = sh(script: 'echo $GIT_COMMIT | cut -c 1-7', returnStdout: true).trim()
        }
        stages {
            stage('extract docker tag') {
                steps {
                    script {
                        env.DOCKER_TAG = extractDockerTag()
                    }
                }
            }
            stage('Build and Push') {
                steps {
                    container('kaniko') {
                        script {
                            sh "ls /kaniko/.docker"
                            sh "echo '${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.DOCKER_TAG}'"
                        }
                    }
                }
            }
        }
    }
}

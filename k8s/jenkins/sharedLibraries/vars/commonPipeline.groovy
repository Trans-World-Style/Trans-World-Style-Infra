def call(Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (!config.imageName || !config.manifestRepo || !config.manifestDir || !config.manifestFile) {
        error("Mandatory config values are missing!")
    }

    pipeline {
        agent {
            kubernetes {
                yaml '''
                    apiVersion: v1
                    kind: Pod
                    metadata:
                      labels:
                        jenkins: slave
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
            IMAGE_NAME = "${config.imageName}"
            MANIFEST_REPO = "${config.manifestRepo}"
            MANIFEST_DIR = "${config.manifestDir}"
            MANIFEST_FILE = "${config.manifestFile}"
            GIT_COMMIT_SHORT = sh(script: 'echo $GIT_COMMIT | cut -c 1-12', returnStdout: true).trim()
        }
        stages {
            stage('extract docker tag') {
                steps {
                    script {
                        // set env.DOCKER_TAG, env.AUTHOR_NAME, env.AUTHOR_EMAIL
                        extractDockerTag()
                        env.IMAGE_TAG = env.DOCKER_TAG + '-' + GIT_COMMIT_SHORT
                    }
                }
            }

            stage('Before Build Stages') {
                when {
                    expression { return config.beforeBuildStages }
                }
                steps {
                    script {
                        config.beforeBuildStages.each { stageName, stageClosure ->
                            stageClosure.call()
                        }
                    }
                }
            }

            stage('Build and Push') {
                steps {
                    container('kaniko') {
                        script {
                            // env.DOCKERHUB_USERNAME = sh(script: 'kubectl get secrets -n jenkins dockerhub-secret -o jsonpath="{.data.username}" | base64 -d', returnStdout: true).trim()
                            env.DOCKERHUB_USERNAME = sh(script: '''kubectl get secrets -n jenkins dockerhub-secret -o jsonpath="{.data.username}" | base64 -d''', returnStdout: true).trim()

                            
                            // buildAndPush(env.DOCKERHUB_USERNAME, IMAGE_NAME, env.IMAGE_TAG)
                            sh "ls /kaniko/.docker"
                            sh "echo ${env.DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.IMAGE_TAG}"
                        }
                    }
                }
            }

            stage('Delivery To Github Manifest') {
                steps {
                    script {
                        sh "env"
                        withCredentials([usernamePassword(credentialsId: 'github-app-credentials',
                            usernameVariable: 'GITHUB_APP_ID', passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
                            dir("${AGENT_WORKDIR}") {
                                sh 'git clone https://$GITHUB_APP_ID:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO'
                                dir("${MANIFEST_REPO.split('/')[1].replace('.git', '')}") {
                                    sh """
                                        sed -i 's|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:.*|${env.DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.IMAGE_TAG}|' ${MANIFEST_DIR}/${MANIFEST_FILE}
                                        git config user.name '${env.AUTHOR_NAME}'
                                        git config user.email '${env.AUTHOR_EMAIL}'
                                        git add .
                                        git commit -m 'Update image tag to ${env.IMAGE_TAG}'
                                    """
                                    sh 'git push https://$GITHUB_APP_ID:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO $GIT_BRANCH'
                                }
                            }
                        }
                    }
                }
            }

            stage('After Build Stages') {
                when {
                    expression { return config.afterBuildStages }
                }
                steps {
                    script {
                        config.afterBuildStages.each { stageName, stageClosure ->
                            echo "Starting custom stage: ${stageName}"
                            stageClosure.call()
                            echo "Finished custom stage: ${stageName}"
                        }
                    }
                }
            }
        }
        // post {
        //     failure {
        //         script {
        //             echo "An error occurred. Keeping the pod running for debugging..."
        //             sleep 3600 // Pod will be kept running for 1 hour
        //         }
        //     }
        // }
    }
}

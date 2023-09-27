def call(Closure body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

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
            DOCKERHUB_USERNAME = "${config.dockerhubUsername ?: 'dodo133'}" // 주의: 추가적인 작은따옴표
            IMAGE_NAME = "${config.imageName ?: 'tws-ai'}" // 주의: 추가적인 작은따옴표
            MANIFEST_REPO = "${config.manifestRepo ?: 'Trans-World-Style/Trans-World-Style-Infra.git'}"
            MANIFEST_DIR = "${config.manifestDir ?: 'k8s/product/ai/cpu'}"
            MANIFEST_FILE = "${config.manifestFile ?: 'ai-deploy-cpu.yaml'}"
            GIT_COMMIT_SHORT = sh(script: 'echo $GIT_COMMIT | cut -c 1-12', returnStdout: true).trim()
        }
        stages {
            stage('extract docker tag') {
                steps {
                    script {
                        extractDockerTag() + '-' + GIT_COMMIT_SHORT
                        sh "echo dt: ${env.AUTHOR_NAME}"
                    }
                }
            }

            // stage('Before Build Stages') {
            //     when {
            //         expression { return config.beforeBuildStages }
            //     }
            //     steps {
            //         script {
            //             config.beforeBuildStages.each { stageName, stageClosure ->
            //                 stageClosure.call()
            //             }
            //         }
            //     }
            // }

            stage('Build and Push') {
                steps {
                    container('kaniko') {
                        script {
                            // buildAndPush(DOCKERHUB_USERNAME, IMAGE_NAME, env.DOCKER_TAG)
                            // sh "${env.docker_extracted}"
                            sh "ls /kaniko/.docker"
                            // sh "echo '${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.docker_extracted.dockerTag}'"
                        }
                    }
                }
            }

            stage('Delivery To Github Manifest') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'github-app-credentials',
                                          usernameVariable: 'GITHUB_APP',
                                          passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
                            dir("${AGENT_WORKDIR}") {
                                sh 'git clone https://$GITHUB_APP:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO'
                                dir("${MANIFEST_REPO.split('/')[1].replace('.git', '')}") {
                                    sh "env"
                                    def branch_name = "${env.GIT_BRANCH}"
                                    // sh """
                                    //     sed -i 's|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:.*|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.DOCKER_TAG}|' ${MANIFEST_DIR}/${MANIFEST_FILE}
                                    //     git config user.name "${env.docker_extracted.authorName}"
                                    //     git config user.email "${env.docker_extracted.authorEmail}"
                                    //     git add .
                                    //     git commit -m "Update image tag to ${env.docker_extracted.dockerTag}"
                                    // """
                                    sh 'git push https://$GITHUB_APP:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO $branch_name'
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
                            stageClosure.call()
                        }
                    }
                }
            }
        }
        post {
            failure {
                script {
                    echo "An error occurred. Keeping the pod running for debugging..."
                    sleep 3600 // Pod will be kept running for 1 hour
                }
            }
        }
    }
}

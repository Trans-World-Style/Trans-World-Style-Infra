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
                      - name: git
                        image: alpine/git
                        command:
                        - cat
                        tty: true
                        volumeMounts:
                        - name: github-credentials
                          mountPath: /root/.git-credentials
                      volumes:
                      - name: jenkins-docker-cfg
                        projected:
                          sources:
                          - secret:
                              name: dockerhub-secret
                              items:
                                - key: config.json
                                  path: config.json
                      - name: github-credentials
                        secret:
                          secretName: github-secret
                    '''
            }
        }
        environment {
            DOCKERHUB_USERNAME = "'${config.dockerhubUsername ?: 'dodo133'}'" // 주의: 추가적인 작은따옴표
            IMAGE_NAME = "'${config.imageName ?: 'tws-ai'}'" // 주의: 추가적인 작은따옴표
            MANIFEST_REPO = "'${config.manifestRepo ?: 'Trans-World-Style/Trans-World-Style-Infra.git'}'"
            MANIFEST_DIR = "'${config.manifestDir ?: 'Trans-World-Style-Infra/k8s/product/ai/cpu'}'"
            MANIFEST_FILE = "'${config.manifestFile ?: 'ai-deploy-cpu.yaml'}'"
            GIT_COMMIT_SHORT = sh(script: 'echo $GIT_COMMIT | cut -c 1-12', returnStdout: true).trim()
        }
        stages {
            stage('extract docker tag') {
                steps {
                    script {
                        env.DOCKER_TAG = extractDockerTag() + '-' + GIT_COMMIT_SHORT
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
                            // buildAndPush(DOCKERHUB_USERNAME, IMAGE_NAME, env.DOCKER_TAG)
                            sh "ls /kaniko/.docker"
                            sh "echo '${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.DOCKER_TAG}'"
                        }
                    }
                }
            }

            stage('Delivery To Github Manifest') {
                steps {
                    container('git') {
                        script {
                            sh """
                            git config credential.helper 'store --file=/root/.git-credentials'
                            git clone https://github.com/${MANIFEST_REPO}
                            sed -i 's|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:.*|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.DOCKER_TAG}|' ${MANIFEST_DIR}/${MANIFEST_FILE}
                            """
                            dir(MANIFEST_DIR) {
                                sh """
                                git config user.name "${env.GIT_AUTHOR_NAME}"
                                git config user.email "${env.GIT_AUTHOR_EMAIL}"
                                git add .
                                git commit -m "Update image tag to ${env.DOCKER_TAG} from ${env.GIT_COMMIT}"
                                git push origin ${env.GIT_BRANCH.replace('origin/', '')}
                                """
                            }
                            // withCredentials([usernamePassword(credentialsId: 'github-cridentials', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                            //     sh "git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${MANIFEST_REPO}"
                            //     sh """
                            //     sed -i 's|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:.*|${DOCKERHUB_USERNAME}/${IMAGE_NAME}:${env.DOCKER_TAG}|' ${MANIFEST_DIR}/${MANIFEST_FILE}
                            //     """
                            //     dir(MANIFEST_DIR) {
                            //         sh """
                            //         git config user.name "${env.GIT_AUTHOR_NAME}"
                            //         git config user.email "${env.GIT_AUTHOR_EMAIL}"
                            //         git add .
                            //         git commit -m "Update image tag to ${env.DOCKER_TAG} from ${env.GIT_COMMIT}"
                            //         git push origin ${env.GIT_BRANCH.replace('origin/', '')}  // GIT_BRANCH의 'origin/' 접두사를 제거
                            //         """
                            //     }
                            //     // dir(MANIFEST_DIR) {
                            //     //     sh """
                            //     //     git config user.name "DW-K"
                            //     //     git config user.email "pch145@naver.com"
                            //     //     git add .
                            //     //     git commit -m "Update image tag to ${env.DOCKER_TAG}"
                            //     //     git push origin main
                            //     //     """
                            //     // }
                            // }
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
    }
}

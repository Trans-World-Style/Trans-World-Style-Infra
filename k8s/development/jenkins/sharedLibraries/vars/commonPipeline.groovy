def call(Closure body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (!config.imageName || !config.manifestRepo || !config.manifestDir || !config.manifestFile || !config.manifestBranch) {
    error("Mandatory config values are missing!")
  }
  def useConfigMap = true
  if (!config.CONFIG_MAP_NAME || !config.CONFIG_MAP_MOUNT_PATH || !config.CONFIG_FILE_NAME) {
    echo 'config map not used'
    useConfigMap = false
  }

  def useSecret = true
  if (!config.SECRET_NAME) {
    echo 'secret not used'
    useSecret = false
  }

  def volumeMounts = []
  def volumes = []

  if (useConfigMap) {
      volumeMounts << '''
            - name: configmap-volume
              mountPath: /etc/config
      '''
      volumes << '''
          - name: configmap-volume
            configMap:
              name: ''' + config.CONFIG_MAP_NAME + '''
      '''
  }

  if (useSecret) {
      volumeMounts << '''
            - name: secret-volume
              mountPath: /etc/secret
      '''
      volumes << '''
          - name: secret-volume
            secret:
              secretName: ''' + config.SECRET_NAME + '''
      '''
  }

  def yamlString = """
        apiVersion: v1
        kind: Pod
        metadata:
          namespace: prod
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
            ${volumeMounts ? 'volumeMounts:\n' + volumeMounts.join('\n') : ''}
          ${volumes ? 'volumes:\n' + volumes.join('\n') : ''}
  """

  // def yamlString = '''
  //     apiVersion: v1
  //     kind: Pod
  //     metadata:
  //       namespace: prod
  //       labels:
  //         jenkins: slave
  //     spec:
  //       containers:
  //       - name: kaniko
  //         image: gcr.io/kaniko-project/executor:debug
  //         command:
  //         - /busybox/cat
  //         imagePullPolicy: Always
  //         tty: true
  // '''

  // if (useConfigMap) {
  //   yamlString += '''
  //         volumeMounts:
  //         - name: configmap-volume
  //           mountPath: /config
  //       volumes:
  //       - name: configmap-volume
  //         configMap:
  //           name: ''' + config.CONFIG_MAP_NAME + '''
  //   '''
  // }

  // if (useConfigMap || useSecret) {
  //   yamlString += '''
  //         volumeMounts:
  //   '''
  // }
  // if (useConfigMap) {
  //   yamlString += '''
  //         - name: configmap-volume
  //           mountPath: /config
  //   '''
  // }

  // if (useSecret) {
  //   yamlString += '''
  //         - name: secret-volume
  //           mountPath: /config
  //   '''
  // }

  // if (useConfigMap || useSecret) {
  //   yamlString += '''
  //       volumes:    
  //   '''
  // }

  // if (useConfigMap) {
  //   yamlString += '''
  //       - name: configmap-volume
  //         configMap:
  //           name: ''' + config.CONFIG_MAP_NAME + '''
  //   '''
  // }

  // if (useSecret) {
  //   yamlString += '''
  //       - name: secret-volume
  //         secret:
  //           secretName: ''' + config.SECRET_NAME + '''
  //   '''
  // }

  pipeline {
    agent {
      kubernetes {
        yaml yamlString
      }
    }
    environment {
      IMAGE_NAME = "${config.imageName}"
      MANIFEST_REPO = "${config.manifestRepo}"
      MANIFEST_DIR = "${config.manifestDir}"
      MANIFEST_FILE = "${config.manifestFile}"
      MANIFEST_BRANCH = "${config.manifestBranch}"
      GIT_COMMIT_SHORT = sh(script: 'echo $GIT_COMMIT | cut -c 1-12', returnStdout: true).trim()
    }
    stages {
      stage('extract docker tag') {
        steps {
          script {
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
              withCredentials([usernamePassword(credentialsId: 'dockerhub-secret',
                                usernameVariable: 'DOCKERHUB_ID', passwordVariable: 'DOCKERHUB_TOKEN')]) {
                // echo "{\"auths\":{\"https://index.docker.io/v1/\":{\"auth\":\"$(echo -n "$DOCKERHUB_ID:$DOCKERHUB_TOKEN" | base64)\"}}}" > /kaniko/.docker/config.json
                
                if(useConfigMap || useSecret) {
                  // def configPath = "`pwd`/${config.CONFIG_MAP_MOUNT_PATH}"
                  // touch "${configPath}/config" "${configPath}/secret"

                  // if(useConfigMap) {
                  //   sh "cp /etc/config/config ${configPath}/${config.CONFIG_FILE_NAME}"
                  // }
                  // if(useSecret) {
                  //   sh "cp /etc/secret/secret ${configPath}/${config.SECRET_FILE_NAME}"
                  // }

                  // cat "${configPath}/config" "${configPath}/secret" > "${configPath}/${config.CONFIG_FILE_NAME}"
                  def configPath = "`pwd`/${config.CONFIG_MAP_MOUNT_PATH}"

                  sh """
                    touch ${configPath}/config ${configPath}/secret
                    
                    if [ "${useConfigMap}" == "true" ]; then
                        cp /etc/config/config ${configPath}/
                    fi
                    if [ "${useSecret}" == "true" ]; then
                        cp /etc/secret/secret ${configPath}/
                    fi

                    cat ${configPath}/config ${configPath}/secret > ${configPath}/${config.CONFIG_FILE_NAME}
                  """
                }
                sh '''
                  ENCODED_AUTH=$(echo -n "$DOCKERHUB_ID:$DOCKERHUB_TOKEN" | base64)
                  echo '{\"auths\":{\"https://index.docker.io/v1/\":{\"auth\":\"'$ENCODED_AUTH'\"}}}' > /kaniko/.docker/config.json
                  /kaniko/executor --context `pwd` --destination $DOCKERHUB_ID/$IMAGE_NAME:$IMAGE_TAG --cache
                '''
              }
            }
          }
        }
      }

      stage('Delivery To Github Manifest') {
        steps {
          script {
            withCredentials([usernamePassword(credentialsId: 'github-app-credentials',
                            usernameVariable: 'GITHUB_APP_ID', passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
              withCredentials([usernamePassword(credentialsId: 'dockerhub-secret',
                              usernameVariable: 'DOCKERHUB_ID', passwordVariable: 'DOCKERHUB_TOKEN')]) {
                dir("${AGENT_WORKDIR}") {
                  sh 'git clone https://$GITHUB_APP_ID:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO'
                  dir("${MANIFEST_REPO.split('/')[1].replace('.git', '')}") {
                    sh """
                      git checkout ${MANIFEST_BRANCH}
                      sed -i 's|${DOCKERHUB_ID}/${IMAGE_NAME}:.*|${DOCKERHUB_ID}/${IMAGE_NAME}:${IMAGE_TAG}|' ${MANIFEST_DIR}/${MANIFEST_FILE}
                      git config user.name '${AUTHOR_NAME}'
                      git config user.email '${AUTHOR_EMAIL}'
                      git add .
                      git commit -m 'Update image tag to ${IMAGE_TAG}'
                    """
                    sh 'git push https://$GITHUB_APP_ID:$GITHUB_ACCESS_TOKEN@github.com/$MANIFEST_REPO $MANIFEST_BRANCH'
                  }
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
    //   always {
    //     script {
    //       echo "Keeping the pod running for debugging..."
    //       sleep 3600
    //     }
    //   }
    // }

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
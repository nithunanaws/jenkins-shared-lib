#!/usr/bin/env groovy

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        parameters {
            string(name: 'DEPLOYMENT_TYPE', defaultValue: 'FUNCTIONAL', description: 'Deployment Type(FUNCTIONAL or RELEASE)', trim: true)
        }

		environment {
            DEPLOYMENT_TYPE = "${env.DEPLOYMENT_TYPE ?: 'FUNCTIONAL'}"
        }
		
        stages {
            stage('Preparation') {
                steps {
                    script {
                        env.LAST_STABLE_BUILD_VERSION = main.getLastStableBuildVersion(currentBuild.getPreviousBuild())
                    }
                }
            }
            stage('Functional') {
                when {
                    expression {
                        return env.DEPLOYMENT_TYPE == 'FUNCTIONAL'
                    }
                }
                steps {
                    script {
                        main.deploy("${env.JOB_NAME}-Functional-Deployment")
                    }
                }
            }
            stage('Release') {
                when {
                    expression {
                        return env.DEPLOYMENT_TYPE == 'RELEASE'
                    }
                }
                steps {
                    script {
                        main.deploy("${env.JOB_NAME}-Release-Deployment")
                    }
                }
            }
            stage('Rollback') {
                when {
                    expression {
                        def isRollbackDisabled = (pipelineParams.rollbackDisabled == null || pipelineParams.rollbackDisabled == false)
                        def isDeployFailed = (env.DEPLOY_STATUS  && env.DEPLOY_STATUS == 'FAILED')
                        def isStageFailed = (env.FAILED_STAGE != null && !env.FAILED_STAGE.contains('Build'))
                        return isRollbackDisabled && isDeployFailed && isStageFailed
                    }
                }
                steps {
                    script {
                        if(env.DEPLOYMENT_TYPE == 'FUNCTIONAL') {
                            main.rollback("${env.JOB_NAME}-Functional-Rollback")
                        }  
                        if(env.DEPLOYMENT_TYPE == 'RELEASE') {
                            main.rollback("${env.JOB_NAME}-Release-Rollback")                        
                        }
                    }
                }
            }        
        }
        post {
            always {
                cleanWs()
            }
            success {
                script {
                    currentBuild.description = "${env.DEPLOYMENT_TYPE} ${env.VERSION}"
                }
            }
			failure {
                script {
                    echo "${env.DEPLOYMENT_TYPE} deployment failed"
                }
            }
        }
    }
} 
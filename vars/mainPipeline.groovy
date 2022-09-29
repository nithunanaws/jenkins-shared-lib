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
                        env.LAST_STABLE_BUILD_VERSION = main.getLastStableBuildVersion(currentBuild.getPreviousBuild(), env.DEPLOYMENT_TYPE)                        
                        if(env.DEPLOYMENT_TYPE == 'RELEASE') {
                            env.LAST_STABLE_FUNC_BUILD_VERSION = main.getLastStableBuildVersion(currentBuild.getPreviousBuild(), 'FUNCTIONAL')
                        } else {
                            env.LAST_STABLE_FUNC_BUILD_VERSION = env.LAST_STABLE_BUILD_VERSION
                        }
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
                        if(env.DEPLOY_STATUS  && env.DEPLOY_STATUS == 'SUCCESS') {
                            main.rollback("${env.JOB_NAME}-Release-Rollback", false)
                        }
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
                            main.rollback("${env.JOB_NAME}-Functional-Rollback", true)
                        }  
                        if(env.DEPLOYMENT_TYPE == 'RELEASE') {
                            main.rollback("${env.JOB_NAME}-Release-Rollback", true)
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
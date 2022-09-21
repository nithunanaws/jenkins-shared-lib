#!/usr/bin/env groovy

def valueOrDefault(val, defaultVal) {
    val != null ? val : defaultVal
}

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

		environment {
            deploymentType = valueOrDefault(pipelineParams.deploymentType, 'FUNCTIONAL')
        }
		
        stages {
            stage('Preparation') {
                steps {
                    script {						
                        automation.deployApp(env.deploymentType, pipelineParams, env.JOB_NAME)
                    }
                }
            }
            stage('Rollback') {
                when {
                    expression {
                        return (pipelineParams.rollbackDisabled == null || pipelineParams.rollbackDisabled == false) && (env.IS_ANY_STAGE_FAILED != null  && env.IS_ANY_STAGE_FAILED == 'true')
                    }
                }
                steps {
                    script {
                        automation.rollbackApp(env.deploymentType)
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
                    currentBuild.description = "${env.deploymentType} ${env.VERSION}"
                }
            }
			failure {
                script {
                    if(env.ROLL_BACK && env.ROLL_BACK == 'true') {                        
                        echo "Deployment failed and rolled back to last successfull version: ${env.LAST_SUCCESS_BUILD_VERSION}"
                    } else if(env.ROLL_BACK || env.ROLL_BACK == 'false' || pipelineParams.rollbackDisabled == true) {
                        echo "Deployment failed and Rollback skipped"
                    } else {
                        echo "Both Deployment and Rollback failed"
                    }
                }
            }
        }
    }
}
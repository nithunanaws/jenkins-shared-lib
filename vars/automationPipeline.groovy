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
		
	def lastSuccessBuildVersion

    pipeline {
        agent any

		environment {
            DEPLOYMENT_TYPE = valueOrDefault(pipelineParams.deploymentType, 'FUNCTIONAL')
        }
		
        stages {
            stage('Deployment') {
                steps {
                    script {						
                        automation.deployApp(env.DEPLOYMENT_TYPE, pipelineParams, env.JOB_NAME)
                    }
                }
            }
            stage('Rollback') {
                when {
                    expression {
                        return (pipelineParams.rollbackDisabled == null || pipelineParams.rollbackDisabled == 'false') && (env.IS_ANY_STAGE_FAILED != null  && env.IS_ANY_STAGE_FAILED == 'true')
                    }
                }
                steps {
                    script {
                        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                            def failedEnv = automation.getFailedDeploymentEnv(env.DEPLOYMENT_TYPE)
                            if(failedEnv) {
                                lastSuccessBuildVersion = automation.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), env.DEPLOYMENT_TYPE)
                                def rollbackRun = build(
                                    job: "${env.JOB_NAME}-Rollback",
                                    parameters: [
                                            string(name: 'VERSION', value: lastSuccessBuildVersion),
                                            string(name: 'FAILED_ENV', value: failedEnv)                                            
                                    ]
                                )
                                if(rollbackRun != null && rollbackRun.getResult() == 'SUCCESS') {
                                    env.ROLL_BACK = 'true'                                    
                                }
                            } else {
                                env.ROLL_BACK = 'false'
                                Utils.markStageSkippedForConditional(env.STAGE_NAME)
                            }                            
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
                    if(env.ROLL_BACK && env.ROLL_BACK == 'true') {
                        echo "Deployment failed and rolled back to last successfull version: ${lastSuccessBuildVersion}"
                    } else if(env.ROLL_BACK && env.ROLL_BACK == 'false') {
                        echo "Deployment failed and Rollback skipped"
                    } else {
                        echo "Both Deployment and Rollback failed"
                    }
                }
            }
        }
    }
}
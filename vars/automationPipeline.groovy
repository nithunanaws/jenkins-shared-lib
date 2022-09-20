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
            deploymentType = valueOrDefault(pipelineParams.deploymentType, 'FUNCTIONAL')
			JOB_NAME = 'test'
        }
		
        stages {
            stage('Deployment Initiated') {
                steps {
                    script {
						lastSuccessBuildVersion = automation.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)
                        automation.deployApp(env.deploymentType, pipelineParams, env.JOB_NAME)
                    }
                }
            }
            stage('Rollback Initiated') {
                when {
                    expression {
                        return env.IS_ANY_STAGE_FAILED != null  && env.IS_ANY_STAGE_FAILED == 'true'
                    }
                }
                steps {
                    script {
                        pipelineParams.buildDisabled = false
                        pipelineParams.acceptanceDisabled = false
                        pipelineParams.regressionDisabled = false
                        env.IS_ANY_STAGE_FAILED = 'false'
                        env.ROLL_BACK = 'true'
                        env.VERSION = lastSuccessBuildVersion
                        automation.rollbackApp(env.deploymentType, pipelineParams, env.JOB_NAME)
                        currentBuild.result = 'FAILURE'
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
                        echo "Build failed and rolled back to last successfull version: ${lastSuccessBuildVersion}"
                    } else {
                        echo "Build failed"
                    }                    
                }
            }
        }
    }
}
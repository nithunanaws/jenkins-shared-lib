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
		
	def LAST_SUCCESS_BUILD_VERSION

    pipeline {
        agent any

		environment {            
            deploymentType = valueOrDefault(pipelineParams.deploymentType, 'FUNCTIONAL')
			JOB_NAME = 'test'
			PREVIOUS_STAGE_FAILED = 'false'
        }
		
        stages {
            stage('Deployment Initiated') {
                steps {
                    script {	
						LAST_SUCCESS_BUILD_VERSION = deploy.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)												
                        deploy.deployApp(env.deploymentType, pipelineParams, env.JOB_NAME)
                    }
                }
            }                        
        } 
		post {            
            success {
                script {
                    currentBuild.description = "${env.deploymentType} ${env.VERSION}"
                }
            }
			failure {
                script {
                    echo "Last Successful Build Version: ${LAST_SUCCESS_BUILD_VERSION}"
                }
            }
        }
    }
}
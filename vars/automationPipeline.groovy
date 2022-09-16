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
			FAILED_STAGE = ''
			LAST_SUCCESS_BUILD_VERSION = ''
        }
		
        stages {
            stage('Deployment Initiated') {
                steps {
                    script {	
						def lastSuccessBuildVersion = deploy.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)
						env.LAST_SUCCESS_BUILD_VERSION = lastSuccessBuildVersion
                        deploy.deployApp(env.deploymentType, pipelineParams, env.FAILED_STAGE)
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
                    echo "${env.LAST_SUCCESS_BUILD_VERSION}"
					echo "${env.FAILED_STAGE}"
                }
            }
        }
    }
}
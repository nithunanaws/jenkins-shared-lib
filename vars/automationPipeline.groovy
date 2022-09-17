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
        }
		
        stages {
            stage('Deployment Initiated') {
                steps {
                    script {	
						LAST_SUCCESS_BUILD_VERSION = deploy.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)												
                        deploy.deployApp(env.deploymentType, pipelineParams)
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
                    echo "Last Successfull Build Version: ${LAST_SUCCESS_BUILD_VERSION}"
					def failedStageName = deploy.getFailedStageName()
					echo "${failedStageName}"
                }
            }
        }
    }
}
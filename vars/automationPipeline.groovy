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
	
	def lastSuccessFullDeployment

    pipeline {
        agent any

		environment {            
            deploymentType = valueOrDefault(pipelineParams.deploymentType, 'FUNCTIONAL')            
        }
		
        stages {
            stage('Deployment Initiated') {
                steps {
                    script {	
						lastSuccessFullDeployment = getLastSuccessfullDeployment(currentBuild.getPreviousBuild(), deploymentType)
                        deploy.deployApp(env.deploymentType, pipelineParams, lastSuccessFullDeployment)
                    }
                }
            }                        
        } 
		post {            
            success {
                script {
                    currentBuild.description = "${env.deploymentType} ${env.VERSION} ${lastSuccessFullDeployment}"
                }
            }            
        }
    }
}
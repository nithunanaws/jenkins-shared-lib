#!/usr/bin/env groovy

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        stages {
            stage('Deployment Initiated') {
                steps {
                    script {
                        deploy.deployApp(pipelineParams)
                    }
                }
            }                        
        } 
		post {            
            success {
                script {
                    currentBuild.description = "${pipelineParams.deploymentType} ? : 'FUNCTIONAL'"                  
                }
            }            
        }
    }
}
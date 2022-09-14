#!/usr/bin/env groovy

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        environment {            
            deploymentType = "${pipelineParams.deploymentType}"
        }

        stages {
            stage('Deployment Initiated') {
                steps {
                    script {
                        deploy.deployApp(env.deploymentType,pipelineParams)
                    }
                }
            }                        
        } 
		post {            
            success {
                script {
                    currentBuild.description = env.deploymentType                  
                }
            }            
        }
    }
}
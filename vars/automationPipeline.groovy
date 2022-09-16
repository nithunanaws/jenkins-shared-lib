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
			GIT_COMMIT_REV=''
        }
		
        stages {
            stage('Deployment Initiated') {
                steps {
                    script {
						GIT_COMMIT_REV = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
						def lastSuccessBuildVersion= deploy.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)						
                        deploy.deployApp(env.deploymentType, pipelineParams)
                    }
                }
            }                        
        } 
		post {            
            success {
                script {
                    currentBuild.description = "${env.deploymentType} ${env.GIT_COMMIT_REV}"
                }
            }            
        }
    }
}
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
						lastSuccessBuildVersion = deploy.getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)
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
                    echo "Last Successful Build Version: ${lastSuccessBuildVersion}"
                }
            }
        }
    }
}
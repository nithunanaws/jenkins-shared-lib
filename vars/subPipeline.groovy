#!/usr/bin/env groovy

def getBaseJobName(def jobName) {
    def strs = jobName.split('-')
    return strs[0]
}

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        parameters {
            string(name: 'DEPLOYMENT_TYPE', description: 'Deployment Type', trim: true)
        }

		environment {
            DEPLOYMENT_TYPE = "${env.DEPLOYMENT_TYPE}"
        }
		
        stages {
            stage('Preparation') {
                steps {
                    script {						
                        sub.deploy(env.DEPLOYMENT_TYPE, pipelineParams, getBaseJobName(env.JOB_NAME))
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
                    echo "${env.DEPLOYMENT_TYPE} deployment failed"
                }
            }
        }
    }
}
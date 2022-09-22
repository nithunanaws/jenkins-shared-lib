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
            string(name: 'VERSION', description: 'Rollback Version', trim: true)
            string(name: 'FAILED_ENVIRONMENT', description: 'Failed Environment', trim: true)
            string(name: 'DEPLOYMENT_TYPE', description: 'Deployment Type', trim: true)
        }

		environment {
            VERSION = "${env.VERSION}"
            FAILED_ENVIRONMENT = "${env.FAILED_ENVIRONMENT}"
            DEPLOYMENT_TYPE = "${env.DEPLOYMENT_TYPE}"
        }
		
        stages {
            stage('Preparation') { 
                steps {
                    script { 
                        env.IS_ANY_STAGE_FAILED = 'false'
                        automation.doRollback(env.DEPLOYMENT_TYPE, pipelineParams, getBaseJobName(env.JOB_NAME))
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
                    currentBuild.description = "${env.deploymentType}_ROLLBACK ${env.VERSION}"
                }
            }
			failure {
                script {
                    echo "Rollback to version ${env.VERSION} failed"
                }
            }
        }
    }
}
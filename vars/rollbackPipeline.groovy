#!/usr/bin/env groovy

def valueOrDefault(val, defaultVal) {
    val != null ? val : defaultVal
}

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
        }

		environment {
            VERSION = "${env.VERSION}"
            FAILED_ENVIRONMENT = "${env.FAILED_ENVIRONMENT}"
            DEPLOYMENT_TYPE = valueOrDefault(pipelineParams.deploymentType, 'FUNCTIONAL')
        }
		
        stages {
            stage('Rollback Preparation') { 
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
                    currentBuild.description = "${env.DEPLOYMENT_TYPE}_ROLLBACK ${env.VERSION}"
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
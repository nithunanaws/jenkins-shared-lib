#!/usr/bin/env groovy

def deploy(def jobName) {
    echo "${env.DEPLOYMENT_TYPE} deployment started"
    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
        deployRun = build(
                job: jobName,
                parameters: [
                        string(name: 'DEPLOYMENT_TYPE', value: env.DEPLOYMENT_TYPE)
                ]
        )
        if(deployRun != null && deployRun.getResult() == 'SUCCESS') {
            env.VERSION = deployRun.buildVariables.VERSION
            deployStatus = 'SUCCESS'
        }
        if (deployRun != null && deployRun.getResult() == 'FAILURE') {
            deployStatus = 'FAILED'
            failedEnv = deployRun.buildVariables.FAILED_ENV
            failedStageName = deployRun.buildVariables.FAILED_STAGE_NAME
            error("${env.DEPLOYMENT_TYPE} Deployment Failed")
        }
    }
}

def rollback() {
    echo "Rollback to version: ${lastStableBuildVersion} started"
    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
        rollbackRun = build(
                job: jobName,
                parameters: [
                        string(name: 'DEPLOYMENT_TYPE', value: env.DEPLOYMENT_TYPE),
                        string(name: 'VERSION', value: lastStableBuildVersion),
                        string(name: 'FAILED_ENV', value: failedEnv)
                ]
        )
        if(rollbackRun != null && rollbackRun.getResult() == 'FAILURE') {  
            error("Rollback to version: ${lastStableBuildVersion} failed")           
        }
    }
    if(rollbackRun != null && rollbackRun.getResult() == 'SUCCESS') {
        echo "Rollback to version: ${lastStableBuildVersion} succeeded"
        currentBuild.result = 'FAILURE'                            
    }
}

def getLastStableBuildVersion(def build, def deploymentType) {
	def successBuilds = []
	def successBuildsDesc = []
	populateSuccessBuilds(build, successBuilds)
	for(eachBuild in successBuilds) {
		if(eachBuild.getDescription().contains(deploymentType)) {
			successBuildsDesc.add(eachBuild.getDescription())
		}
	}
	def buildVersion
	if(successBuildsDesc) {
		def lastSuccessBuildDesc = successBuildsDesc.first()
		def descWords = lastSuccessBuildDesc.split(" ")
		if(descWords) {
			if(descWords.size() == 2) {
				buildVersion = descWords[1]
			}
		}
	}
	return buildVersion
}

def populateSuccessBuilds(def build, def successBuilds) {
	def allBuilds = []
	populateAllBuilds(build, allBuilds)
	for(eachBuild in allBuilds) {
		if(eachBuild != null && eachBuild.getResult() == 'SUCCESS') {
			successBuilds.add(eachBuild)
		}
	}
}

def populateAllBuilds(def build, def allBuilds) {	
	if(build != null) {
		allBuilds.add(build)
		populateAllBuilds(build.getPreviousBuild(), allBuilds)
	}
}

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    def deployRun
    def rollbackRun
    def deployStatus
    def lastStableBuildVersion
    def failedEnv
    def failedStageName

    pipeline {
        agent any

        parameters {
            string(name: 'DEPLOYMENT_TYPE', defaultValue: 'FUNCTIONAL', description: 'Deployment Type (FUNCTIONAL or RELEASE)', trim: true)
        }

		environment {
            DEPLOYMENT_TYPE = "${env.DEPLOYMENT_TYPE} ?: 'FUNCTIONAL'"
        }
		
        stages {
            stage('Preparation') {
                steps {
                    script {
                        lastStableBuildVersion = getLastStableBuildVersion(currentBuild.getPreviousBuild(), env.DEPLOYMENT_TYPE)
                        echo "Last Stable Build Version: ${lastStableBuildVersion}"
                    }
                }
            }
            stage('Functional') {
                when {
                    expression {
                        return env.DEPLOYMENT_TYPE == 'FUNCTIONAL'
                    }
                }
                steps {
                    script {                        
                        deploy("${env.JOB_NAME}-Deployment")
                    }
                }
            }
            stage('Release') {
                when {
                    expression {
                        return env.DEPLOYMENT_TYPE == 'RELEASE'
                    }
                }
                steps {
                    script {                        
                        deploy("${env.JOB_NAME}-Deployment")
                    }
                }
            }                       
            stage('Rollback') {
                when {
                    expression {
                        def isRollbackDisabled = (pipelineParams.rollbackDisabled == null || pipelineParams.rollbackDisabled == false)
                        def isDeployFailed = (deployStatus != null  && deployStatus == 'FAILED')
                        return isRollbackDisabled && isDeployFailed && !failedStageName.contains('Build')
                    }
                }
                steps {
                    script {                        
                        rollback("${env.JOB_NAME}-Rollback")                        
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
                    echo "${env.DEPLOYMENT_TYPE} Deployment Failed"
                }
            }
        }
    }
} 
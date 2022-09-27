#!/usr/bin/env groovy

def runJob(def jobName, def parameters) {
    return build(job: jobName, parameters: parameters, propagate: false)
}	

def deploy(def jobName) {
    def deployRun
    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
        def parameters = [
                            string(name: 'DEPLOYMENT_TYPE', value: env.DEPLOYMENT_TYPE)
                    ]
        deployRun = runJob(jobName, parameters)
        if(deployRun != null && deployRun.getResult() == 'SUCCESS') {            
            env.VERSION = deployRun.buildVariables.VERSION
            deployStatus = 'SUCCESS'
            echo "${env.DEPLOYMENT_TYPE} deployment successful"
        }
        if (deployRun != null && deployRun.getResult() == 'FAILURE') {
            env.DEPLOY_STATUS = 'FAILED'
            env.FAILED_ENV = deployRun.buildVariables.FAILED_ENV            
            env.FAILED_STAGE = deployRun.buildVariables.FAILED_STAGE
            error("${env.DEPLOYMENT_TYPE} deployment failed")            
        }
    }
}

def rollback(def jobName) {    
    def rollbackRun
    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
        def parameters = [
                            string(name: 'DEPLOYMENT_TYPE', value: env.DEPLOYMENT_TYPE),
                            string(name: 'VERSION', value: env.LAST_STABLE_BUILD_VERSION),
                            string(name: 'FAILED_ENV', value: env.FAILED_ENV)
                    ]
        rollbackRun = runJob(jobName, parameters)        
        if(rollbackRun != null && rollbackRun.getResult() == 'SUCCESS') {
            echo "Rollback to version: ${env.LAST_STABLE_BUILD_VERSION} successful"       
        }
        if(rollbackRun != null && rollbackRun.getResult() == 'FAILURE') {  
            error("Rollback to version: ${env.LAST_STABLE_BUILD_VERSION} failed")           
        }
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

    pipeline {
        agent any

        parameters {
            string(name: 'DEPLOYMENT_TYPE', defaultValue: 'FUNCTIONAL', description: 'Deployment Type (FUNCTIONAL or RELEASE)', trim: true)
        }

		environment {
            DEPLOYMENT_TYPE = "${env.DEPLOYMENT_TYPE ?: 'FUNCTIONAL'}"
        }
		
        stages {
            stage('Preparation') {
                steps {
                    script {
                        env.LAST_STABLE_BUILD_VERSION = getLastStableBuildVersion(currentBuild.getPreviousBuild(), env.DEPLOYMENT_TYPE)                        
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
                        deploy("${env.JOB_NAME}-Functional")
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
                        deploy("${env.JOB_NAME}-Release")
                    }
                }
            }                       
            stage('Rollback') {
                when {
                    expression {
                        def isRollbackDisabled = (pipelineParams.rollbackDisabled == null || pipelineParams.rollbackDisabled == false)                        
                        def isDeployFailed = (env.DEPLOY_STATUS  && env.DEPLOY_STATUS == 'FAILED')                        
                        def isStageFailed = (env.FAILED_STAGE != null && !env.FAILED_STAGE.contains('Build'))                        
                        return isRollbackDisabled && isDeployFailed && isStageFailed
                    }
                }
                steps {
                    script {
                        if(env.DEPLOYMENT_TYPE == 'FUNCTIONAL') {
                            rollback("${env.JOB_NAME}-Functional-Rollback")
                        }  
                        if(env.DEPLOYMENT_TYPE == 'RELEASE') {
                            rollback("${env.JOB_NAME}-Release-Rollback")
                        }                                             
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
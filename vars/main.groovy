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
                            string(name: 'FUNC_VERSION', value: env.LAST_STABLE_FUNC_BUILD_VERSION),
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
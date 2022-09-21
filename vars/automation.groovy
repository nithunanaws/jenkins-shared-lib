#!/usr/bin/env groovy
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def getDeploymentEnvironments(def deploymentType) {
    def map = [FUNCTIONAL:'INT,QAF',RELEASE:'INT,QAR,STG,PT,PROD']
    map[deploymentType] ?: 'INT,QAF'
}

def markStageAsSkipped(def stageName, def isStageDisabled) {
	if(isStageDisabled != null && isStageDisabled == true) {
		Utils.markStageSkippedForConditional(stageName)
	}
}

def runJob(def jobName, def isStageDisabled, def parameters) {
	def result
	if(isStageDisabled == null || isStageDisabled == false) {
		if(parameters) {
			result = build(job: jobName, parameters: parameters)
		} else {
			result = build(job: jobName)
		}		
	}
	return result
}

def runStage(def stageName, def jobName, def isStageDisabled, def parameters) {
	catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
		if(env.IS_ANY_STAGE_FAILED == 'true') {
			error("Force failing due to ${FAILED_STAGE_NAME} stage failure")
		}
		try {
			if(env.IS_ANY_STAGE_FAILED == 'false') {
				def jobResult = runJob(jobName, isStageDisabled, parameters)
				if(jobResult != null) {
					if(jobName.contains("Build")) {
						env.VERSION = jobResult.buildVariables.VERSION
					}
				}				
			}
		} catch(Exception ex) {			
			env.IS_ANY_STAGE_FAILED = 'true'
			env.FAILED_STAGE_NAME = stageName
			error("Failing due to ${jobName} failure")
		}
	}
}

def getLastSuccessBuildVersion(def build, def deploymentType) {
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

def deployApp(def deploymentType, def pipelineParams, def jobName) {
    def envs = getDeploymentEnvironments(deploymentType)
    def deployEnvs = envs.split(',')
    for(deployEnv in deployEnvs) {
        doDeploy(deployEnv, deploymentType, pipelineParams, jobName)
    }
}

def rollbackApp(def deploymentType, def pipelineParams, def jobName) {
	def envs = getDeploymentEnvironments(deploymentType)
	def deployEnvs = envs.split(',')
	def idx = deployEnvs.findIndexOf{ it ==  env.FAILED_ENV}
	def rollbackEnvs = deployEnvs.take(idx + 1)
	for(rollbackEnv in rollbackEnvs) {
		doDeploy(rollbackEnv, deploymentType, pipelineParams, jobName)
	}
}

def getFailedDeploymentEnv(def deploymentType) {	
	def envs = getDeploymentEnvironments(deploymentType)
	def deployEnvs = envs.split(',')
	def failedEnv
	for(deployEnv in deployEnvs) {
		if(env.FAILED_STAGE_NAME.contains(deployEnv)) {
			failedEnv = deployEnv
			break
		}
	}
	return failedEnv
}

def doDeploy(def deployEnv, def deploymentType, def pipelineParams, def jobName) {
    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {								
				env.IS_ANY_STAGE_FAILED = 'false'
				def parameters = [
                                	string(name: 'BRANCH', value: 'develop')
                            ]
				runStage(env.STAGE_NAME, "${jobName}-Build", pipelineParams.buildDisabled, parameters)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
			}
        }
    }    
    stage("${deployEnv}-Deploy") {
        script {			
			def parameters = [
                                string(name: 'VERSION', value: env.VERSION)
                        ]	
			runStage(env.STAGE_NAME, "${jobName}-Deploy", pipelineParams.deployDisabled, parameters)
			markStageAsSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {
            script {				
				def parameters = []
				runStage(env.STAGE_NAME, "${jobName}-Acceptance", pipelineParams.acceptanceDisabled, parameters)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {
            script {				
				def parameters = []
				runStage(env.STAGE_NAME, "${jobName}-Regression", pipelineParams.regressionDisabled, parameters)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)
			}
        }
    }	
}
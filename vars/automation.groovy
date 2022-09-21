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

def runStage(def deployEnv, def jobName, def isStageDisabled, def parameters) {
	catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
		if(env.IS_ANY_STAGE_FAILED == 'true') {
			error("Failed due to ${env.FAILED_STAGE_NAME} failure")
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
			env.FAILED_ENV = deployEnv
			env.FAILED_STAGE_NAME = env.STAGE_NAME
			error("${jobName} Failed")
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
        doDeploy(deployEnv, pipelineParams, jobName)
    }
}

def doRollback(def deploymentType, def pipelineParams, def jobName) {
	def envs = getDeploymentEnvironments(deploymentType)
	def deployEnvs = envs.split(',')
	def idx = deployEnvs.findIndexOf{ it ==  env.FAILED_ENV}
	def rollbackEnvs = deployEnvs.take(idx + 1)
	for(rollbackEnv in rollbackEnvs) {
		doDeploy(rollbackEnv, pipelineParams, jobName)
	}
}

def rollbackApp(def deploymentType) {
	catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {                            
		if(env.FAILED_ENV) {
			env.LAST_SUCCESS_BUILD_VERSION = getLastSuccessBuildVersion(currentBuild.getPreviousBuild(), deploymentType)
			def rollbackRun = build(
				job: "${env.JOB_NAME}-Rollback",
				parameters: [
						string(name: 'VERSION', value: env.LAST_SUCCESS_BUILD_VERSION),
						string(name: 'FAILED_ENV', value: env.FAILED_ENV)                                            
				]
			)
			if(rollbackRun != null && rollbackRun.getResult() == 'SUCCESS') {
				env.ROLL_BACK = 'true'                                    
			}
		} else {
			env.ROLL_BACK = 'false'
			markStageAsSkipped(env.STAGE_NAME, true)                                
		}                            
	}
}

def doDeploy(def deployEnv, def pipelineParams, def jobName) {
    if(deployEnv == "INT") {
        stage("Build") {
            script {								
				env.IS_ANY_STAGE_FAILED = 'false'
				def parameters = [
                                	string(name: 'BRANCH', value: 'develop')
                            ]
				runStage(deployEnv, "${jobName}-Build", pipelineParams.buildDisabled, parameters)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
			}
        }
    }    
    stage("${deployEnv}-Deploy") {
        script {			
			def parameters = [
                                string(name: 'VERSION', value: env.VERSION)
                        ]	
			runStage(deployEnv, "${jobName}-Deploy", pipelineParams.deployDisabled, parameters)
			markStageAsSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
		}
    }
    if(deployEnv == "INT") {
        stage("Acceptance") {
            script {				
				def parameters = []
				runStage(deployEnv, "${jobName}-Acceptance", pipelineParams.acceptanceDisabled, parameters)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("Regression") {
            script {				
				def parameters = []
				runStage(deployEnv, "${jobName}-Regression", pipelineParams.regressionDisabled, parameters)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)
			}
        }
    }	
}
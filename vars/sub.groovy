#!/usr/bin/env groovy
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def getDeploymentEnvironments(def deploymentType) {
    def map = [FUNCTIONAL:'INT,QAF',RELEASE:'INT,QAR']
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
			result = build(job: jobName, parameters: parameters, propagate: false)
		} else {
			result = build(job: jobName, propagate: false)
		}		
	}
	return result
}

def runStage(def deployEnv, def jobName, def isStageDisabled, def parameters) {
	catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
		if(env.IS_ANY_STAGE_FAILED == 'true') {
			echo "Stage \"${env.STAGE_NAME}\" skipped due to \"${env.FAILED_STAGE}\" failure"
			Utils.markStageSkippedForConditional(env.STAGE_NAME)
		} else {
			def jobRun = runJob(jobName, isStageDisabled, parameters)
			if(jobRun != null && jobRun.getResult() == 'SUCCESS') {
				if(jobName.contains("Build")) {
					env.VERSION = jobRun.buildVariables.VERSION
				}				
			}
			if(jobRun != null && jobRun.getResult() == 'FAILURE') {
				env.IS_ANY_STAGE_FAILED = 'true'
				env.FAILED_ENV = deployEnv
				env.FAILED_STAGE = env.STAGE_NAME
				error("Stage \"${env.STAGE_NAME}\" failed")
			}
		}				
	}
}

def deploy(def deploymentType, def pipelineParams, def jobName) {
    def envs = getDeploymentEnvironments(deploymentType)
    def deployEnvs = envs.split(',')
    for(deployEnv in deployEnvs) {
        doDeploy(deployEnv, pipelineParams, jobName)
    }
}

def rollback(def deploymentType, def pipelineParams, def jobName) {
	def envs = getDeploymentEnvironments(deploymentType)
	def deployEnvs = envs.split(',')
	def idx = deployEnvs.findIndexOf{ it ==  env.FAILED_ENV}
	def rollbackEnvs = deployEnvs.take(idx + 1)
	for(rollbackEnv in rollbackEnvs) {
		doDeploy(rollbackEnv, pipelineParams, jobName)
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
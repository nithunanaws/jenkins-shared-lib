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

def getDeployEnvMap(def deploymentType) {
	def envs = getDeploymentEnvironments(env.DEPLOYMENT_TYPE)
	def deployEnvs = envs.split(',')
	def envList = ['INT','QAF','QAR']
	def deployEnvMap = []
	for(env in envList) {
		if(deployEnvs.contains(env)) {
			deployEnvMap.put(env, false)
		} else {
			deployEnvMap.put(env, true)
		}
	}
	return deployEnvMap
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

def deploy(def pipelineParams, def jobName, def isRollback) {
    def envs = getDeploymentEnvironments(env.DEPLOYMENT_TYPE)
    def deployEnvs = envs.split(',')
	def deployEnvMap = getDeployEnvMap(env.DEPLOYMENT_TYPE)
	if(!isRollback) {
		for(deployEnv in deployEnvMap.keySet()) {
			doDeploy(deployEnv, deployEnvMap[deployEnv], pipelineParams, jobName, isRollback, false)
		}		
	} else {
		def foundFailedEnv = false
		for(deployEnv in deployEnvMap.keySet()) {
			if(!foundFailedEnv) {
				if(deployEnv == env.FAILED_ENV) {
					foundFailedEnv = true
					doDeploy(deployEnv, deployEnvMap[deployEnv], pipelineParams, jobName, isRollback, false)
				} else {
					foundFailedEnv = false
					doDeploy(deployEnv, deployEnvMap[deployEnv], pipelineParams, jobName, isRollback, false)
				}
			} else {
				doDeploy(deployEnv, deployEnvMap[deployEnv], pipelineParams, jobName, isRollback, true)
			}
		}
	}
}

def doDeploy(def deployEnv, def isStageSkipped, def pipelineParams, def jobName, def isRollback, def isDeployStageSkipped) {
    if(deployEnv == "INT") {
        stage("Build") {
            script {								
				env.IS_ANY_STAGE_FAILED = 'false'
				if(!isStageSkipped) {
					def parameters = [
                                	string(name: 'BRANCH', value: 'develop')
                            ]
					runStage(deployEnv, "${jobName}-Build", pipelineParams.buildDisabled, parameters)
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
				} else {
					markStageAsSkipped(env.STAGE_NAME, true)
				}
				
			}
        }
    }    
    stage("${deployEnv}-Deploy") {
        script {
			if(!isStageSkipped) {
				def parameters
				if(!isRollback) {
					parameters = [
									string(name: 'VERSION', value: env.VERSION)
							]
					runStage(deployEnv, "${jobName}-Deploy", pipelineParams.deployDisabled, parameters)
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)			
				} else {
					if(deployEnv == "INT") {
						parameters = [
									string(name: 'VERSION', value: env.FUNC_VERSION)
							]
					} else {
						parameters = [
									string(name: 'VERSION', value: env.VERSION)
							]
					}
					if(!isDeployStageSkipped) {
						runStage(deployEnv, "${jobName}-Deploy", pipelineParams.deployDisabled, parameters)										
					} else {
						markStageAsSkipped(env.STAGE_NAME, true)
					}
				}
			} else {
				markStageAsSkipped(env.STAGE_NAME, true)
			}						
		}
    }	
    if(deployEnv == "INT") {
        stage("Acceptance") {
            script {
				if(!isStageSkipped) {
					def parameters = []
					runStage(deployEnv, "${jobName}-Acceptance", pipelineParams.acceptanceDisabled, parameters)
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)
				} else {
					markStageAsSkipped(env.STAGE_NAME, true)
				}	 						
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("Regression") {
            script {		
				if(!isStageSkipped) {
					def parameters = []
					runStage(deployEnv, "${jobName}-Regression", pipelineParams.regressionDisabled, parameters)
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)
				} else {
					markStageAsSkipped(env.STAGE_NAME, true)
				}				
			}
        }
    }	
}
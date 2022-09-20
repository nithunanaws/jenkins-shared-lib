#!/usr/bin/env groovy
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def getDeploymentEnvironments(def deploymentType) {
    def map = [FUNCTIONAL:'INT,QAF',RELEASE:'INT,QAR,STG,PT,PROD']        
    map[deploymentType] ?: 'INT,QAF'
}

def deployApp(def deploymentType, def pipelineParams, def jobName) {	
    def envs = getDeploymentEnvironments(deploymentType)
    def deployEnvs = envs.split(',')	
    for(deployEnv in deployEnvs) {
        doDeploy(deployEnv, deploymentType, pipelineParams, jobName)        
    }
}

def markStageAsSkipped(def stageName, def isStageDisabled) {
	if(isStageDisabled != null && isStageDisabled == true) {
		Utils.markStageSkippedForConditional(stageName)
	}
}

def runJob(def jobName, def isStageDisabled) {
	def result
	if(isStageDisabled == null || isStageDisabled == false) {
		result = build(job: jobName)
	}	
	return result
}

def runStage(def stageName, def jobName, def isStageDisabled) {
	catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
		if(env.IS_STAGE_FAILED == 'true') {
			sh 'exit 1'	
		}
		try {
			if(env.IS_STAGE_FAILED == 'false') {
				def jobResult = runJob(jobName, isStageDisabled)
				if(jobName.contains("build")) {
					env.VERSION = jobResult.buildVariables.VERSION
				}			
			} 								
		} catch(Exception e) {					
			env.IS_STAGE_FAILED = 'true'
			env.STAGE_FAILED = stageName
			sh 'exit 1'		
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
	def lastSuccessBuildDesc = successBuildsDesc.first()
	def descWords = lastSuccessBuildDesc.split(" ")
	return descWords[1]
}

def populateSuccessBuilds(def build, def successBuilds) {
	def allBuilds = []
	populateAllBuilds(build, allBuilds)
	for(eachBuild in allBuilds) {
		if(eachBuild != null && eachBuild.getResult() != 'FAILURE') {
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

def doDeploy(def deployEnv, def deploymentType, def pipelineParams, def jobName) {

    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
				env.IS_STAGE_FAILED = 'false'
				runStage(env.STAGE_NAME, "${jobName}-build", pipelineParams.buildDisabled)				
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			markStageAsSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
			runStage(env.STAGE_NAME, "${jobName}-deploy", pipelineParams.deployDisabled)							
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)
				runStage(env.STAGE_NAME, "${jobName}-acceptance", pipelineParams.acceptanceDisabled)											
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {            
            script {
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)
				runStage(env.STAGE_NAME, "${jobName}-regression", pipelineParams.regressionDisabled)							
			}
        }
    }	
}
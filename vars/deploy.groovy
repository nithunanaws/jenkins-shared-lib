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
	def buildRun
    def deployRun
    def acceptanceRun
    def regressionRun	

    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				buildRun = runJob("${jobName}-build", pipelineParams.buildDisabled)
				env.VERSION = buildRun.buildVariables.VERSION
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			echo "Status: ${env.PREVIOUS_STAGE_FAILED}"		
			if(env.PREVIOUS_STAGE_FAILED == 'false') {
				deployRun = runJob("${jobName}-deploy", pipelineParams.deployDisabled)
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
			}			
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				isStageFailed = 'false'
				try {
					acceptanceRun = runJob("${jobName}-acceptance", pipelineParams.acceptanceDisabled)						
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)					
				} catch(Exception e) {
					isStageFailed = 'true'					
					currentBuild.result = 'FAILURE'
				}
				if(isStageFailed == 'true') {
					echo "Status: ${env.PREVIOUS_STAGE_FAILED}"
					env.PREVIOUS_STAGE_FAILED = 'true'
					echo "Status: ${env.PREVIOUS_STAGE_FAILED}"
				}
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {            
            script {
				regressionRun = runJob("${jobName}-regression", pipelineParams.regressionDisabled)	
				markStageAsSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)
			}
        }
    }	
}
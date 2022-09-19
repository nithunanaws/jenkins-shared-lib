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
				try {
					buildRun = runJob("${jobName}-build", pipelineParams.buildDisabled)
					env.VERSION = buildRun.buildVariables.VERSION					
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
					env.IS_STAGE_FAILED = 'false'
				} catch(Exception e) {					
					env.IS_STAGE_FAILED = 'true'
					env.STAGE_FAILED = env.STAGE_NAME
					currentBuild.result = 'FAILURE'
				}				
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			if(env.IS_STAGE_FAILED == 'true') {
				sh 'exit 1'
			}
			try {
				if(env.IS_STAGE_FAILED == 'false') {
					deployRun = runJob("${jobName}-deploy", pipelineParams.deployDisabled)
					markStageAsSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
				}				
			} catch(Exception e) {					
				env.IS_STAGE_FAILED = 'true'
				env.STAGE_FAILED = env.STAGE_NAME
				currentBuild.result = 'FAILURE'				
			}						
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				if(env.IS_STAGE_FAILED == 'true') {
					bat '''dir QWERTY || exit'''
				}
				try {
					if(env.IS_STAGE_FAILED == 'false') {
						acceptanceRun = runJob("${jobName}-acceptance", pipelineParams.acceptanceDisabled)						
						markStageAsSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)
					}									
				} catch(Exception e) {					
					env.IS_STAGE_FAILED = 'true'	
					env.STAGE_FAILED = env.STAGE_NAME
					currentBuild.result = 'FAILURE'	
				}				
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {            
            script {
				if(env.IS_STAGE_FAILED == 'true') {
					sh 'exit 1'
				}
				try {
					if(env.IS_STAGE_FAILED == 'false') {
						regressionRun = runJob("${jobName}-regression", pipelineParams.regressionDisabled)	
						markStageAsSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)
					} 								
				} catch(Exception e) {					
					env.IS_STAGE_FAILED = 'true'
					env.STAGE_FAILED = env.STAGE_NAME
					currentBuild.result = 'FAILURE'
				}				
			}
        }
    }	
}
#!/usr/bin/env groovy
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def getDeploymentEnvironments(deploymentType) {
    def map = [FUNCTIONAL:'INT,QAF',RELEASE:'INT,QAR,STG,PT,PROD']        
    map[deploymentType] ?: 'INT,QAF'
}

def deployApp(deploymentType, pipelineParams) {	
    def envs = getDeploymentEnvironments(deploymentType)
    def deployEnvs = envs.split(',')
    for(deployEnv in deployEnvs) {
        doDeploy(deployEnv, deploymentType, pipelineParams)        
    }
}

def markStageSkipped(stageName, isStageDisabled) {
	if(isStageDisabled != null && isStageDisabled == true) {
		Utils.markStageSkippedForConditional(stageName)
	}
}

def runStageJob(jobName, isStageDisabled) {
	if(isStageDisabled == null || isStageDisabled == false) {
		build(job: jobName)
	}
}

def getLastSuccessBuildVersion(build, deploymentType) {
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

def populateSuccessBuilds(build, successBuilds) {
	def allBuilds = []
	populateAllBuilds(build, allBuilds)
	for(eachBuild in allBuilds) {
		if(eachBuild != null && eachBuild.getResult() != 'FAILURE') {
			successBuilds.add(eachBuild)
		}
	}	
}

def populateAllBuilds(build, allBuilds) {	
	if(build != null) {
		allBuilds.add(build)
		populateAllBuilds(build.getPreviousBuild(), allBuilds)
	}	
}

def doDeploy(deployEnv, deploymentType, pipelineParams) {
	def buildRun
    def deployRun
    def acceptanceRun
    def regressionRun	

    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				if (pipelineParams.buildDisabled == null || pipelineParams.buildDisabled == false) {
					buildRun = build(job: "test-build")					
                    if (buildRun.getResult() != 'SUCCESS') {
                        error("Build failed")
                    }
					env.VERSION = buildRun.buildVariables.VERSION
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			if (pipelineParams.deployDisabled == null || pipelineParams.deployDisabled == false) {
				deployRun = build(job: "test-deploy")				
                if (deployRun.getResult() != 'SUCCESS') {
                    error("Deployment failed")
                }
			}
			markStageSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				runStageJob("test-acceptance", pipelineParams.acceptanceDisabled)
				markStageSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)				
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {            
            script {
				if (pipelineParams.regressionDisabled == null || pipelineParams.regressionDisabled == false) {
					regressionRun = build(job: "test-regression")					
                    if (regressionRun.getResult() != 'SUCCESS') {
                        error("Regression tests failed")
                    }
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)	
			}
        }
    }	
}
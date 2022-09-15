#!/usr/bin/env groovy
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def getDeploymentEnvironments(deploymentType) {
    def map = [FUNCTIONAL:'INT,QAF',RELEASE:'INT,QAR,STG,PT,PROD']        
    map[deploymentType] ?: 'INT,QAF'
}

def deployApp(deploymentType, pipelineParams) {	
    def envs = getDeploymentEnvironments(deploymentType)
    def deploymentEnvs = envs.split(',')
    for(String deployEnv: deploymentEnvs) {
        doDeploy(deployEnv, deploymentType, pipelineParams)        
    }
}

def markStageSkipped(stageName, isStageDisabled) {
	if(isStageDisabled != null && isStageDisabled == true) {
		Utils.markStageSkippedForConditional(stageName)
	}
}

def doDeploy(deployEnv, deploymentType, pipelineParams) {
    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				if (pipelineParams.buildDisabled == null || pipelineParams.buildDisabled == false) {
					echo "${deploymentType}-Build Stage"
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			if (pipelineParams.deployDisabled == null || pipelineParams.deployDisabled == false) {
				echo "${deployEnv}-Deploy Stage"
			}
			markStageSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				if (pipelineParams.acceptanceDisabled == null || pipelineParams.acceptanceDisabled == false) {
					echo "${deployEnv}-Acceptance Stage"
					error("Acceptance tests failed with result")
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)				
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {            
            script {
				if (pipelineParams.regressionDisabled == null || pipelineParams.regressionDisabled == false) {
					echo "${deployEnv}-Regression Stage"
					error("Regression tests failed with result")
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)	
			}
        }
    }    
}
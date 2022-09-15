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

def doDeploy(deployEnv, deploymentType, pipelineParams) {
    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				if (pipelineParams.buildDisabled == null || pipelineParams.buildDisabled == false) {
					echo "${deploymentType}-Build Stage"
				}
				if(pipelineParams.buildDisabled != null && pipelineParams.buildDisabled == true) {					
					Utils.markStageSkippedForConditional("${deployEnv}-Build")
				}
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			if (pipelineParams.deployDisabled == null || pipelineParams.deployDisabled == false) {
				echo "${deployEnv}-Deploy Stage"
			}
			if(pipelineParams.deployDisabled != null && pipelineParams.deployDisabled == true) {					
				Utils.markStageSkippedForConditional("${deployEnv}-Deploy")
			}
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				if (pipelineParams.acceptanceDisabled == null || pipelineParams.acceptanceDisabled == false) {
					echo "${deployEnv}-Acceptance Stage"
					error("${env.STAGE_NAME}-Acceptance tests failed with result")
				}
				if(pipelineParams.acceptanceDisabled != null && pipelineParams.acceptanceDisabled == true) {					
					Utils.markStageSkippedForConditional("${deployEnv}-Acceptance")
				}
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
				if(pipelineParams.regressionDisabled != null && pipelineParams.regressionDisabled == true) {					
					Utils.markStageSkippedForConditional("${deployEnv}-Regression")
				}
			}
        }
    }    
}
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
	def buildRun
    def deployRun
    def acceptanceRun
    def regressionRun

    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				if (pipelineParams.buildDisabled == null || pipelineParams.buildDisabled == false) {
					buildRun = build(job: "test-build")
					def buildRunResult = buildRun.getResult()
                    if (buildRunResult != 'SUCCESS') {
                        error("Build failed with result: ${buildRunResult}")
                    }
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.buildDisabled)
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			if (pipelineParams.deployDisabled == null || pipelineParams.deployDisabled == false) {
				deployRun = build(job: "test-deploy")
				def deployRunResult = deployRun.getResult()
                if (deployRunResult != 'SUCCESS') {
                    error("Deployment failed with result: ${deployRunResult}")
                }
			}
			markStageSkipped(env.STAGE_NAME, pipelineParams.deployDisabled)
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				if (pipelineParams.acceptanceDisabled == null || pipelineParams.acceptanceDisabled == false) {
					acceptanceRun = build(job: "test-acceptance")	
					def acceptanceRunResult = acceptanceRun.getResult()
                    if (acceptanceRunResult != 'SUCCESS') {
                        error("Acceptance tests failed with result: ${acceptanceRunResult}")
                    }
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.acceptanceDisabled)				
			}
        }
    }
    if(deployEnv == "QAR") {
        stage("${deployEnv}-Regression") {            
            script {
				if (pipelineParams.regressionDisabled == null || pipelineParams.regressionDisabled == false) {
					regressionRun = build(job: "test-regression")
					def regressionRunResult = regressionRun.getResult()
                    if (regressionRunResult != 'SUCCESS') {
                        error("Regression tests failed with result: ${regressionRunResult}")
                    }
				}
				markStageSkipped(env.STAGE_NAME, pipelineParams.regressionDisabled)	
			}
        }
    }    
}
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
				if (pipelineParams.buildDisabled == null ||  pipelineParams.buildDisabled == false) {
					echo "${deploymentType}-Build Stage"
				}				
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			if (pipelineParams.deployDisabled == null ||  pipelineParams.deployDisabled == false) {
				echo "${deployEnv}-Deploy Stage"
			}			
		}
    }
    if(deployEnv == "INT") {
        stage("${deployEnv}-Acceptance") {            
            script {
				if (pipelineParams.acceptanceDisabled == null ||  pipelineParams.acceptanceDisabled == false) {
					echo "${deployEnv}-Acceptance Stage"
					error("Acceptance tests failed with result")
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
				if (pipelineParams.regressionDisabled == null ||  pipelineParams.regressionDisabled == false) {
					echo "${deployEnv}-Regression Stage"					
				}					
			}
        }
    }    
}

def call(body) {
	
	// evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any

        environment {            
            deploymentType = "${pipelineParams.deploymentType}"
        }

        stages {
            stage('Deployment Initiated') {
                steps {
                    script {
                        deployApp(env.deploymentType,pipelineParams)
                    }
                }
            }                        
        } 
		post {            
            success {
                script {
                    currentBuild.description = env.deploymentType                  
                }
            }            
        }
    }
}
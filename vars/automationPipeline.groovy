#!/usr/bin/env groovy

def getdeploymentEnvironments(deploymentType) {
    def map = [FUNCTIONAL:'INT,QAF',RELEASE:'INT,QAR,STG,PT,PROD']        
    map[deploymentType] ?: 'INT,QAF'
}

def deployApp(deploymentType) {
    def envs = getdeploymentEnvironments(deploymentType)
    def deploymentEnvs = envs.split(',')
    for(String deployEnv: deploymentEnvs) {
        doDeploy(deployEnv, deploymentType)        
    }
}

def doDeploy(deployEnv, deploymentType) {
    if(deployEnv == "INT") {
        stage("${deploymentType}-Build") {
            script {
				echo "${deploymentType}-Build Stage"
			}
        }
    }    
    stage("${deployEnv}-Deploy") {        
        script {
			echo "${deployEnv}-Deploy Stage"
		}
    }    
	stage("INT-Acceptance") {  		
		script {
			if(${deployEnv} == "INT") {
				echo "${deployEnv}-Acceptance Stage"
			}						
		}
	}    
	stage("QAR-Regression") {		
		script {
			if(${deployEnv} == "INT") {
				echo "${deployEnv}-Regression Stage"
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
                        deployApp(env.deploymentType)
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
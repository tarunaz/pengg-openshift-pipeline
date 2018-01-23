#!/usr/bin/env groovy

def call (body) {
        def config = [:]
    	body.resolveStrategy = Closure.DELEGATE_FIRST
    	body.delegate = config
  	body()

	def jenkinsToken = readFile('/etc/jenkins/token')
                
	openshiftDeploy apiURL: "${ocpUrl}", depCfg: config.microservice, namespace: config.namespace, authToken: "${jenkinsToken}",  verbose: 'true', waitTime: '', waitUnit: 'sec'
          		
    	echo "Verifying the deployment in TPASS..."
    	openshiftVerifyDeployment apiURL: config.ocpUrl, depCfg: config.microservice, namespace: config.namespace, authToken: "${jenkinsToken}", replicaCount: '1', verbose: 'true', verifyReplicaCount: 'true', waitTime: '900', waitUnit: 'sec'


 }



#!/usr/bin/env groovy

def call (Map config) {
   
	// Skip TLS for Openshift Jenkins Plugin
 	env.SKIP_TLS = 'true'

	def jenkinsToken = readFile('/etc/jenkins/token')
                
	openshiftDeploy apiURL: config.ocpUrl, depCfg: config.microservice, namespace: config.deployNamespace, authToken: "${jenkinsToken}",  verbose: 'false', waitTime: '', waitUnit: 'sec'
          		
    	echo "Verifying the deployment in TPASS..."
    	openshiftVerifyDeployment apiURL: config.ocpUrl, depCfg: config.microservice, namespace: config.deployNamespace, authToken: "${jenkinsToken}", replicaCount: '1', verbose: 'false', verifyReplicaCount: 'true', waitTime: '900', waitUnit: 'sec'


 }



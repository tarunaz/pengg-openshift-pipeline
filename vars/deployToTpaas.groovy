#!/usr/bin/env groovy

def call () {
   
	echo "Deploy to TPAAS"

	// Skip TLS for Openshift Jenkins Plugin
 	env.SKIP_TLS = 'true'

	def jenkinsToken = readFile('/etc/jenkins/token')
                
	openshiftDeploy apiURL: "${OCP_DEPLOY_URL}", depCfg: "${BASE}", namespace: "${DEPLOY_NAMESPACE}", authToken: "${jenkinsToken}",  verbose: 'false', waitTime: '', waitUnit: 'sec'
          		
    	echo "Verifying the deployment in TPASS..."
    	openshiftVerifyDeployment apiURL: "${OCP_DEPLOY_URL}", depCfg: "${BASE}", namespace: "${DEPLOY_NAMESPACE}", authToken: "${jenkinsToken}", replicaCount: '1', verbose: 'false', verifyReplicaCount: 'true', waitTime: '900', waitUnit: 'sec'


 }



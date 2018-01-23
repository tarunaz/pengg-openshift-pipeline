#!/usr/bin/groovy

import com.netapp.PipelineUtils

/**
   * Main Netapp Pipeline meant for development branch
   * @param gitRepoUrl URL to the DSM source code
   * @param microserviceSubmodule Submodule that gets built into jar
   * @param hipchatRooms Comma delimmited hipchat rooms
   * @param microservice DSM name
   * @param mavenCredentialsId Credential Id to user/pass that works with FIS artifactory in maven server
   * @param fortifyCredentialsId Credential Id to Fortify auth token
   * @param artifactoryCredentialsId Credential Id to push/pull artifacts from artifactory
   * @param artifactoryRepoBaseURL Artifactory URL for push/pull of artifacts
   * @param artifactoryRepoName Artifactory repo name
   */

def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // Instantiate variables used in pipeline
    ocpUrl = "https://tpaas-console.netapp.com:8443"
    jenkinsToken = ""
  
    registryUrl = 'mobile-docker-1.repos.fismobile.com/test'
    def pipelineUtils = new PipelineUtils()

    try {
   
        node('nodejs') {
                
            // Skip TLS for Openshift Jenkins Plugin
            env.SKIP_TLS = 'true'
       
            jenkinsToken = readFile('/etc/jenkins/token')
           
	    echo "openshiftbuild  Connect & Trigger openshift Buid in registry cluster..."
       	    stage('build spog') {
                // Checkout source code
 	        checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'app-root']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: config.gitRepoUrl]]])
		
		// Checkout openshift template files
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pengg-openshift']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'a0abb0d8-4a01-4d4e-a4c7-90526325f245', url: 'git@github.com:tarunaz/pengg-openshift.git']]])
                
		//Process templates and start build
                pipelineUtils.processTemplateAndStartBuild("pengg-openshift/pengg-openshift-system/openshift/templates/pengg-runtime-bc-spog.yaml", "BASE=${config.microservice} SOURCE_REPOSITORY_URL=${config.gitRepoUrl} SOURCE_REPOSITORY_REF=${config.sourceRepositoryRef} GIT_PULL_SECRET=${config.gitPullSecret} ANGULAR_HOME_DIR=web Version_File_Loc=version DEST_DEPLOY_NAMESPACE=${config.deployNamespace} authToken=${jenkinsToken} email_Address=${config.emailAddress}", config.buildNamespace, "${config.microservice}-${config.sourceRepositoryRef}")

    	    }


            echo "Openshift Tag image with custom version number..."
    	    stage('tag image') {
                echo "readFile version "	
                def VERSION = readFile 'app-root/version'
                
		echo "$VERSION"
	        openshiftTag alias: 'false', destStream: 'tarun-spog', destTag: "$VERSION", destinationNamespace: '', srcStream: 'tarun-spog', srcTag: 'master', verbose: 'false'
	    }

	    echo "openshift import image at TPAAS..."
	    stage('import image to TPAAS') {
                echo "$jenkinsToken"
	        pipelineUtils.login(ocpUrl, jenkinsToken)
	     
                sh """
    	           oc project "${config.deployNamespace}"

                   oc process -f pengg-openshift/pengg-openshift-system/openshift/templates/pengg-spog-dc.yml \
                    NAME=${config.microservice} APPLICATION_IS_TAG_WEB="${config.microservice}:1.7" APPLICATION_IS_TAG_API="${config.microservice}:${config.sourceRepositoryRef}" APPLICATION_IS_NM_WEB=${config.deployNamespace} | oc apply -f - 

                   oc import-image 'tarun-spog:1.7' --from=registry.netapp.com/nss/tarun-spog:1.7 --confirm
            	"""
	    }
            
	    echo "openshift deployement to TPAAS .."
            stage('deploy to TPAAS') {
           
		openshiftDeploy apiURL: "${ocpUrl}", depCfg: config.microservice, namespace: config.deployNamespace, authToken: "${jenkinsToken}",  verbose: 'true', waitTime: '', waitUnit: 'sec'
          		
 	    	echo "Verifying the deployment in TPASS..."
            	openshiftVerifyDeployment apiURL: "${ocpUrl}", depCfg: config.microservice, namespace: config.deployNamespace, authToken: "${jenkinsToken}", replicaCount: '2', verbose: 'true', verifyReplicaCount: 'true', waitTime: '900', waitUnit: 'sec'
	    }
	  } // node
    } catch (err) {
        currentBuild.result = 'FAILED'
        throw err
    }
}

